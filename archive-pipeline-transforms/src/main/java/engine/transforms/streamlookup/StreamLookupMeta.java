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

package org.apache.hop.pipeline.transforms.streamlookup;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.injection.AfterInjection;
import org.apache.hop.core.injection.Injection;
import org.apache.hop.core.injection.InjectionSupported;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.iVariables;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.TransformIOMeta;
import org.apache.hop.pipeline.transform.TransformIOMetaInterface;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;
import org.apache.hop.pipeline.transform.errorhandling.Stream;
import org.apache.hop.pipeline.transform.errorhandling.StreamIcon;
import org.apache.hop.pipeline.transform.errorhandling.StreamInterface;
import org.apache.hop.pipeline.transform.errorhandling.StreamInterface.StreamType;
import org.w3c.dom.Node;

import java.util.List;

@InjectionSupported( localizationPrefix = "StreamLookupMeta.Injection." )
public class StreamLookupMeta extends BaseTransformMeta implements TransformMetaInterface {
  private static Class<?> PKG = StreamLookupMeta.class; // for i18n purposes, needed by Translator!!

  /**
   * fields in input streams with which we look up values
   */
  @Injection( name = "KEY_STREAM" )
  private String[] keystream;

  /**
   * fields in lookup stream with which we look up values
   */
  @Injection( name = "KEY_LOOKUP" )
  private String[] keylookup;

  /**
   * return these field values from lookup
   */
  @Injection( name = "RETRIEVE_VALUE" )
  private String[] value;

  /**
   * rename to this after lookup
   */
  @Injection( name = "RETRIEVE_VALUE_NAME" )
  private String[] valueName;

  /**
   * default value in case not found...
   */
  @Injection( name = "RETRIEVE_VALUE_DEFAULT" )
  private String[] valueDefault;

  /**
   * type of default value
   */
  @Injection( name = "RETRIEVE_DEFAULT_TYPE" )
  private int[] valueDefaultType;

  /**
   * Indicate that the input is considered sorted!
   */
  private boolean inputSorted;

  /**
   * Indicate that we need to preserve memory by serializing objects
   */
  @Injection( name = "PRESERVE_MEMORY" )
  private boolean memoryPreservationActive;

  /**
   * Indicate that we want to use a sorted list vs. a hashtable
   */
  @Injection( name = "SORTED_LIST" )
  private boolean usingSortedList;

  /**
   * The content of the key and lookup is a single Integer (long)
   */
  @Injection( name = "INTEGER_PAIR" )
  private boolean usingIntegerPair;

  public StreamLookupMeta() {
    super(); // allocate BaseTransformMeta
  }

  @Override
  public void loadXML( Node transformNode, IMetaStore metaStore ) throws HopXMLException {
    readData( transformNode );
  }

  public void allocate( int nrkeys, int nrvalues ) {
    setKeystream( new String[ nrkeys ] );
    setKeylookup( new String[ nrkeys ] );

    setValue( new String[ nrvalues ] );
    setValueName( new String[ nrvalues ] );
    setValueDefault( new String[ nrvalues ] );
    setValueDefaultType( new int[ nrvalues ] );
  }

