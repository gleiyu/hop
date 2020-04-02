//CHECKSTYLE:FileLength:OFF
/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2019 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.job;

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.cluster.SlaveServer;
import org.apache.hop.core.Const;
import org.apache.hop.core.ExecutorInterface;
import org.apache.hop.core.ExtensionDataInterface;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.Result;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopJobException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.gui.JobTracker;
import org.apache.hop.core.logging.ChannelLogTable;
import org.apache.hop.core.logging.DefaultLogLevel;
import org.apache.hop.core.logging.HasLogChannelInterface;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.JobEntryLogTable;
import org.apache.hop.core.logging.JobLogTable;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LogStatus;
import org.apache.hop.core.logging.LoggingBuffer;
import org.apache.hop.core.logging.LoggingHierarchy;
import org.apache.hop.core.logging.LoggingObjectInterface;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.LoggingRegistry;
import org.apache.hop.core.logging.Metrics;
import org.apache.hop.core.parameters.DuplicateParamException;
import org.apache.hop.core.parameters.NamedParams;
import org.apache.hop.core.parameters.NamedParamsDefault;
import org.apache.hop.core.parameters.UnknownParamException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.job.entries.job.JobEntryJob;
import org.apache.hop.job.entries.special.JobEntrySpecial;
import org.apache.hop.job.entries.pipeline.JobEntryPipeline;
import org.apache.hop.job.entry.JobEntryCopy;
import org.apache.hop.job.entry.JobEntryInterface;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.resource.ResourceUtil;
import org.apache.hop.resource.TopLevelResource;
import org.apache.hop.www.RegisterJobServlet;
import org.apache.hop.www.RegisterPackageServlet;
import org.apache.hop.www.SocketRepository;
import org.apache.hop.www.StartJobServlet;
import org.apache.hop.www.WebResult;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class executes a job as defined by a JobMeta object.
 * <p>
 * The definition of a PDI job is represented by a JobMeta object. It is typically loaded from a .kjb file,
 * or it is generated dynamically. The declared parameters of the job definition are then queried using
 * listParameters() and assigned values using calls to setParameterValue(..).
 *
 * @author Matt Casters
 * @since 07-apr-2003
 */
