/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.pipeline.transforms.creditcardvalidator;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformDataInterface;
import org.apache.hop.pipeline.transform.TransformInterface;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;
import org.w3c.dom.Node;

import java.util.List;

/*
 * Created on 03-Juin-2008
 *
 */

public class CreditCardValidatorMeta extends BaseTransformMeta implements TransformMetaInterface {
  private static Class<?> PKG = CreditCardValidatorMeta.class; // for i18n purposes, needed by Translator!!

  /**
   * dynamic field
   */
  private String fieldname;

  private String cardtype;

  private String notvalidmsg;

  /**
   * function result: new value name
   */
  private String resultfieldname;

  private boolean onlydigits;

  public CreditCardValidatorMeta() {
    super(); // allocate BaseTransformMeta
  }

  /**
   * @return Returns the fieldname.
   */
  public String getDynamicField() {
    return this.fieldname;
  }

  /**
   * @param fieldname The fieldname to set.
   */
  public void setDynamicField( String fieldname ) {
    this.fieldname = fieldname;
  }

  /**
   * @return Returns the resultName.
   */
  public String getResultFieldName() {
    return resultfieldname;
  }

  public void setOnlyDigits( boolean onlydigits ) {
    this.onlydigits = onlydigits;
  }

  public boolean isOnlyDigits() {
    return this.onlydigits;
  }

  /**
   * @param resultfieldname The resultfieldname to set.
   */
  public void setResultFieldName( String resultfieldname ) {
    this.resultfieldname = resultfieldname;
  }

  /**
   * @param cardtype The cardtype to set.
   */
  public void setCardType( String cardtype ) {
    this.cardtype = cardtype;
  }

  /**
   * @return Returns the cardtype.
   */
  public String getCardType() {
    return cardtype;
  }

  /**
   * @param notvalidmsg The notvalidmsg to set.
   */
  public void setNotValidMsg( String notvalidmsg ) {
    this.notvalidmsg = notvalidmsg;
  }

  /**
   * @return Returns the notvalidmsg.
   */
  public String getNotValidMsg() {
    return notvalidmsg;
  }

  public void loadXML( Node transformNode, IMetaStore metaStore ) throws HopXMLException {
    readData( transformNode );
  }

  public Object clone() {
    CreditCardValidatorMeta retval = (CreditCardValidatorMeta) super.clone();

    return retval;
  }

  public void setDefault() {
    resultfieldname = "result";
    onlydigits = false;
    cardtype = "card type";
    notvalidmsg = "not valid message";
  }

  public void getFields( RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, TransformMeta nextTransform,
                         VariableSpace space, IMetaStore metaStore ) throws HopTransformException {
    String realresultfieldname = space.environmentSubstitute( resultfieldname );
    if ( !Utils.isEmpty( realresultfieldname ) ) {
      ValueMetaInterface v = new ValueMetaBoolean( realresultfieldname );
      v.setOrigin( name );
      inputRowMeta.addValueMeta( v );
    }
    String realcardtype = space.environmentSubstitute( cardtype );
    if ( !Utils.isEmpty( realcardtype ) ) {
      ValueMetaInterface v = new ValueMetaString( realcardtype );
      v.setOrigin( name );
      inputRowMeta.addValueMeta( v );
    }
    String realnotvalidmsg = space.environmentSubstitute( notvalidmsg );
    if ( !Utils.isEmpty( notvalidmsg ) ) {
      ValueMetaInterface v = new ValueMetaString( realnotvalidmsg );
      v.setOrigin( name );
      inputRowMeta.addValueMeta( v );
    }
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder();

    retval.append( "    " ).append( XMLHandler.addTagValue( "fieldname", fieldname ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "resultfieldname", resultfieldname ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "cardtype", cardtype ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "onlydigits", onlydigits ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "notvalidmsg", notvalidmsg ) );

    return retval.toString();
  }

  private void readData( Node transformNode ) throws HopXMLException {
    try {
      fieldname = XMLHandler.getTagValue( transformNode, "fieldname" );
      resultfieldname = XMLHandler.getTagValue( transformNode, "resultfieldname" );
      cardtype = XMLHandler.getTagValue( transformNode, "cardtype" );
      notvalidmsg = XMLHandler.getTagValue( transformNode, "notvalidmsg" );
      onlydigits = "Y".equalsIgnoreCase( XMLHandler.getTagValue( transformNode, "onlydigits" ) );

    } catch ( Exception e ) {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "CreditCardValidatorMeta.Exception.UnableToReadTransformMeta" ), e );
    }
  }

  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
                     IMetaStore metaStore ) {
    CheckResult cr;
    String error_message = "";

    String realresultfieldname = pipelineMeta.environmentSubstitute( resultfieldname );
    if ( Utils.isEmpty( realresultfieldname ) ) {
      error_message = BaseMessages.getString( PKG, "CreditCardValidatorMeta.CheckResult.ResultFieldMissing" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_ERROR, error_message, transformMeta );
      remarks.add( cr );
    } else {
      error_message = BaseMessages.getString( PKG, "CreditCardValidatorMeta.CheckResult.ResultFieldOK" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_OK, error_message, transformMeta );
      remarks.add( cr );
    }
    if ( Utils.isEmpty( fieldname ) ) {
      error_message = BaseMessages.getString( PKG, "CreditCardValidatorMeta.CheckResult.CardFieldMissing" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_ERROR, error_message, transformMeta );
      remarks.add( cr );
    } else {
      error_message = BaseMessages.getString( PKG, "CreditCardValidatorMeta.CheckResult.CardFieldOK" );
      cr = new CheckResult( CheckResult.TYPE_RESULT_OK, error_message, transformMeta );
      remarks.add( cr );
    }
    // See if we have input streams leading to this transform!
    if ( input.length > 0 ) {
      cr =
        new CheckResult( CheckResult.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "CreditCardValidatorMeta.CheckResult.ReceivingInfoFromOtherTransforms" ), transformMeta );
      remarks.add( cr );
    } else {
      cr =
        new CheckResult( CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "CreditCardValidatorMeta.CheckResult.NoInpuReceived" ), transformMeta );
      remarks.add( cr );
    }

  }

  public TransformInterface getTransform( TransformMeta transformMeta, TransformDataInterface transformDataInterface, int cnr,
                                PipelineMeta pipelineMeta, Pipeline pipeline ) {
    return new CreditCardValidator( transformMeta, transformDataInterface, cnr, pipelineMeta, pipeline );
  }

  public TransformDataInterface getTransformData() {
    return new CreditCardValidatorData();
  }

  public boolean supportsErrorHandling() {
    return true;
  }

}