  @Override
  public Object clone() {
    StreamLookupMeta retval = (StreamLookupMeta) super.clone();
    TransformIOMetaInterface thisTransformIO = getTransformIOMeta();
    TransformIOMetaInterface thatTransformIO = retval.getTransformIOMeta();
    if ( thisTransformIO != null
      && thisTransformIO.getInfoStreams() != null
      && thatTransformIO != null
      && thisTransformIO.getInfoStreams() != null ) {
      List<StreamInterface> thisInfoStream = thisTransformIO.getInfoStreams();
      List<StreamInterface> thatInfoStream = thatTransformIO.getInfoStreams();
      thatInfoStream.get( 0 ).setStreamType( thisInfoStream.get( 0 ).getStreamType() );
      thatInfoStream.get( 0 ).setTransformMeta( thisInfoStream.get( 0 ).getTransformMeta() );
      thatInfoStream.get( 0 ).setDescription( thisInfoStream.get( 0 ).getDescription() );
      thatInfoStream.get( 0 ).setStreamIcon( thisInfoStream.get( 0 ).getStreamIcon() );
      thatInfoStream.get( 0 ).setSubject( thisInfoStream.get( 0 ).getSubject() );
    }

    int nrkeys = keystream.length;
    int nrvals = value.length;
    retval.allocate( nrkeys, nrvals );
    System.arraycopy( keystream, 0, retval.keystream, 0, nrkeys );
    System.arraycopy( keylookup, 0, retval.keylookup, 0, nrkeys );
    System.arraycopy( value, 0, retval.value, 0, nrvals );
    System.arraycopy( valueName, 0, retval.valueName, 0, nrvals );
    System.arraycopy( valueDefault, 0, retval.valueDefault, 0, nrvals );
    System.arraycopy( valueDefaultType, 0, retval.valueDefaultType, 0, nrvals );
    return retval;
  }