public class Job extends Thread implements VariableSpace, NamedParams, HasLogChannelInterface, LoggingObjectInterface,
  ExecutorInterface, ExtensionDataInterface {
  private static Class<?> PKG = Job.class; // for i18n purposes, needed by Translator!!

  public static final String CONFIGURATION_IN_EXPORT_FILENAME = "__job_execution_configuration__.xml";

  private LogChannelInterface log;

  private LogLevel logLevel = DefaultLogLevel.getLogLevel();

  private String containerObjectId;

  private JobMeta jobMeta;

  private int logCommitSize = 10;

  private AtomicInteger errors;

  private VariableSpace variables = new Variables();

  /**
   * The job that's launching this (sub-) job. This gives us access to the whole chain, including the parent variables,
   * etc.
   */
  protected Job parentJob;

  /**
   * The parent pipeline
   */
  protected Pipeline parentPipeline;

  /**
   * The parent logging interface to reference
   */
  private LoggingObjectInterface parentLoggingObject;

  /**
   * Keep a list of the job entries that were executed. org.apache.hop.core.logging.CentralLogStore.getInstance()
   */
  private JobTracker jobTracker;

  /**
   * A flat list of results in THIS job, in the order of execution of job entries
   */
  private final LinkedList<JobEntryResult> jobEntryResults = new LinkedList<JobEntryResult>();

  private Date startDate, endDate, currentDate, logDate, depDate;

  private long batchId;

  /**
   * This is the batch ID that is passed from job to job to pipeline, if nothing is passed, it's the job's batch
   * id
   */
  private long passedBatchId;

  /**
   * The rows that were passed onto this job by a previous pipeline. These rows are passed onto the first job
   * entry in this job (on the result object)
   */
  private List<RowMetaAndData> sourceRows;

  /**
   * The result of the job, after execution.
   */
  private Result result;

  private boolean interactive;

  private List<JobListener> jobListeners;

  private List<JobEntryListener> jobEntryListeners;

  private List<DelegationListener> delegationListeners;

  private Map<JobEntryCopy, JobEntryPipeline> activeJobEntryPipeline;

  private Map<JobEntryCopy, JobEntryJob> activeJobEntryJobs;

  /**
   * Parameters of the job.
   */
  private NamedParams namedParams = new NamedParamsDefault();

  private SocketRepository socketRepository;

  private int maxJobEntriesLogged;

  private JobEntryCopy startJobEntryCopy;
  private Result startJobEntryResult;

  private String executingServer;

  private String executingUser;

  private String transactionId;

  private Map<String, Object> extensionDataMap;

  /**
   * Int value for storage job statuses
   */
  private AtomicInteger status;

  /**
   * <p>
   * This enum stores bit masks which are used to manipulate with statuses over field {@link Job#status}
   */
  enum BitMaskStatus {
    ACTIVE( 1 ), INITIALIZED( 2 ), STOPPED( 4 ), FINISHED( 8 );

    private final int mask;
    // the sum of status masks
    public static final int BIT_STATUS_SUM = 63;

    BitMaskStatus( int mask ) {
      this.mask = mask;
    }
  }

  public Job( String name, String file, String[] args ) {
    this();
    jobMeta = new JobMeta();

    if ( name != null ) {
      setName( name + " (" + super.getName() + ")" );
    }
    jobMeta.setName( name );
    jobMeta.setFilename( file );

    init();
    this.log = new LogChannel( this );
  }

  public void init() {
    status = new AtomicInteger();

    jobListeners = new ArrayList<JobListener>();
    jobEntryListeners = new ArrayList<JobEntryListener>();
    delegationListeners = new ArrayList<DelegationListener>();

    // these 2 maps are being modified concurrently and must be thread-safe
    activeJobEntryPipeline = new ConcurrentHashMap<JobEntryCopy, JobEntryPipeline>();
    activeJobEntryJobs = new ConcurrentHashMap<JobEntryCopy, JobEntryJob>();

    extensionDataMap = new HashMap<String, Object>();

    jobTracker = new JobTracker( jobMeta );
    synchronized ( jobEntryResults ) {
      jobEntryResults.clear();
    }
    errors = new AtomicInteger( 0 );
    batchId = -1;
    passedBatchId = -1;
    maxJobEntriesLogged = Const.toInt( EnvUtil.getSystemProperty( Const.HOP_MAX_JOB_ENTRIES_LOGGED ), 1000 );

    result = null;
    startJobEntryCopy = null;
    startJobEntryResult = null;

    this.setDefaultLogCommitSize();
  }

  private void setDefaultLogCommitSize() {
    String propLogCommitSize = this.getVariable( "pentaho.log.commit.size" );
    if ( propLogCommitSize != null ) {
      // override the logCommit variable
      try {
        logCommitSize = Integer.parseInt( propLogCommitSize );
      } catch ( Exception ignored ) {
        logCommitSize = 10; // ignore parsing error and default to 10
      }
    }
  }

  public Job( JobMeta jobMeta ) {
    this( jobMeta, null );
  }

  public Job( JobMeta jobMeta, LoggingObjectInterface parentLogging ) {
    this.jobMeta = jobMeta;
    this.containerObjectId = jobMeta.getContainerObjectId();
    this.parentLoggingObject = parentLogging;

    init();

    jobTracker = new JobTracker( jobMeta );

    this.log = new LogChannel( this, parentLogging );
    this.logLevel = log.getLogLevel();

    if ( this.containerObjectId == null ) {
      this.containerObjectId = log.getContainerObjectId();
    }
  }

  public Job() {
    init();
    this.log = new LogChannel( this );
    this.logLevel = log.getLogLevel();
  }

  /**
   * Gets the name property of the JobMeta property.
   *
   * @return String name for the JobMeta
   */
  @Override
  public String toString() {
    if ( jobMeta == null || Utils.isEmpty( jobMeta.getName() ) ) {
      return getName();
    } else {
      return jobMeta.getName();
    }
  }

  public static final Job createJobWithNewClassLoader() throws HopException {
    try {
      // Load the class.
      Class<?> jobClass = Const.createNewClassLoader().loadClass( Job.class.getName() );

      // create the class
      // Try to instantiate this one...
      Job job = (Job) jobClass.getDeclaredConstructor().newInstance();

      // Done!
      return job;
    } catch ( Exception e ) {
      String message = BaseMessages.getString( PKG, "Job.Log.ErrorAllocatingNewJob", e.toString() );
      throw new HopException( message, e );
    }
  }

  public String getJobname() {
    if ( jobMeta == null ) {
      return null;
    }
    return jobMeta.getName();
  }

  /**
   * Threads main loop: called by Thread.start();
   */
  @Override public void run() {

    ExecutorService heartbeat = null; // this job's heartbeat scheduled executor

    try {
      setStopped( false );
      setFinished( false );
      setInitialized( true );

      // Create a new variable name space as we want jobs to have their own set of variables.
      // initialize from parentJob or null
      //
      variables.initializeVariablesFrom( parentJob );
      setInternalHopVariables( variables );
      copyParametersFrom( jobMeta );
      activateParameters();

      // Run the job
      //
      fireJobStartListeners();

      heartbeat = startHeartbeat( getHeartbeatIntervalInSeconds() );

      result = execute();
    } catch ( Throwable je ) {
      log.logError( BaseMessages.getString( PKG, "Job.Log.ErrorExecJob", je.getMessage() ), je );
      // log.logError(Const.getStackTracker(je));
      //
      // we don't have result object because execute() threw a curve-ball.
      // So we create a new error object.
      //
      result = new Result();
      result.setNrErrors( 1L );
      result.setResult( false );
      addErrors( 1 ); // This can be before actual execution

      emergencyWriteJobTracker( result );

      setActive( false );
      setFinished( true );
      setStopped( false );
    } finally {
      try {
        shutdownHeartbeat( heartbeat );

        ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobFinish.id, this );
        log.logDebug( BaseMessages.getString( PKG, "Job.Log.DisposeEmbeddedMetastore" ) );

        fireJobFinishListeners();

        // release unused vfs connections
        HopVFS.freeUnusedResources();

      } catch ( HopException e ) {
        result.setNrErrors( 1 );
        result.setResult( false );
        log.logError( BaseMessages.getString( PKG, "Job.Log.ErrorExecJob", e.getMessage() ), e );

        emergencyWriteJobTracker( result );
      }
    }
  }

  private void emergencyWriteJobTracker( Result res ) {
    JobEntryResult jerFinalResult =
      new JobEntryResult( res, this.getLogChannelId(), BaseMessages.getString( PKG, "Job.Comment.JobFinished" ), null,
        null, 0, null );
    JobTracker finalTrack = new JobTracker( this.getJobMeta(), jerFinalResult );
    // jobTracker is up to date too.
    this.jobTracker.addJobTracker( finalTrack );
  }

  /**
   * Execute a job without previous results. This is a job entry point (not recursive)<br>
   * <br>
   *
   * @return the result of the execution
   * @throws HopException
   */
  private Result execute() throws HopException {
    try {
      log.snap( Metrics.METRIC_JOB_START );

      setFinished( false );
      setStopped( false );
      HopEnvironment.setExecutionInformation( this );

      log.logMinimal( BaseMessages.getString( PKG, "Job.Comment.JobStarted" ) );

      ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobStart.id, this );

      // Start the tracking...
      JobEntryResult jerStart =
        new JobEntryResult( null, null, BaseMessages.getString( PKG, "Job.Comment.JobStarted" ), BaseMessages
          .getString( PKG, "Job.Reason.Started" ), null, 0, null );
      jobTracker.addJobTracker( new JobTracker( jobMeta, jerStart ) );

      setActive( true );
      // Where do we start?
      JobEntryCopy startpoint;

      // synchronize this to a parent job if needed.
      //
      Object syncObject = this;
      if ( parentJob != null ) {
        syncObject = parentJob; // parallel execution in a job
      }

      synchronized ( syncObject ) {
        beginProcessing();
      }

      Result res = null;

      if ( startJobEntryCopy == null ) {
        startpoint = jobMeta.findJobEntry( JobMeta.STRING_SPECIAL_START, 0 );
      } else {
        startpoint = startJobEntryCopy;
        res = startJobEntryResult;
      }
      if ( startpoint == null ) {
        throw new HopJobException( BaseMessages.getString( PKG, "Job.Log.CounldNotFindStartingPoint" ) );
      }

      JobEntryResult jerEnd = null;

      if ( startpoint.isStart() ) {
        // Perform optional looping in the special Start job entry...
        //
        // long iteration = 0;

        boolean isFirst = true;
        JobEntrySpecial jes = (JobEntrySpecial) startpoint.getEntry();
        while ( ( jes.isRepeat() || isFirst ) && !isStopped() ) {
          isFirst = false;
          res = execute( 0, null, startpoint, null, BaseMessages.getString( PKG, "Job.Reason.Started" ) );
        }
        jerEnd =
          new JobEntryResult( res, jes.getLogChannelId(), BaseMessages.getString( PKG, "Job.Comment.JobFinished" ),
            BaseMessages.getString( PKG, "Job.Reason.Finished" ), null, 0, null );
      } else {
        res = execute( 0, res, startpoint, null, BaseMessages.getString( PKG, "Job.Reason.Started" ) );
        jerEnd =
          new JobEntryResult( res, startpoint.getEntry().getLogChannel().getLogChannelId(), BaseMessages.getString(
            PKG, "Job.Comment.JobFinished" ), BaseMessages.getString( PKG, "Job.Reason.Finished" ), null, 0, null );
      }
      // Save this result...
      jobTracker.addJobTracker( new JobTracker( jobMeta, jerEnd ) );
      log.logMinimal( BaseMessages.getString( PKG, "Job.Comment.JobFinished" ) );

      setActive( false );
      if ( !isStopped() ) {
        setFinished( true );
      }
      return res;
    } finally {
      log.snap( Metrics.METRIC_JOB_STOP );
    }
  }

  /**
   * Execute a job with previous results passed in.<br>
   * <br>
   * Execute called by JobEntryJob: don't clear the jobEntryResults.
   *
   * @param nr     The job entry number
   * @param result the result of the previous execution
   * @return Result of the job execution
   * @throws HopJobException
   */
  public Result execute( int nr, Result result ) throws HopException {
    setFinished( false );
    setActive( true );
    setInitialized( true );
    HopEnvironment.setExecutionInformation( this );

    // Where do we start?
    JobEntryCopy startpoint;

    // Perhaps there is already a list of input rows available?
    if ( getSourceRows() != null ) {
      result.setRows( getSourceRows() );
    }

    startpoint = jobMeta.findJobEntry( JobMeta.STRING_SPECIAL_START, 0 );
    if ( startpoint == null ) {
      throw new HopJobException( BaseMessages.getString( PKG, "Job.Log.CounldNotFindStartingPoint" ) );
    }

    JobEntrySpecial jes = (JobEntrySpecial) startpoint.getEntry();
    Result res;
    do {
      res = execute( nr, result, startpoint, null, BaseMessages.getString( PKG, "Job.Reason.StartOfJobentry" ) );
      setActive( false );
    } while ( jes.isRepeat() && !isStopped() );
    return res;
  }

  /**
   * Sets the finished flag.<b> Then launch all the job listeners and call the jobFinished method for each.<br>
   *
   * @see JobListener#jobFinished(Job)
   */
  public void fireJobFinishListeners() throws HopException {
    synchronized ( jobListeners ) {
      for ( JobListener jobListener : jobListeners ) {
        jobListener.jobFinished( this );
      }
    }
  }

  /**
   * Call all the jobStarted method for each listener.<br>
   *
   * @see JobListener#jobStarted(Job)
   */
  public void fireJobStartListeners() throws HopException {
    synchronized ( jobListeners ) {
      for ( JobListener jobListener : jobListeners ) {
        jobListener.jobStarted( this );
      }
    }
  }

  /**
   * Execute a job entry recursively and move to the next job entry automatically.<br>
   * Uses a back-tracking algorithm.<br>
   *
   * @param nr
   * @param prev_result
   * @param jobEntryCopy
   * @param previous
   * @param reason
   * @return
   * @throws HopException
   */
  private Result execute( final int nr, Result prev_result, final JobEntryCopy jobEntryCopy, JobEntryCopy previous,
                          String reason ) throws HopException {
    Result res = null;

    if ( isStopped() ) {
      res = new Result( nr );
      res.stopped = true;
      return res;
    }

    // if we didn't have a previous result, create one, otherwise, copy the content...
    //
    final Result newResult;
    Result prevResult = null;
    if ( prev_result != null ) {
      prevResult = prev_result.clone();
    } else {
      prevResult = new Result();
    }

    JobExecutionExtension extension = new JobExecutionExtension( this, prevResult, jobEntryCopy, true );
    ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobBeforeJobEntryExecution.id, extension );

    if ( extension.result != null ) {
      prevResult = extension.result;
    }

    if ( !extension.executeEntry ) {
      newResult = prevResult;
    } else {
      if ( log.isDetailed() ) {
        log.logDetailed( "exec(" + nr + ", " + ( prev_result != null ? prev_result.getNrErrors() : 0 ) + ", "
          + ( jobEntryCopy != null ? jobEntryCopy.toString() : "null" ) + ")" );
      }

      // Which entry is next?
      JobEntryInterface jobEntryInterface = jobEntryCopy.getEntry();
      jobEntryInterface.getLogChannel().setLogLevel( logLevel );

      // Track the fact that we are going to launch the next job entry...
      JobEntryResult jerBefore =
        new JobEntryResult( null, null, BaseMessages.getString( PKG, "Job.Comment.JobStarted" ), reason, jobEntryCopy
          .getName(), jobEntryCopy.getNr(), environmentSubstitute( jobEntryCopy.getEntry().getFilename() ) );
      jobTracker.addJobTracker( new JobTracker( jobMeta, jerBefore ) );

      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader( jobEntryInterface.getClass().getClassLoader() );
      // Execute this entry...
      JobEntryInterface cloneJei = (JobEntryInterface) jobEntryInterface.clone();
      ( (VariableSpace) cloneJei ).copyVariablesFrom( this );
      cloneJei.setMetaStore( getJobMeta().getMetaStore() );
      cloneJei.setParentJob( this );
      cloneJei.setParentJobMeta( this.getJobMeta() );
      final long start = System.currentTimeMillis();

      cloneJei.getLogChannel().logDetailed( "Starting job entry" );
      for ( JobEntryListener jobEntryListener : jobEntryListeners ) {
        jobEntryListener.beforeExecution( this, jobEntryCopy, cloneJei );
      }
      if ( interactive ) {
        if ( jobEntryCopy.isPipeline() ) {
          getActiveJobEntryPipeline().put( jobEntryCopy, (JobEntryPipeline) cloneJei );
        }
        if ( jobEntryCopy.isJob() ) {
          getActiveJobEntryJobs().put( jobEntryCopy, (JobEntryJob) cloneJei );
        }
      }
      log.snap( Metrics.METRIC_JOBENTRY_START, cloneJei.toString() );
      newResult = cloneJei.execute( prevResult, nr );
      log.snap( Metrics.METRIC_JOBENTRY_STOP, cloneJei.toString() );

      final long end = System.currentTimeMillis();
      if ( interactive ) {
        if ( jobEntryCopy.isPipeline() ) {
          getActiveJobEntryPipeline().remove( jobEntryCopy );
        }
        if ( jobEntryCopy.isJob() ) {
          getActiveJobEntryJobs().remove( jobEntryCopy );
        }
      }

      if ( cloneJei instanceof JobEntryPipeline ) {
        String throughput = newResult.getReadWriteThroughput( (int) ( ( end - start ) / 1000 ) );
        if ( throughput != null ) {
          log.logMinimal( throughput );
        }
      }
      for ( JobEntryListener jobEntryListener : jobEntryListeners ) {
        jobEntryListener.afterExecution( this, jobEntryCopy, cloneJei, newResult );
      }

      Thread.currentThread().setContextClassLoader( cl );
      addErrors( (int) newResult.getNrErrors() );

      // Also capture the logging text after the execution...
      //
      LoggingBuffer loggingBuffer = HopLogStore.getAppender();
      StringBuffer logTextBuffer = loggingBuffer.getBuffer( cloneJei.getLogChannel().getLogChannelId(), false );
      newResult.setLogText( logTextBuffer.toString() + newResult.getLogText() );

      // Save this result as well...
      //
      JobEntryResult jerAfter =
        new JobEntryResult( newResult, cloneJei.getLogChannel().getLogChannelId(), BaseMessages.getString( PKG,
          "Job.Comment.JobFinished" ), null, jobEntryCopy.getName(), jobEntryCopy.getNr(), environmentSubstitute(
          jobEntryCopy.getEntry().getFilename() ) );
      jobTracker.addJobTracker( new JobTracker( jobMeta, jerAfter ) );
      synchronized ( jobEntryResults ) {
        jobEntryResults.add( jerAfter );

        // Only keep the last X job entry results in memory
        //
        if ( maxJobEntriesLogged > 0 ) {
          while ( jobEntryResults.size() > maxJobEntriesLogged ) {
            // Remove the oldest.
            jobEntryResults.removeFirst();
          }
        }
      }
    }

    extension = new JobExecutionExtension( this, prevResult, jobEntryCopy, extension.executeEntry );
    ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobAfterJobEntryExecution.id, extension );

    // Try all next job entries.
    //
    // Keep track of all the threads we fired in case of parallel execution...
    // Keep track of the results of these executions too.
    //
    final List<Thread> threads = new ArrayList<Thread>();
    // next 2 lists is being modified concurrently so must be synchronized for this case.
    final Queue<Result> threadResults = new ConcurrentLinkedQueue<Result>();
    final Queue<HopException> threadExceptions = new ConcurrentLinkedQueue<HopException>();
    final List<JobEntryCopy> threadEntries = new ArrayList<JobEntryCopy>();

    // Launch only those where the hop indicates true or false
    //
    int nrNext = jobMeta.findNrNextJobEntries( jobEntryCopy );
    for ( int i = 0; i < nrNext && !isStopped(); i++ ) {
      // The next entry is...
      final JobEntryCopy nextEntry = jobMeta.findNextJobEntry( jobEntryCopy, i );

      // See if we need to execute this...
      final JobHopMeta hi = jobMeta.findJobHop( jobEntryCopy, nextEntry );

      // The next comment...
      final String nextComment;
      if ( hi.isUnconditional() ) {
        nextComment = BaseMessages.getString( PKG, "Job.Comment.FollowedUnconditional" );
      } else {
        if ( newResult.getResult() ) {
          nextComment = BaseMessages.getString( PKG, "Job.Comment.FollowedSuccess" );
        } else {
          nextComment = BaseMessages.getString( PKG, "Job.Comment.FollowedFailure" );
        }
      }

      //
      // If the link is unconditional, execute the next job entry (entries).
      // If the start point was an evaluation and the link color is correct:
      // green or red, execute the next job entry...
      //
      if ( hi.isUnconditional() || ( jobEntryCopy.evaluates() && ( !( hi.getEvaluation() ^ newResult
        .getResult() ) ) ) ) {
        // Start this next transform!
        if ( log.isBasic() ) {
          log.logBasic( BaseMessages.getString( PKG, "Job.Log.StartingEntry", nextEntry.getName() ) );
        }

        // Pass along the previous result, perhaps the next job can use it...
        // However, set the number of errors back to 0 (if it should be reset)
        // When an evaluation is executed the errors e.g. should not be reset.
        if ( nextEntry.resetErrorsBeforeExecution() ) {
          newResult.setNrErrors( 0 );
        }

        // Now execute!
        //
        // if (we launch in parallel, fire the execution off in a new thread...
        //
        if ( jobEntryCopy.isLaunchingInParallel() ) {
          threadEntries.add( nextEntry );

          Runnable runnable = new Runnable() {
            @Override public void run() {
              try {
                Result threadResult = execute( nr + 1, newResult, nextEntry, jobEntryCopy, nextComment );
                threadResults.add( threadResult );
              } catch ( Throwable e ) {
                log.logError( Const.getStackTracker( e ) );
                threadExceptions.add( new HopException( BaseMessages.getString( PKG, "Job.Log.UnexpectedError",
                  nextEntry.toString() ), e ) );
                Result threadResult = new Result();
                threadResult.setResult( false );
                threadResult.setNrErrors( 1L );
                threadResults.add( threadResult );
              }
            }
          };
          Thread thread = new Thread( runnable );
          threads.add( thread );
          thread.start();
          if ( log.isBasic() ) {
            log.logBasic( BaseMessages.getString( PKG, "Job.Log.LaunchedJobEntryInParallel", nextEntry.getName() ) );
          }
        } else {
          try {
            // Same as before: blocks until it's done
            //
            res = execute( nr + 1, newResult, nextEntry, jobEntryCopy, nextComment );
          } catch ( Throwable e ) {
            log.logError( Const.getStackTracker( e ) );
            throw new HopException( BaseMessages.getString( PKG, "Job.Log.UnexpectedError", nextEntry.toString() ),
              e );
          }
          if ( log.isBasic() ) {
            log.logBasic( BaseMessages.getString( PKG, "Job.Log.FinishedJobEntry", nextEntry.getName(), res.getResult()
              + "" ) );
          }
        }
      }
    }

    // OK, if we run in parallel, we need to wait for all the job entries to
    // finish...
    //
    if ( jobEntryCopy.isLaunchingInParallel() ) {
      for ( int i = 0; i < threads.size(); i++ ) {
        Thread thread = threads.get( i );
        JobEntryCopy nextEntry = threadEntries.get( i );

        try {
          thread.join();
        } catch ( InterruptedException e ) {
          log.logError( jobMeta.toString(), BaseMessages.getString( PKG,
            "Job.Log.UnexpectedErrorWhileWaitingForJobEntry", nextEntry.getName() ) );
          threadExceptions.add( new HopException( BaseMessages.getString( PKG,
            "Job.Log.UnexpectedErrorWhileWaitingForJobEntry", nextEntry.getName() ), e ) );
        }
      }
    }

    // Perhaps we don't have next transforms??
    // In this case, return the previous result.
    if ( res == null ) {
      res = prevResult;
    }

    // See if there where any errors in the parallel execution
    //
    if ( threadExceptions.size() > 0 ) {
      res.setResult( false );
      res.setNrErrors( threadExceptions.size() );

      for ( HopException e : threadExceptions ) {
        log.logError( jobMeta.toString(), e.getMessage(), e );
      }

      // Now throw the first Exception for good measure...
      //
      throw threadExceptions.poll();
    }

    // In parallel execution, we aggregate all the results, simply add them to
    // the previous result...
    //
    for ( Result threadResult : threadResults ) {
      res.add( threadResult );
    }

    // If there have been errors, logically, we need to set the result to
    // "false"...
    //
    if ( res.getNrErrors() > 0 ) {
      res.setResult( false );
    }

    return res;
  }

  /**
   * Wait until this job has finished.
   */
  public void waitUntilFinished() {
    waitUntilFinished( -1L );
  }

  /**
   * Wait until this job has finished.
   *
   * @param maxMiliseconds the maximum number of ms to wait
   */
  public void waitUntilFinished( long maxMiliseconds ) {
    long time = 0L;
    while ( isAlive() && ( time < maxMiliseconds || maxMiliseconds <= 0 ) ) {
      try {
        Thread.sleep( 1 );
        time += 1;
      } catch ( InterruptedException e ) {
        // Ignore sleep errors
      }
    }
  }

  /**
   * Get the number of errors that happened in the job.
   *
   * @return nr of error that have occurred during execution. During execution of a job the number can change.
   */
  public int getErrors() {
    return errors.get();
  }

  /**
   * Set the number of occured errors to 0.
   */
  public void resetErrors() {
    errors.set( 0 );
  }

  /**
   * Add a number of errors to the total number of erros that occured during execution.
   *
   * @param nrToAdd nr of errors to add.
   */
  public void addErrors( int nrToAdd ) {
    if ( nrToAdd > 0 ) {
      errors.addAndGet( nrToAdd );
    }
  }

  /**
   * Handle logging at start
   *
   * @return true if it went OK.
   * @throws HopException
   */
  public boolean beginProcessing() throws HopException {
    currentDate = new Date();
    logDate = new Date();
    startDate = Const.MIN_DATE;
    endDate = currentDate;

    resetErrors();

    final JobLogTable jobLogTable = jobMeta.getJobLogTable();
    int intervalInSeconds = Const.toInt( environmentSubstitute( jobLogTable.getLogInterval() ), -1 );

    if ( jobLogTable.isDefined() ) {

      DatabaseMeta logcon = jobMeta.getJobLogTable().getDatabaseMeta();
      String schemaName = environmentSubstitute( jobMeta.getJobLogTable().getActualSchemaName() );
      String tableName = environmentSubstitute( jobMeta.getJobLogTable().getActualTableName() );
      String schemaAndTable =
        jobMeta.getJobLogTable().getDatabaseMeta().getQuotedSchemaTableCombination( schemaName, tableName );
      Database ldb = new Database( this, logcon );
      ldb.shareVariablesWith( this );
      ldb.connect();
      ldb.setCommit( logCommitSize );

      try {
        // See if we have to add a batch id...
        Long id_batch = 1L;
        if ( jobMeta.getJobLogTable().isBatchIdUsed() ) {
          id_batch = logcon.getNextBatchId( ldb, schemaName, tableName, jobLogTable.getKeyField().getFieldName() );
          setBatchId( id_batch.longValue() );
          if ( getPassedBatchId() <= 0 ) {
            setPassedBatchId( id_batch.longValue() );
          }
        }

        Object[] lastr = ldb.getLastLogDate( schemaAndTable, jobMeta.getName(), true, LogStatus.END );
        if ( !Utils.isEmpty( lastr ) ) {
          Date last;
          try {
            last = ldb.getReturnRowMeta().getDate( lastr, 0 );
          } catch ( HopValueException e ) {
            throw new HopJobException( BaseMessages.getString( PKG, "Job.Log.ConversionError", "" + tableName ), e );
          }
          if ( last != null ) {
            startDate = last;
          }
        }

        depDate = currentDate;

        ldb.writeLogRecord( jobMeta.getJobLogTable(), LogStatus.START, this, null );
        if ( !ldb.isAutoCommit() ) {
          ldb.commitLog( true, jobMeta.getJobLogTable() );
        }
        ldb.disconnect();

        // If we need to do periodic logging, make sure to install a timer for
        // this...
        //
        if ( intervalInSeconds > 0 ) {
          final Timer timer = new Timer( getName() + " - interval logging timer" );
          TimerTask timerTask = new TimerTask() {
            @Override public void run() {
              try {
                endProcessing();
              } catch ( Exception e ) {
                log.logError( BaseMessages.getString( PKG, "Job.Exception.UnableToPerformIntervalLogging" ), e );
                // Also stop the show...
                //

                errors.incrementAndGet();
                stopAll();
              }
            }
          };
          timer.schedule( timerTask, intervalInSeconds * 1000, intervalInSeconds * 1000 );

          addJobListener( new JobAdapter() {
            @Override public void jobFinished( Job job ) {
              timer.cancel();
            }
          } );
        }

        // Add a listener at the end of the job to take of writing the final job
        // log record...
        //
        addJobListener( new JobAdapter() {
          @Override public void jobFinished( Job job ) throws HopException {
            try {
              endProcessing();
            } catch ( HopJobException e ) {
              log.logError( BaseMessages.getString( PKG, "Job.Exception.UnableToWriteToLoggingTable", jobLogTable
                .toString() ), e );
              // do not skip exception here
              // job is failed in case log database record is failed!
              throw new HopException( e );
            }
          }
        } );

      } catch ( HopDatabaseException dbe ) {
        addErrors( 1 ); // This is even before actual execution
        throw new HopJobException( BaseMessages.getString( PKG, "Job.Log.UnableToProcessLoggingStart", ""
          + tableName ), dbe );
      } finally {
        ldb.disconnect();
      }
    }

    // If we need to write out the job entry logging information, do so at the end of the job:
    //
    JobEntryLogTable jobEntryLogTable = jobMeta.getJobEntryLogTable();
    if ( jobEntryLogTable.isDefined() ) {
      addJobListener( new JobAdapter() {
        @Override public void jobFinished( Job job ) throws HopException {
          try {
            writeJobEntryLogInformation();
          } catch ( HopException e ) {
            throw new HopException( BaseMessages.getString( PKG,
              "Job.Exception.UnableToPerformJobEntryLoggingAtJobEnd" ), e );
          }
        }
      } );
    }

    // If we need to write the log channel hierarchy and lineage information,
    // add a listener for that too...
    //
    ChannelLogTable channelLogTable = jobMeta.getChannelLogTable();
    if ( channelLogTable.isDefined() ) {
      addJobListener( new JobAdapter() {

        @Override public void jobFinished( Job job ) throws HopException {
          try {
            writeLogChannelInformation();
          } catch ( HopException e ) {
            throw new HopException( BaseMessages.getString( PKG, "Job.Exception.UnableToPerformLoggingAtPipelineEnd" ),
              e );
          }
        }
      } );
    }

    JobExecutionExtension extension = new JobExecutionExtension( this, result, null, false );
    ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobBeginProcessing.id, extension );

    return true;
  }

  //
  // Handle logging at end

  /**
   * End processing.
   *
   * @return true, if successful
   * @throws HopJobException the kettle job exception
   */
  private boolean endProcessing() throws HopJobException {
    LogStatus status;
    if ( !isActive() ) {
      if ( isStopped() ) {
        status = LogStatus.STOP;
      } else {
        status = LogStatus.END;
      }
    } else {
      status = LogStatus.RUNNING;
    }
    try {
      if ( errors.get() == 0 && result != null && !result.getResult() ) {
        errors.incrementAndGet();
      }

      logDate = new Date();

      /*
       * Sums errors, read, written, etc.
       */

      JobLogTable jobLogTable = jobMeta.getJobLogTable();
      if ( jobLogTable.isDefined() ) {

        writeLogTableInformation( jobLogTable, status );
      }

      return true;
    } catch ( Exception e ) {
      throw new HopJobException( e ); // In case something else goes wrong.
    }
  }

  /**
   * Writes information to Job Log table. Cleans old records, in case job is finished.
   */
  protected void writeLogTableInformation( JobLogTable jobLogTable, LogStatus status ) throws HopJobException,
    HopDatabaseException {
    boolean cleanLogRecords = status.equals( LogStatus.END );
    String tableName = jobLogTable.getActualTableName();
    DatabaseMeta logcon = jobLogTable.getDatabaseMeta();

    Database ldb = createDataBase( logcon );
    ldb.shareVariablesWith( this );
    try {
      ldb.connect();
      ldb.setCommit( logCommitSize );
      ldb.writeLogRecord( jobLogTable, status, this, null );

      if ( cleanLogRecords ) {
        ldb.cleanupLogRecords( jobLogTable );
      }

    } catch ( HopDatabaseException dbe ) {
      addErrors( 1 );
      throw new HopJobException( "Unable to end processing by writing log record to table " + tableName, dbe );
    } finally {
      if ( !ldb.isAutoCommit() ) {
        ldb.commitLog( true, jobLogTable );
      }
      ldb.disconnect();
    }
  }

  /**
   * Write log channel information.
   *
   * @throws HopException the kettle exception
   */
  protected void writeLogChannelInformation() throws HopException {
    Database db = null;
    ChannelLogTable channelLogTable = jobMeta.getChannelLogTable();

    // PDI-7070: If parent job has the same channel logging info, don't duplicate log entries
    Job j = getParentJob();

    if ( j != null ) {
      if ( channelLogTable.equals( j.getJobMeta().getChannelLogTable() ) ) {
        return;
      }
    }
    // end PDI-7070

    try {
      db = new Database( this, channelLogTable.getDatabaseMeta() );
      db.shareVariablesWith( this );
      db.connect();
      db.setCommit( logCommitSize );

      List<LoggingHierarchy> loggingHierarchyList = getLoggingHierarchy();
      for ( LoggingHierarchy loggingHierarchy : loggingHierarchyList ) {
        db.writeLogRecord( channelLogTable, LogStatus.START, loggingHierarchy, null );
      }

      // Also time-out the log records in here...
      //
      db.cleanupLogRecords( channelLogTable );

    } catch ( Exception e ) {
      throw new HopException( BaseMessages.getString( PKG,
        "Pipeline.Exception.UnableToWriteLogChannelInformationToLogTable" ), e );
    } finally {
      if ( !db.isAutoCommit() ) {
        db.commit( true );
      }
      db.disconnect();
    }
  }

  /**
   * Write job entry log information.
   *
   * @throws HopException the kettle exception
   */
  protected void writeJobEntryLogInformation() throws HopException {
    Database db = null;
    JobEntryLogTable jobEntryLogTable = getJobMeta().getJobEntryLogTable();
    try {
      db = createDataBase( jobEntryLogTable.getDatabaseMeta() );
      db.shareVariablesWith( this );
      db.connect();
      db.setCommit( logCommitSize );

      for ( JobEntryCopy copy : getJobMeta().getJobCopies() ) {
        db.writeLogRecord( jobEntryLogTable, LogStatus.START, copy, this );
      }

      db.cleanupLogRecords( jobEntryLogTable );
    } catch ( Exception e ) {
      throw new HopException( BaseMessages.getString( PKG, "Job.Exception.UnableToJobEntryInformationToLogTable" ),
        e );
    } finally {
      if ( !db.isAutoCommit() ) {
        db.commitLog( true, jobEntryLogTable );
      }
      db.disconnect();
    }
  }

  protected Database createDataBase( DatabaseMeta databaseMeta ) {
    return new Database( this, databaseMeta );
  }

  public boolean isInitialized() {
    int exist = status.get() & BitMaskStatus.INITIALIZED.mask;
    return exist != 0;
  }

  protected void setInitialized( boolean initialized ) {
    status.updateAndGet( v -> initialized ? v | BitMaskStatus.INITIALIZED.mask : ( BitMaskStatus.BIT_STATUS_SUM
      ^ BitMaskStatus.INITIALIZED.mask ) & v );
  }

  public boolean isActive() {
    int exist = status.get() & BitMaskStatus.ACTIVE.mask;
    return exist != 0;
  }

  protected void setActive( boolean active ) {
    status.updateAndGet( v -> active ? v | BitMaskStatus.ACTIVE.mask : ( BitMaskStatus.BIT_STATUS_SUM
      ^ BitMaskStatus.ACTIVE.mask ) & v );
  }

  public boolean isStopped() {
    int exist = status.get() & BitMaskStatus.STOPPED.mask;
    return exist != 0;
  }

  /**
   * Stop all activity by setting the stopped property to true.
   */
  public void stopAll() {
    setStopped( true );
  }

  /**
   * Sets the stopped.
   */
  public void setStopped( boolean stopped ) {
    status.updateAndGet( v -> stopped ? v | BitMaskStatus.STOPPED.mask : ( BitMaskStatus.BIT_STATUS_SUM
      ^ BitMaskStatus.STOPPED.mask ) & v );
  }

  public boolean isFinished() {
    int exist = status.get() & BitMaskStatus.FINISHED.mask;
    return exist != 0;
  }

  public void setFinished( boolean finished ) {
    status.updateAndGet( v -> finished ? v | BitMaskStatus.FINISHED.mask : ( BitMaskStatus.BIT_STATUS_SUM
      ^ BitMaskStatus.FINISHED.mask ) & v );
  }

  public Date getStartDate() {
    return startDate;
  }

  public Date getEndDate() {
    return endDate;
  }

  public Date getCurrentDate() {
    return currentDate;
  }

  /**
   * Gets the dep date.
   *
   * @return Returns the depDate
   */
  public Date getDepDate() {
    return depDate;
  }

  public Date getLogDate() {
    return logDate;
  }

  public JobMeta getJobMeta() {
    return jobMeta;
  }

  public Thread getThread() {
    return this;
  }

  public JobTracker getJobTracker() {
    return jobTracker;
  }

  public void setJobTracker( JobTracker jobTracker ) {
    this.jobTracker = jobTracker;
  }

  public void setSourceRows( List<RowMetaAndData> sourceRows ) {
    this.sourceRows = sourceRows;
  }

  /**
   * Gets the source rows.
   *
   * @return the source rows
   */
  public List<RowMetaAndData> getSourceRows() {
    return sourceRows;
  }

  /**
   * Gets the parent job.
   *
   * @return Returns the parentJob
   */
  public Job getParentJob() {
    return parentJob;
  }

  /**
   * Sets the parent job.
   *
   * @param parentJob The parentJob to set.
   */
  public void setParentJob( Job parentJob ) {
    this.logLevel = parentJob.getLogLevel();
    this.log.setLogLevel( logLevel );
    this.containerObjectId = log.getContainerObjectId();
    this.parentJob = parentJob;
  }

  public Result getResult() {
    return result;
  }

  public void setResult( Result result ) {
    this.result = result;
  }

  public long getBatchId() {
    return batchId;
  }

  public void setBatchId( long batchId ) {
    this.batchId = batchId;
  }

  public long getPassedBatchId() {
    return passedBatchId;
  }

  public void setPassedBatchId( long jobBatchId ) {
    this.passedBatchId = jobBatchId;
  }

  /**
   * Sets the internal kettle variables.
   *
   * @param var the new internal kettle variables.
   */
  public void setInternalHopVariables( VariableSpace var ) {
    boolean hasFilename = jobMeta != null && !Utils.isEmpty( jobMeta.getFilename() );
    if ( hasFilename ) { // we have a finename that's defined.
      try {
        FileObject fileObject = HopVFS.getFileObject( jobMeta.getFilename(), this );
        FileName fileName = fileObject.getName();

        // The filename of the pipeline
        variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_NAME, fileName.getBaseName() );

        // The directory of the pipeline
        FileName fileDir = fileName.getParent();
        variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY, fileDir.getURI() );
      } catch ( Exception e ) {
        variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY, "" );
        variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_NAME, "" );
      }
    } else {
      variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY, "" );
      variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_NAME, "" );
    }

    // The name of the job
    variables.setVariable( Const.INTERNAL_VARIABLE_JOB_NAME, Const.NVL( jobMeta.getName(), "" ) );


  }

  protected void setInternalEntryCurrentDirectory( boolean hasFilename ) {
    variables.setVariable( Const.INTERNAL_VARIABLE_ENTRY_CURRENT_DIRECTORY, variables.getVariable(
      hasFilename ? Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY
        : Const.INTERNAL_VARIABLE_ENTRY_CURRENT_DIRECTORY ) );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#copyVariablesFrom(org.apache.hop.core.variables.VariableSpace)
   */
  @Override public void copyVariablesFrom( VariableSpace space ) {
    variables.copyVariablesFrom( space );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#environmentSubstitute(java.lang.String)
   */
  @Override public String environmentSubstitute( String aString ) {
    return variables.environmentSubstitute( aString );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#environmentSubstitute(java.lang.String[])
   */
  @Override public String[] environmentSubstitute( String[] aString ) {
    return variables.environmentSubstitute( aString );
  }

  @Override public String fieldSubstitute( String aString, RowMetaInterface rowMeta, Object[] rowData )
    throws HopValueException {
    return variables.fieldSubstitute( aString, rowMeta, rowData );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#getParentVariableSpace()
   */
  @Override public VariableSpace getParentVariableSpace() {
    return variables.getParentVariableSpace();
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.apache.hop.core.variables.VariableSpace#setParentVariableSpace(org.apache.hop.core.variables.VariableSpace)
   */
  @Override public void setParentVariableSpace( VariableSpace parent ) {
    variables.setParentVariableSpace( parent );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#getVariable(java.lang.String, java.lang.String)
   */
  @Override public String getVariable( String variableName, String defaultValue ) {
    return variables.getVariable( variableName, defaultValue );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#getVariable(java.lang.String)
   */
  @Override public String getVariable( String variableName ) {
    return variables.getVariable( variableName );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#getBooleanValueOfVariable(java.lang.String, boolean)
   */
  @Override public boolean getBooleanValueOfVariable( String variableName, boolean defaultValue ) {
    if ( !Utils.isEmpty( variableName ) ) {
      String value = environmentSubstitute( variableName );
      if ( !Utils.isEmpty( value ) ) {
        return ValueMetaString.convertStringToBoolean( value );
      }
    }
    return defaultValue;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * org.apache.hop.core.variables.VariableSpace#initializeVariablesFrom(org.apache.hop.core.variables.VariableSpace)
   */
  @Override public void initializeVariablesFrom( VariableSpace parent ) {
    variables.initializeVariablesFrom( parent );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#listVariables()
   */
  @Override public String[] listVariables() {
    return variables.listVariables();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#setVariable(java.lang.String, java.lang.String)
   */
  @Override public void setVariable( String variableName, String variableValue ) {
    variables.setVariable( variableName, variableValue );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#shareVariablesWith(org.apache.hop.core.variables.VariableSpace)
   */
  @Override public void shareVariablesWith( VariableSpace space ) {
    variables = space;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.variables.VariableSpace#injectVariables(java.util.Map)
   */
  @Override public void injectVariables( Map<String, String> prop ) {
    variables.injectVariables( prop );
  }

  public String getStatus() {
    String message;

    if ( isActive() ) {
      if ( isStopped() ) {
        message = Pipeline.STRING_HALTING;
      } else {
        message = Pipeline.STRING_RUNNING;
      }
    } else if ( isFinished() ) {
      message = Pipeline.STRING_FINISHED;
      if ( getResult().getNrErrors() > 0 ) {
        message += " (with errors)";
      }
    } else if ( isStopped() ) {
      message = Pipeline.STRING_STOPPED;
      if ( getResult().getNrErrors() > 0 ) {
        message += " (with errors)";
      }
    } else {
      message = Pipeline.STRING_WAITING;
    }

    return message;
  }

  /**
   * Send to slave server.
   *
   * @param jobMeta                the job meta
   * @param executionConfiguration the execution configuration
   * @param metaStore              the metaStore
   * @return the string
   * @throws HopException the kettle exception
   */
  public static String sendToSlaveServer( JobMeta jobMeta, JobExecutionConfiguration executionConfiguration, IMetaStore metaStore ) throws HopException {
    String carteObjectId;
    SlaveServer slaveServer = executionConfiguration.getRemoteServer();

    if ( slaveServer == null ) {
      throw new HopException( BaseMessages.getString( PKG, "Job.Log.NoSlaveServerSpecified" ) );
    }
    if ( Utils.isEmpty( jobMeta.getName() ) ) {
      throw new HopException( BaseMessages.getString( PKG, "Job.Log.UniqueJobName" ) );
    }

    // Align logging levels between execution configuration and remote server
    slaveServer.getLogChannel().setLogLevel( executionConfiguration.getLogLevel() );

    try {
      // Inject certain internal variables to make it more intuitive.
      //
      for ( String var : Const.INTERNAL_PIPELINE_VARIABLES ) {
        executionConfiguration.getVariables().put( var, jobMeta.getVariable( var ) );
      }
      for ( String var : Const.INTERNAL_JOB_VARIABLES ) {
        executionConfiguration.getVariables().put( var, jobMeta.getVariable( var ) );
      }

      if ( executionConfiguration.isPassingExport() ) {
        // First export the job... slaveServer.getVariable("MASTER_HOST")
        //
        FileObject tempFile =
          HopVFS.createTempFile( "jobExport", ".zip", System.getProperty( "java.io.tmpdir" ), jobMeta );

        TopLevelResource topLevelResource =
          ResourceUtil.serializeResourceExportInterface( tempFile.getName().toString(), jobMeta, jobMeta,
            metaStore, executionConfiguration.getXML(), CONFIGURATION_IN_EXPORT_FILENAME );

        // Send the zip file over to the slave server...
        String result =
          slaveServer.sendExport( topLevelResource.getArchiveName(), RegisterPackageServlet.TYPE_JOB, topLevelResource
            .getBaseResourceName() );
        WebResult webResult = WebResult.fromXMLString( result );
        if ( !webResult.getResult().equalsIgnoreCase( WebResult.STRING_OK ) ) {
          throw new HopException( "There was an error passing the exported job to the remote server: " + Const.CR
            + webResult.getMessage() );
        }
        carteObjectId = webResult.getId();
      } else {
        String xml = new JobConfiguration( jobMeta, executionConfiguration ).getXML();

        String reply = slaveServer.sendXML( xml, RegisterJobServlet.CONTEXT_PATH + "/?xml=Y" );
        WebResult webResult = WebResult.fromXMLString( reply );
        if ( !webResult.getResult().equalsIgnoreCase( WebResult.STRING_OK ) ) {
          throw new HopException( "There was an error posting the job on the remote server: " + Const.CR + webResult
            .getMessage() );
        }
        carteObjectId = webResult.getId();
      }

      // Start the job
      //
      String reply =
        slaveServer.execService( StartJobServlet.CONTEXT_PATH + "/?name=" + URLEncoder.encode( jobMeta.getName(),
          "UTF-8" ) + "&xml=Y&id=" + carteObjectId );
      WebResult webResult = WebResult.fromXMLString( reply );
      if ( !webResult.getResult().equalsIgnoreCase( WebResult.STRING_OK ) ) {
        throw new HopException( "There was an error starting the job on the remote server: " + Const.CR + webResult
          .getMessage() );
      }
      return carteObjectId;
    } catch ( HopException ke ) {
      throw ke;
    } catch ( Exception e ) {
      throw new HopException( e );
    }
  }

  public void addJobListener( JobListener jobListener ) {
    synchronized ( jobListeners ) {
      jobListeners.add( jobListener );
    }
  }

  public void addJobEntryListener( JobEntryListener jobEntryListener ) {
    jobEntryListeners.add( jobEntryListener );
  }

  public void removeJobListener( JobListener jobListener ) {
    synchronized ( jobListeners ) {
      jobListeners.remove( jobListener );
    }
  }

  public void removeJobEntryListener( JobEntryListener jobEntryListener ) {
    jobEntryListeners.remove( jobEntryListener );
  }

  public List<JobEntryListener> getJobEntryListeners() {
    return jobEntryListeners;
  }

  public List<JobListener> getJobListeners() {
    synchronized ( jobListeners ) {
      return new ArrayList<JobListener>( jobListeners );
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#addParameterDefinition(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override public void addParameterDefinition( String key, String defValue, String description ) throws DuplicateParamException {
    namedParams.addParameterDefinition( key, defValue, description );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#getParameterDescription(java.lang.String)
   */
  @Override public String getParameterDescription( String key ) throws UnknownParamException {
    return namedParams.getParameterDescription( key );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#getParameterDefault(java.lang.String)
   */
  @Override public String getParameterDefault( String key ) throws UnknownParamException {
    return namedParams.getParameterDefault( key );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#getParameterValue(java.lang.String)
   */
  @Override public String getParameterValue( String key ) throws UnknownParamException {
    return namedParams.getParameterValue( key );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#listParameters()
   */
  @Override public String[] listParameters() {
    return namedParams.listParameters();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#setParameterValue(java.lang.String, java.lang.String)
   */
  @Override public void setParameterValue( String key, String value ) throws UnknownParamException {
    namedParams.setParameterValue( key, value );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#eraseParameters()
   */
  public void eraseParameters() {
    namedParams.eraseParameters();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#clearParameters()
   */
  public void clearParameters() {
    namedParams.clearParameters();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#activateParameters()
   */
  public void activateParameters() {
    String[] keys = listParameters();

    for ( String key : keys ) {
      String value;
      try {
        value = getParameterValue( key );
      } catch ( UnknownParamException e ) {
        value = "";
      }
      String defValue;
      try {
        defValue = getParameterDefault( key );
      } catch ( UnknownParamException e ) {
        defValue = "";
      }

      if ( Utils.isEmpty( value ) ) {
        setVariable( key, Const.NVL( defValue, "" ) );
      } else {
        setVariable( key, Const.NVL( value, "" ) );
      }
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#copyParametersFrom(org.apache.hop.core.parameters.NamedParams)
   */
  public void copyParametersFrom( NamedParams params ) {
    namedParams.copyParametersFrom( params );
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.hop.core.parameters.NamedParams#mergeParametersWith(org.apache.hop.core.parameters.NamedParams,
   * boolean replace)
   */
  @Override
  public void mergeParametersWith( NamedParams params, boolean replace ) {
    namedParams.mergeParametersWith( params, replace );
  }

  /**
   * Sets the socket repository.
   *
   * @param socketRepository the new socket repository
   */
  public void setSocketRepository( SocketRepository socketRepository ) {
    this.socketRepository = socketRepository;
  }

  /**
   * Gets the socket repository.
   *
   * @return the socket repository
   */
  public SocketRepository getSocketRepository() {
    return socketRepository;
  }

  /**
   * Gets the log channel interface.
   *
   * @return LogChannelInterface
   */
  public LogChannelInterface getLogChannel() {
    return log;
  }

  /**
   * Gets the job name.
   *
   * @return jobName
   */
  public String getObjectName() {
    return getJobname();
  }

  /**
   * Always returns null for Job.
   *
   * @return null
   */
  public String getObjectCopy() {
    return null;
  }

  /**
   * Gets the file name.
   *
   * @return the filename
   */
  public String getFilename() {
    if ( jobMeta == null ) {
      return null;
    }
    return jobMeta.getFilename();
  }

  /**
   * Gets the log channel id.
   *
   * @return the logChannelId
   */
  public String getLogChannelId() {
    return log.getLogChannelId();
  }

  /**
   * Gets LoggingObjectType.JOB, which is always the value for Job.
   *
   * @return LoggingObjectType LoggingObjectType.JOB
   */
  public LoggingObjectType getObjectType() {
    return LoggingObjectType.JOB;
  }

  /**
   * Gets parent logging object.
   *
   * @return parentLoggingObject
   */
  public LoggingObjectInterface getParent() {
    return parentLoggingObject;
  }

  /**
   * Gets the logLevel.
   *
   * @return logLevel
   */
  public LogLevel getLogLevel() {
    return logLevel;
  }

  /**
   * Sets the log level.
   *
   * @param logLevel the new log level
   */
  public void setLogLevel( LogLevel logLevel ) {
    this.logLevel = logLevel;
    log.setLogLevel( logLevel );
  }

  /**
   * Gets the logging hierarchy.
   *
   * @return the logging hierarchy
   */
  public List<LoggingHierarchy> getLoggingHierarchy() {
    List<LoggingHierarchy> hierarchy = new ArrayList<LoggingHierarchy>();
    List<String> childIds = LoggingRegistry.getInstance().getLogChannelChildren( getLogChannelId() );
    for ( String childId : childIds ) {
      LoggingObjectInterface loggingObject = LoggingRegistry.getInstance().getLoggingObject( childId );
      if ( loggingObject != null ) {
        hierarchy.add( new LoggingHierarchy( getLogChannelId(), batchId, loggingObject ) );
      }
    }

    return hierarchy;
  }

  /**
   * Gets the boolean value of interactive.
   *
   * @return the interactive
   */
  public boolean isInteractive() {
    return interactive;
  }

  /**
   * Sets the value of interactive.
   *
   * @param interactive the interactive to set
   */
  public void setInteractive( boolean interactive ) {
    this.interactive = interactive;
  }

  /**
   * Gets the activeJobEntryPipelines.
   *
   * @return the activeJobEntryPipelines
   */
  public Map<JobEntryCopy, JobEntryPipeline> getActiveJobEntryPipeline() {
    return activeJobEntryPipeline;
  }

  /**
   * Gets the activeJobEntryJobs.
   *
   * @return the activeJobEntryJobs
   */
  public Map<JobEntryCopy, JobEntryJob> getActiveJobEntryJobs() {
    return activeJobEntryJobs;
  }

  /**
   * Gets a flat list of results in THIS job, in the order of execution of job entries.
   *
   * @return A flat list of results in THIS job, in the order of execution of job entries
   */
  public List<JobEntryResult> getJobEntryResults() {
    synchronized ( jobEntryResults ) {
      return new ArrayList<JobEntryResult>( jobEntryResults );
    }
  }

  /**
   * Gets the carteObjectId.
   *
   * @return the carteObjectId
   */
  public String getContainerObjectId() {
    return containerObjectId;
  }

  /**
   * Sets the execution container object id (containerObjectId).
   *
   * @param containerObjectId the execution container object id to set
   */
  public void setContainerObjectId( String containerObjectId ) {
    this.containerObjectId = containerObjectId;
  }

  /**
   * Gets the parent logging object.
   *
   * @return the parent logging object
   */
  public LoggingObjectInterface getParentLoggingObject() {
    return parentLoggingObject;
  }

  /**
   * Gets the registration date. For job, this always returns null
   *
   * @return null
   */
  public Date getRegistrationDate() {
    return null;
  }

  /**
   * Gets the start job entry copy.
   *
   * @return the startJobEntryCopy
   */
  public JobEntryCopy getStartJobEntryCopy() {
    return startJobEntryCopy;
  }

  /**
   * Sets the start job entry copy.
   *
   * @param startJobEntryCopy the startJobEntryCopy to set
   */
  public void setStartJobEntryCopy( JobEntryCopy startJobEntryCopy ) {
    this.startJobEntryCopy = startJobEntryCopy;
  }

  /**
   * Gets the executing server.
   *
   * @return the executingServer
   */
  public String getExecutingServer() {
    if ( executingServer == null ) {
      setExecutingServer( Const.getHostname() );
    }
    return executingServer;
  }

  /**
   * Sets the executing server.
   *
   * @param executingServer the executingServer to set
   */
  public void setExecutingServer( String executingServer ) {
    this.executingServer = executingServer;
  }

  /**
   * Gets the executing user.
   *
   * @return the executingUser
   */
  public String getExecutingUser() {
    return executingUser;
  }

  /**
   * Sets the executing user.
   *
   * @param executingUser the executingUser to set
   */
  public void setExecutingUser( String executingUser ) {
    this.executingUser = executingUser;
  }

  @Override
  public boolean isGatheringMetrics() {
    return log != null && log.isGatheringMetrics();
  }

  @Override
  public void setGatheringMetrics( boolean gatheringMetrics ) {
    if ( log != null ) {
      log.setGatheringMetrics( gatheringMetrics );
    }
  }

  @Override
  public boolean isForcingSeparateLogging() {
    return log != null && log.isForcingSeparateLogging();
  }

  @Override
  public void setForcingSeparateLogging( boolean forcingSeparateLogging ) {
    if ( log != null ) {
      log.setForcingSeparateLogging( forcingSeparateLogging );
    }
  }

  /**
   * Gets the transaction id.
   *
   * @return the transactionId
   */
  public String getTransactionId() {
    return transactionId;
  }

  /**
   * Sets the transaction id.
   *
   * @param transactionId the transactionId to set
   */
  public void setTransactionId( String transactionId ) {
    this.transactionId = transactionId;
  }

  public List<DelegationListener> getDelegationListeners() {
    return delegationListeners;
  }

  public void setDelegationListeners( List<DelegationListener> delegationListeners ) {
    this.delegationListeners = delegationListeners;
  }

  public void addDelegationListener( DelegationListener delegationListener ) {
    delegationListeners.add( delegationListener );
  }

  public Pipeline getParentPipeline() {
    return parentPipeline;
  }

  public void setParentPipeline( Pipeline parentPipeline ) {
    this.parentPipeline = parentPipeline;
  }

  public Map<String, Object> getExtensionDataMap() {
    return extensionDataMap;
  }

  public Result getStartJobEntryResult() {
    return startJobEntryResult;
  }

  public void setStartJobEntryResult( Result startJobEntryResult ) {
    this.startJobEntryResult = startJobEntryResult;
  }

  protected ExecutorService startHeartbeat( final long intervalInSeconds ) {

    final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor( new ThreadFactory() {

      @Override
      public Thread newThread( Runnable r ) {
        Thread thread = new Thread( r, "Job Heartbeat Thread for: " + getName() );
        thread.setDaemon( true );
        return thread;
      }
    } );

    heartbeat.scheduleAtFixedRate( new Runnable() {
      public void run() {

        if ( Job.this.isFinished() ) {
          log.logBasic( "Shutting down heartbeat signal for " + jobMeta.getName() );
          shutdownHeartbeat( heartbeat );
          return;
        }

        try {

          log.logDebug( "Triggering heartbeat signal for " + jobMeta.getName() + " at every " + intervalInSeconds
            + " seconds" );
          ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.JobHeartbeat.id, Job.this );

        } catch ( HopException e ) {
          log.logError( e.getMessage(), e );
        }
      }
    }, intervalInSeconds /* initial delay */, intervalInSeconds /* interval delay */, TimeUnit.SECONDS );

    return heartbeat;
  }

  protected void shutdownHeartbeat( ExecutorService heartbeat ) {

    if ( heartbeat != null ) {

      try {
        heartbeat.shutdownNow(); // prevents waiting tasks from starting and attempts to stop currently executing ones

      } catch ( Throwable t ) {
        /* do nothing */
      }
    }
  }

  private int getHeartbeatIntervalInSeconds() {

    JobMeta meta = this.jobMeta;

    // 1 - check if there's a user defined value ( job-specific ) heartbeat periodic interval;
    // 2 - check if there's a default defined value ( job-specific ) heartbeat periodic interval;
    // 3 - use default Const.HEARTBEAT_PERIODIC_INTERVAL_IN_SECS if none of the above have been set

    try {

      if ( meta != null ) {

        return Const.toInt( meta.getParameterValue( Const.VARIABLE_HEARTBEAT_PERIODIC_INTERVAL_SECS ), Const.toInt( meta
            .getParameterDefault( Const.VARIABLE_HEARTBEAT_PERIODIC_INTERVAL_SECS ),
          Const.HEARTBEAT_PERIODIC_INTERVAL_IN_SECS ) );
      }

    } catch ( Exception e ) {
      /* do nothing, return Const.HEARTBEAT_PERIODIC_INTERVAL_IN_SECS */
    }

    return Const.HEARTBEAT_PERIODIC_INTERVAL_IN_SECS;
  }
}
