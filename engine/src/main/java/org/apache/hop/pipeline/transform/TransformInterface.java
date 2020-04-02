/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.pipeline.transform;

import org.apache.hop.core.ResultFile;
import org.apache.hop.core.RowSet;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.logging.HasLogChannelInterface;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.engine.IEngineComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The interface that any pipeline transform or plugin needs to implement.
 * <p>
 * Created on 12-AUG-2004
 *
 * @author Matt
 */

public interface TransformInterface<Meta extends TransformMetaInterface, Data extends TransformDataInterface >
  extends VariableSpace, HasLogChannelInterface, IEngineComponent {

  /**
   * @return the pipeline that is executing this transform
   */
  Pipeline getPipeline();

  /**
   * Perform the equivalent of processing one row. Typically this means reading a row from input (getRow()) and passing
   * a row to output (putRow)).
   *
   * @param smi The transforms metadata to work with
   * @param sdi The transforms temporary working data to work with (database connections, result sets, caches, temporary
   *            variables, etc.)
   * @return false if no more rows can be processed or an error occurred.
   * @throws HopException
   */
  boolean processRow( Meta smi, Data sdi ) throws HopException;

  /**
   * This method checks if the transform is capable of processing at least one row.
   * <p>
   * For example, if a transform has no input records but needs at least one to function, it will return false.
   *
   * @return true if the transform can process a row.
   */
  boolean canProcessOneRow();

  /**
   * Initialize and do work where other transforms need to wait for...
   *
   * @param transformMetaInterface The metadata to work with
   * @param transformDataInterface The data to initialize
   */
  boolean init( Meta transformMetaInterface, Data transformDataInterface );

  /**
   * Dispose of this transform: close files, empty logs, etc.
   *
   * @param sii The metadata to work with
   * @param sdi The data to dispose of
   */
  void dispose( Meta sii, Data sdi );

  /**
   * Mark the start time of the transform.
   */
  void markStart();

  /**
   * Mark the end time of the transform.
   */
  void markStop();

  /**
   * Stop running operations...
   *
   * @param transformMetaInterface The metadata that might be needed by the transform to stop running.
   * @param transformDataInterface The interface to the transform data containing the connections, resultsets, open files, etc.
   */
  void stopRunning( Meta transformMetaInterface, Data transformDataInterface ) throws HopException;

  /**
   * @return true if the transform is running after having been initialized
   */
  boolean isRunning();

  /**
   * Flag the transform as running or not
   *
   * @param running the running flag to set
   */
  void setRunning( boolean running );

  /**
   * @return True if the transform is marked as stopped. Execution should stop immediate.
   */
  boolean isStopped();

  /**
   * @param stopped true if the transform needs to be stopped
   */
  void setStopped( boolean stopped );

  /**
   * @param stopped true if the transform needs to be safe stopped
   */
  default void setSafeStopped( boolean stopped ) {
  }

  /**
   * @return true if transform is safe stopped.
   */
  default boolean isSafeStopped() {
    return false;
  }

  /**
   * @return True if the transform is paused
   */
  boolean isPaused();

  /**
   * Flags all rowsets as stopped/completed/finished.
   */
  void stopAll();

  /**
   * Pause a running transform
   */
  void pauseRunning();

  /**
   * Resume a running transform
   */
  void resumeRunning();

  /**
   * Get the name of the transform.
   *
   * @return the name of the transform
   */
  String getTransformName();

  /**
   * @return The transforms copy number (default 0)
   */
  int getCopy();

  /**
   * @return the type ID of the transform...
   */
  String getTransformPluginId();

  /**
   * Get the number of errors
   *
   * @return the number of errors
   */
  long getErrors();

  /**
   * Sets the number of errors
   *
   * @param errors the number of errors to set
   */
  void setErrors( long errors );

  /**
   * @return Returns the linesInput.
   */
  long getLinesInput();

  /**
   * @return Returns the linesOutput.
   */
  long getLinesOutput();

  /**
   * @return Returns the linesRead.
   */
  long getLinesRead();

  /**
   * @return Returns the linesWritten.
   */
  long getLinesWritten();

  /**
   * @return Returns the linesUpdated.
   */
  long getLinesUpdated();

  /**
   * @param linesRejected transforms the lines rejected by error handling.
   */
  void setLinesRejected( long linesRejected );

  /**
   * @return Returns the lines rejected by error handling.
   */
  long getLinesRejected();

  /**
   * Put a row on the destination rowsets.
   *
   * @param row The row to send to the destinations transforms
   */
  void putRow( RowMetaInterface row, Object[] data ) throws HopException;

  /**
   * @return a row from the source transform(s).
   */
  Object[] getRow() throws HopException;

  /**
   * Signal output done to destination transforms
   */
  void setOutputDone();

  /**
   * Add a rowlistener to the transform allowing you to inspect (or manipulate, be careful) the rows coming in or exiting the
   * transform.
   *
   * @param rowListener the rowlistener to add
   */
  void addRowListener( RowListener rowListener );

  /**
   * Remove a rowlistener from this transform.
   *
   * @param rowListener the rowlistener to remove
   */
  void removeRowListener( RowListener rowListener );

  /**
   * @return a list of the installed RowListeners
   */
  List<RowListener> getRowListeners();

  /**
   * @return The list of active input rowsets for the transform
   */
  List<RowSet> getInputRowSets();

  /**
   * @return The list of active output rowsets for the transform
   */
  List<RowSet> getOutputRowSets();

  /**
   * @return true if the transform is running partitioned
   */
  boolean isPartitioned();

  /**
   * @param partitionId the partitionID to set
   */
  void setPartitionId( String partitionId );

  /**
   * @return the transforms partition ID
   */
  String getPartitionId();

  /**
   * Call this method typically, after ALL the slave pipelines in a clustered run have finished.
   */
  void cleanup();

  /**
   * This method is executed by Pipeline right before the threads start and right after initialization.<br>
   * <br>
   * <b>!!! A plugin implementing this method should make sure to also call <i>super.initBeforeStart();</i> !!!</b>
   *
   * @throws HopTransformException In case there is an error
   */
  void initBeforeStart() throws HopTransformException;

  /**
   * Attach a transform listener to be notified when a transform arrives in a certain state. (finished)
   *
   * @param transformListener The listener to add to the transform
   */
  void addTransformListener( TransformListener transformListener );

  /**
   * @return true if the thread is a special mapping transform
   */
  boolean isMapping();

  /**
   * @return The metadata for this transform
   */
  TransformMeta getTransformMeta();

  /**
   * @return the logging channel for this transform
   */
  @Override LogChannelInterface getLogChannel();

  /**
   * @param usingThreadPriorityManagment set to true to actively manage priorities of transform threads
   */
  void setUsingThreadPriorityManagment( boolean usingThreadPriorityManagment );

  /**
   * @return true if we are actively managing priorities of transform threads
   */
  boolean isUsingThreadPriorityManagment();

  /**
   * @return The total amount of rows in the input buffers
   */
  int rowsetInputSize();

  /**
   * @return The total amount of rows in the output buffers
   */
  int rowsetOutputSize();

  /**
   * @return The number of "processed" lines of a transform. Well, a representable metric for that anyway.
   */
  long getProcessed();

  /**
   * @return The result files for this transform
   */
  Map<String, ResultFile> getResultFiles();

  /**
   * @return the description as in {@link TransformDataInterface}
   */
  BaseTransformData.TransformExecutionStatus getStatus();

  /**
   * @return The number of ms that this transform has been running
   */
  long getRuntime();

  /**
   * To be used to flag an error output channel of a transform prior to execution for performance reasons.
   */
  void identifyErrorOutput();

  /**
   * @param partitioned true if this transform is partitioned
   */
  void setPartitioned( boolean partitioned );

  /**
   * @param partitioningMethod The repartitioning method
   */
  void setRepartitioning( int partitioningMethod );

  /**
   * Calling this method will alert the transform that we finished passing a batch of records to the transform. Specifically for
   * transforms like "Sort Rows" it means that the buffered rows can be sorted and passed on.
   *
   * @throws HopException In case an error occurs during the processing of the batch of rows.
   */
  void batchComplete() throws HopException;

  /**
   * Pass along the metastore to use when loading external elements at runtime.
   *
   * @param metaStore The metastore to use
   */
  void setMetaStore( IMetaStore metaStore );

  /**
   * @return The metastore that the transform uses to load external elements from.
   */
  IMetaStore getMetaStore();

  /**
   * @return the index of the active (current) output row set
   */
  int getCurrentOutputRowSetNr();

  /**
   * @param index Sets the index of the active (current) output row set to use.
   */
  void setCurrentOutputRowSetNr( int index );

  /**
   * @return the index of the active (current) input row set
   */
  int getCurrentInputRowSetNr();

  /**
   * @param index Sets the index of the active (current) input row set to use.
   */
  void setCurrentInputRowSetNr( int index );

  default Collection<TransformStatus> subStatuses() {
    return Collections.emptyList();
  }

  default void addRowSetToInputRowSets( RowSet rowSet ) {
    getInputRowSets().add( rowSet );
  }

  default void addRowSetToOutputRowSets( RowSet rowSet ) {
    getOutputRowSets().add( rowSet );
  }

}