  private void readData( Node transformNode ) throws HopXMLException {
    try {
      String dtype;
      int nrkeys, nrvalues;

      String lookupFromTransformName = XMLHandler.getTagValue( transformNode, "from" );
      StreamInterface infoStream = getTransformIOMeta().getInfoStreams().get( 0 );
      infoStream.setSubject( lookupFromTransformName );

      setInputSorted( "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, "input_sorted" ) ) );
      setMemoryPreservationActive( "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, "preserve_memory" ) ) );
      setUsingSortedList( "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, "sorted_list" ) ) );
      setUsingIntegerPair( "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, "integer_pair" ) ) );

      Node lookup = XMLHandler.getSubNode( transformNode, "lookup" );
      nrkeys = XMLHandler.countNodes( lookup, "key" );
      nrvalues = XMLHandler.countNodes( lookup, "value" );

      allocate( nrkeys, nrvalues );

      for ( int i = 0; i < nrkeys; i++ ) {
        Node knode = XMLHandler.getSubNodeByNr( lookup, "key", i );
        // CHECKSTYLE:Indentation:OFF
        getKeystream()[ i ] = XMLHandler.getTagValue( knode, "name" );
        getKeylookup()[ i ] = XMLHandler.getTagValue( knode, "field" );
        // CHECKSTYLE:Indentation:ON
      }

      for ( int i = 0; i < nrvalues; i++ ) {
        Node vnode = XMLHandler.getSubNodeByNr( lookup, "value", i );
        // CHECKSTYLE:Indentation:OFF
        getValue()[ i ] = XMLHandler.getTagValue( vnode, "name" );
        getValueName()[ i ] = XMLHandler.getTagValue( vnode, "rename" );
        if ( getValueName()[ i ] == null ) {
          getValueName()[ i ] = getValue()[ i ]; // default: same name to return!
        }

        getValueDefault()[ i ] = XMLHandler.getTagValue( vnode, "default" );
        dtype = XMLHandler.getTagValue( vnode, "type" );
        getValueDefaultType()[ i ] = ValueMetaFactory.getIdForValueMeta( dtype );
        // CHECKSTYLE:Indentation:ON
      }
    } catch ( Exception e ) {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "StreamLookupMeta.Exception.UnableToLoadTransformMetaFromXML" ), e );
    }
  }

  @Override
  public void searchInfoAndTargetTransforms( List<TransformMeta> transforms ) {
    List<StreamInterface> infoStreams = getTransformIOMeta().getInfoStreams();
    for ( StreamInterface stream : infoStreams ) {
      stream.setTransformMeta( TransformMeta.findTransform( transforms, (String) stream.getSubject() ) );
    }
  }

  @Override
  public void setDefault() {
    setMemoryPreservationActive( true );
    setUsingSortedList( false );
    setUsingIntegerPair( false );

    allocate( 0, 0 );
  }

  @Override
  public void getFields( IRowMeta row, String origin, IRowMeta[] info, TransformMeta nextTransform,
                         iVariables variables, IMetaStore metaStore ) throws HopTransformException {
    if ( info != null && info.length == 1 && info[ 0 ] != null ) {
      for ( int i = 0; i < getValueName().length; i++ ) {
        IValueMeta v = info[ 0 ].searchValueMeta( getValue()[ i ] );
        if ( v != null ) {
          // Configuration error/missing resources...
          v.setName( getValueName()[ i ] );
          v.setOrigin( origin );
          row.addValueMeta( v );
        } else {
          throw new HopTransformException( BaseMessages.getString( PKG,
            "StreamLookupMeta.Exception.ReturnValueCanNotBeFound", getValue()[ i ] ) );
        }
      }
    } else {
      for ( int i = 0; i < getValueName().length; i++ ) {
        try {
          IValueMeta v = ValueMetaFactory.createValueMeta( getValueName()[ i ], getValueDefaultType()[ i ] );
          v.setOrigin( origin );
          row.addValueMeta( v );
        } catch ( Exception e ) {
          throw new HopTransformException( e );
        }
      }
    }
  }

  @Override
  public String getXML() {
    StringBuilder retval = new StringBuilder();

    StreamInterface infoStream = getTransformIOMeta().getInfoStreams().get( 0 );
    retval.append( "    " ).append( XMLHandler.addTagValue( "from", infoStream.getTransformName() ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "input_sorted", isInputSorted() ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "preserve_memory", isMemoryPreservationActive() ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "sorted_list", isUsingSortedList() ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "integer_pair", isUsingIntegerPair() ) );

    retval.append( "    <lookup>" ).append( Const.CR );
    for ( int i = 0; i < getKeystream().length; i++ ) {
      retval.append( "      <key>" ).append( Const.CR );
      retval.append( "        " ).append( XMLHandler.addTagValue( "name", getKeystream()[ i ] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "field", getKeylookup()[ i ] ) );
      retval.append( "      </key>" ).append( Const.CR );
    }

    for ( int i = 0; i < getValue().length; i++ ) {
      retval.append( "      <value>" ).append( Const.CR );
      retval.append( "        " ).append( XMLHandler.addTagValue( "name", getValue()[ i ] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "rename", getValueName()[ i ] ) );
      retval.append( "        " ).append( XMLHandler.addTagValue( "default", getValueDefault()[ i ] ) );
      retval.append( "        " ).append(
        XMLHandler.addTagValue( "type", ValueMetaFactory.getValueMetaName( getValueDefaultType()[ i ] ) ) );
      retval.append( "      </value>" ).append( Const.CR );
    }
    retval.append( "    </lookup>" ).append( Const.CR );

    return retval.toString();
  }

  @Override
  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     IRowMeta prev, String[] input, String[] output, IRowMeta info, iVariables variables,
                     IMetaStore metaStore ) {
    CheckResult cr;

    if ( prev != null && prev.size() > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.TransformReceivingFields", prev.size() + "" ), transformMeta );
      remarks.add( cr );

      String error_message = "";
      boolean error_found = false;

      // Starting from selected fields in ...
      // Check the fields from the previous stream!
      for ( String aKeystream : getKeystream() ) {
        int idx = prev.indexOfValue( aKeystream );
        if ( idx < 0 ) {
          error_message += "\t\t" + aKeystream + Const.CR;
          error_found = true;
        }
      }
      if ( error_found ) {
        error_message =
          BaseMessages.getString( PKG, "StreamLookupMeta.CheckResult.FieldsNotFound" )
            + Const.CR + Const.CR + error_message;

        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, transformMeta );
        remarks.add( cr );
      } else {
        cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
            PKG, "StreamLookupMeta.CheckResult.AllFieldsFound" ), transformMeta );
        remarks.add( cr );
      }
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.CouldNotFindFieldsFromPreviousTransforms" ), transformMeta );
      remarks.add( cr );
    }

    if ( info != null && info.size() > 0 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.TransformReceivingLookupData", info.size() + "" ), transformMeta );
      remarks.add( cr );

      String error_message = "";
      boolean error_found = false;

      // Check the fields from the lookup stream!
      for ( String aKeylookup : getKeylookup() ) {
        int idx = info.indexOfValue( aKeylookup );
        if ( idx < 0 ) {
          error_message += "\t\t" + aKeylookup + Const.CR;
          error_found = true;
        }
      }
      if ( error_found ) {
        error_message =
          BaseMessages.getString( PKG, "StreamLookupMeta.CheckResult.FieldsNotFoundInLookupStream" )
            + Const.CR + Const.CR + error_message;

        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, transformMeta );
        remarks.add( cr );
      } else {
        cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
            PKG, "StreamLookupMeta.CheckResult.AllFieldsFoundInTheLookupStream" ), transformMeta );
        remarks.add( cr );
      }

      // Check the values to retrieve from the lookup stream!
      for ( String aValue : getValue() ) {
        int idx = info.indexOfValue( aValue );
        if ( idx < 0 ) {
          error_message += "\t\t" + aValue + Const.CR;
          error_found = true;
        }
      }
      if ( error_found ) {
        error_message =
          BaseMessages.getString( PKG, "StreamLookupMeta.CheckResult.FieldsNotFoundInLookupStream2" )
            + Const.CR + Const.CR + error_message;

        cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, error_message, transformMeta );
        remarks.add( cr );
      } else {
        cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
            PKG, "StreamLookupMeta.CheckResult.AllFieldsFoundInTheLookupStream2" ), transformMeta );
        remarks.add( cr );
      }
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.FieldsNotFoundFromInLookupSep" ), transformMeta );
      remarks.add( cr );
    }

    // See if the source transform is filled in!
    StreamInterface infoStream = getTransformIOMeta().getInfoStreams().get( 0 );
    if ( infoStream.getTransformMeta() == null ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.SourceTransformNotSelected" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.SourceTransformIsSelected" ), transformMeta );
      remarks.add( cr );

      // See if the transform exists!
      //
      if ( info != null ) {
        cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
            PKG, "StreamLookupMeta.CheckResult.SourceTransformExist", infoStream.getTransformName() ), transformMeta );
        remarks.add( cr );
      } else {
        cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
            PKG, "StreamLookupMeta.CheckResult.SourceTransformDoesNotExist", infoStream.getTransformName() ), transformMeta );
        remarks.add( cr );
      }
    }

    // See if we have input streams leading to this transform!
    if ( input.length >= 2 ) {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.TransformReceivingInfoFromInputTransforms", input.length + "" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "StreamLookupMeta.CheckResult.NeedAtLeast2InputStreams", Const.CR, Const.CR ), transformMeta );
      remarks.add( cr );
    }
  }

  @Override
  public ITransform getTransform( TransformMeta transformMeta, ITransformData data, int cnr,
                                PipelineMeta pipelineMeta, Pipeline pipeline ) {
    return new StreamLookup( transformMeta, this, data, cnr, pipelineMeta, pipeline );
  }

  @Override
  public ITransformData getTransformData() {
    return new StreamLookupData();
  }

  @Override
  public boolean excludeFromRowLayoutVerification() {
    return true;
  }

  /**
   * Returns the Input/Output metadata for this transform. The generator transform only produces output, does not accept input!
   */
  @Override
  public TransformIOMetaInterface getTransformIOMeta() {
    TransformIOMetaInterface ioMeta = super.getTransformIOMeta( false );
    if ( ioMeta == null ) {

      ioMeta = new TransformIOMeta( true, true, false, false, false, false );

      StreamInterface stream =
        new Stream( StreamType.INFO, null, BaseMessages.getString(
          PKG, "StreamLookupMeta.InfoStream.Description" ), StreamIcon.INFO, null );
      ioMeta.addStream( stream );
      setTransformIOMeta( ioMeta );
    }

    return ioMeta;
  }

  @Override
  public void resetTransformIoMeta() {
    // Do nothing, don't reset as there is no need to do this.
  }

  /**
   * @return Returns the inputSorted.
   */
  public boolean isInputSorted() {
    return inputSorted;
  }

  /**
   * @param inputSorted The inputSorted to set.
   */
  public void setInputSorted( boolean inputSorted ) {
    this.inputSorted = inputSorted;
  }

  /**
   * @return Returns the keylookup.
   */
  public String[] getKeylookup() {
    return keylookup;
  }

  /**
   * @param keylookup The keylookup to set.
   */
  public void setKeylookup( String[] keylookup ) {
    this.keylookup = keylookup;
  }

  /**
   * @return Returns the keystream.
   */
  public String[] getKeystream() {
    return keystream;
  }

  /**
   * @param keystream The keystream to set.
   */
  public void setKeystream( String[] keystream ) {
    this.keystream = keystream;
  }

  /**
   * @return Returns the value.
   */
  public String[] getValue() {
    return value;
  }

  /**
   * @param value The value to set.
   */
  public void setValue( String[] value ) {
    this.value = value;
  }

  /**
   * @return Returns the valueDefault.
   */
  public String[] getValueDefault() {
    return valueDefault;
  }

  /**
   * @param valueDefault The valueDefault to set.
   */
  public void setValueDefault( String[] valueDefault ) {
    this.valueDefault = valueDefault;
  }

  /**
   * @return Returns the valueDefaultType.
   */
  public int[] getValueDefaultType() {
    return valueDefaultType;
  }

  /**
   * @param valueDefaultType The valueDefaultType to set.
   */
  public void setValueDefaultType( int[] valueDefaultType ) {
    this.valueDefaultType = valueDefaultType;
  }

  /**
   * @return Returns the valueName.
   */
  public String[] getValueName() {
    return valueName;
  }

  /**
   * @param valueName The valueName to set.
   */
  public void setValueName( String[] valueName ) {
    this.valueName = valueName;
  }

  public boolean isMemoryPreservationActive() {
    return memoryPreservationActive;
  }

  public void setMemoryPreservationActive( boolean memoryPreservationActive ) {
    this.memoryPreservationActive = memoryPreservationActive;
  }

  public boolean isUsingSortedList() {
    return usingSortedList;
  }

  public void setUsingSortedList( boolean usingSortedList ) {
    this.usingSortedList = usingSortedList;
  }

  /**
   * @return the usingIntegerPair
   */
  public boolean isUsingIntegerPair() {
    return usingIntegerPair;
  }

  /**
   * @param usingIntegerPair the usingIntegerPair to set
   */
  public void setUsingIntegerPair( boolean usingIntegerPair ) {
    this.usingIntegerPair = usingIntegerPair;
  }

  /**
   * If we use injection we can have different arrays lengths.
   * We need synchronize them for consistency behavior with UI
   */
  @AfterInjection
  public void afterInjectionSynchronization() {
    if ( value == null || value.length == 0 ) {
      return;
    }
    int nrFields = value.length;
    //PDI-16110
    if ( valueDefaultType.length < nrFields ) {
      int[] newValueDefaultType = new int[ nrFields ];
      System.arraycopy( valueDefaultType, 0, newValueDefaultType, 0, valueDefaultType.length );
      for ( int i = valueDefaultType.length; i < newValueDefaultType.length; i++ ) {
        newValueDefaultType[ i ] = -1; //set a undefined value (<0). It will be correct processed in a handleNullIf method
      }
      valueDefaultType = newValueDefaultType;
    }
    if ( valueName.length < nrFields ) {
      String[] newValueName = new String[ nrFields ];
      System.arraycopy( valueName, 0, newValueName, 0, valueName.length );
      valueName = newValueName;
    }

    if ( valueDefault.length < nrFields ) {
      String[] newValueDefault = new String[ nrFields ];
      System.arraycopy( valueDefault, 0, newValueDefault, 0, valueDefault.length );
      valueDefault = newValueDefault;
    }

  }
}