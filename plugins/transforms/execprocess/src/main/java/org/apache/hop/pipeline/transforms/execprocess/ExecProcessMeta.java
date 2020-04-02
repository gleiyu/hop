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

package org.apache.hop.pipeline.transforms.execprocess;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.*;
import org.w3c.dom.Node;

import java.util.List;

/*
 * Created on 03-11-2008
 *
 */

@Transform(
        id = "ExecProcess",
        image = "ui/images/RPL.svg",
        i18nPackageName = "org.apache.hop.pipeline.transforms.execprocess",
        name = "BaseTransform.TypeLongDesc.ExecProcess",
        description = "BaseTransform.TypeTooltipDesc.ExecProcess",
        categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Utility"
)
public class ExecProcessMeta extends BaseTransformMeta implements TransformMetaInterface<ExecProcess, ExecProcessData> {
  private static Class<?> PKG = ExecProcessMeta.class; // for i18n purposes, needed by Translator!!

  /**
   * dynamic process field name
   */
  private String processfield;

  /**
   * function result: new value name
   */
  private String resultfieldname;

  /**
   * function result: error fieldname
   */
  private String errorfieldname;

  /**
   * function result: exit value fieldname
   */
  private String exitvaluefieldname;

  /**
   * fail if the exit status is different from 0
   **/
  private boolean failwhennotsuccess;

  /**
   * Output Line Delimiter - defaults to empty string for backward compatibility
   **/
  public String outputLineDelimiter = "";

  /**
   * Whether arguments for the command are provided in input fields
   **/
  private boolean argumentsInFields;

  /**
   * The field names where arguments should be found
   **/
  private String[] argumentFieldNames;

  public ExecProcessMeta() {
    super(); // allocate BaseTransformMeta
    allocate(0);
  }

  public void allocate(int argumentCount) {
    this.argumentFieldNames = new String[argumentCount];
  }

  /**
   * @return Returns the processfield.
   */
  public String getProcessField() {
    return processfield;
  }

  /**
   * @param processfield The processfield to set.
   */
  public void setProcessField(String processfield) {
    this.processfield = processfield;
  }

  /**
   * @return Returns the resultName.
   */
  public String getResultFieldName() {
    return resultfieldname;
  }

  /**
   * @param resultfieldname The resultfieldname to set.
   */
  public void setResultFieldName(String resultfieldname) {
    this.resultfieldname = resultfieldname;
  }

  /**
   * @return Returns the errorfieldname.
   */
  public String getErrorFieldName() {
    return errorfieldname;
  }

  /**
   * @param errorfieldname The errorfieldname to set.
   */
  public void setErrorFieldName(String errorfieldname) {
    this.errorfieldname = errorfieldname;
  }

  /**
   * @return Returns the exitvaluefieldname.
   */
  public String getExitValueFieldName() {
    return exitvaluefieldname;
  }

  /**
   * @param exitvaluefieldname The exitvaluefieldname to set.
   */
  public void setExitValueFieldName(String exitvaluefieldname) {
    this.exitvaluefieldname = exitvaluefieldname;
  }

  /**
   * @return Returns the failwhennotsuccess.
   */
  public boolean isFailWhenNotSuccess() {
    return failwhennotsuccess;
  }

  /**
   * @param failwhennotsuccess The failwhennotsuccess to set.
   * @deprecated due to method name typo
   */
  @Deprecated
  public void setFailWhentNoSuccess(boolean failwhennotsuccess) {
    setFailWhenNotSuccess(failwhennotsuccess);
  }

  /**
   * @param failwhennotsuccess The failwhennotsuccess to set.
   */
  public void setFailWhenNotSuccess(boolean failwhennotsuccess) {
    this.failwhennotsuccess = failwhennotsuccess;
  }

  @Override
  public void loadXML(Node transformNode, IMetaStore metaStore) throws HopXMLException {
    readData(transformNode, metaStore);
  }

  @Override
  public Object clone() {
    ExecProcessMeta retval = (ExecProcessMeta) super.clone();

    return retval;
  }

  @Override
  public void setDefault() {
    resultfieldname = "Result output";
    errorfieldname = "Error output";
    exitvaluefieldname = "Exit value";
    failwhennotsuccess = false;
  }

  @Override
  public void getFields(RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, TransformMeta nextTransform,
                        VariableSpace space, IMetaStore metaStore) throws HopTransformException {
    // Output fields (String)
    String realOutputFieldname = space.environmentSubstitute(resultfieldname);
    if (!Utils.isEmpty(realOutputFieldname)) {
      ValueMetaInterface v = new ValueMetaString(realOutputFieldname);
      v.setLength(100, -1);
      v.setOrigin(name);
      inputRowMeta.addValueMeta(v);
    }
    String realerrofieldname = space.environmentSubstitute(errorfieldname);
    if (!Utils.isEmpty(realerrofieldname)) {
      ValueMetaInterface v = new ValueMetaString(realerrofieldname);
      v.setLength(100, -1);
      v.setOrigin(name);
      inputRowMeta.addValueMeta(v);
    }
    String realexitvaluefieldname = space.environmentSubstitute(exitvaluefieldname);
    if (!Utils.isEmpty(realexitvaluefieldname)) {
      ValueMetaInterface v = new ValueMetaInteger(realexitvaluefieldname);
      v.setLength(ValueMetaInterface.DEFAULT_INTEGER_LENGTH, 0);
      v.setOrigin(name);
      inputRowMeta.addValueMeta(v);
    }
  }

  @Override
  public String getXML() {
    StringBuilder retval = new StringBuilder();

    retval.append("    " + XMLHandler.addTagValue("processfield", processfield));
    retval.append("    " + XMLHandler.addTagValue("resultfieldname", resultfieldname));
    retval.append("    " + XMLHandler.addTagValue("errorfieldname", errorfieldname));
    retval.append("    " + XMLHandler.addTagValue("exitvaluefieldname", exitvaluefieldname));
    retval.append("    " + XMLHandler.addTagValue("failwhennotsuccess", failwhennotsuccess));
    retval.append("    " + XMLHandler.addTagValue("outputlinedelimiter", outputLineDelimiter));
    retval.append("    ").append(XMLHandler.addTagValue("argumentsInFields", argumentsInFields));

    retval.append("    ").append(XMLHandler.openTag("argumentFields")).append(Const.CR);
    for (int i = 0; i < argumentFieldNames.length; i++) {
      retval.append("      ").append(XMLHandler.openTag("argumentField")).append(Const.CR);
      retval.append("        ").append(XMLHandler.addTagValue("argumentFieldName", argumentFieldNames[i]));
      retval.append("      ").append(XMLHandler.closeTag("argumentField")).append(Const.CR);
    }
    retval.append("    ").append(XMLHandler.closeTag("argumentFields")).append(Const.CR);
    return retval.toString();
  }

  private void readData(Node transformNode, IMetaStore metaStore) throws HopXMLException {
    try {
      processfield = XMLHandler.getTagValue(transformNode, "processfield");
      resultfieldname = XMLHandler.getTagValue(transformNode, "resultfieldname");
      errorfieldname = XMLHandler.getTagValue(transformNode, "errorfieldname");
      exitvaluefieldname = XMLHandler.getTagValue(transformNode, "exitvaluefieldname");
      failwhennotsuccess = "Y".equalsIgnoreCase(XMLHandler.getTagValue(transformNode, "failwhennotsuccess"));
      outputLineDelimiter = XMLHandler.getTagValue(transformNode, "outputlinedelimiter");
      if (outputLineDelimiter == null) {
        outputLineDelimiter = ""; // default to empty string for backward compatibility
      }

      argumentsInFields = "Y".equalsIgnoreCase(XMLHandler.getTagValue(transformNode, "argumentsInFields"));
      Node argumentFieldsNode = XMLHandler.getSubNode(transformNode, "argumentFields");
      if (argumentFieldsNode == null) {
        argumentFieldNames = new String[0];
      } else {
        int argumentFieldCount = XMLHandler.countNodes(argumentFieldsNode, "argumentField");
        argumentFieldNames = new String[argumentFieldCount];
        for (int i = 0; i < argumentFieldCount; i++) {
          Node fnode = XMLHandler.getSubNodeByNr(argumentFieldsNode, "argumentField", i);
          argumentFieldNames[i] = XMLHandler.getTagValue(fnode, "argumentFieldName");
        }
      }

    } catch (Exception e) {
      throw new HopXMLException(
              BaseMessages.getString(PKG, "ExecProcessMeta.Exception.UnableToReadTransformMeta"), e);
    }
  }

  @Override
  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, TransformMeta transformMeta,
                     RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
                     IMetaStore metaStore) {
    CheckResult cr;
    String error_message = "";

    if (Utils.isEmpty(resultfieldname)) {
      error_message = BaseMessages.getString(PKG, "ExecProcessMeta.CheckResult.ResultFieldMissing");
      cr = new CheckResult(CheckResult.TYPE_RESULT_ERROR, error_message, transformMeta );
    } else {
      error_message = BaseMessages.getString(PKG, "ExecProcessMeta.CheckResult.ResultFieldOK");
      cr = new CheckResult(CheckResult.TYPE_RESULT_OK, error_message, transformMeta );
    }
    remarks.add(cr);

    if (Utils.isEmpty(processfield)) {
      error_message = BaseMessages.getString(PKG, "ExecProcessMeta.CheckResult.ProcessFieldMissing");
      cr = new CheckResult(CheckResult.TYPE_RESULT_ERROR, error_message, transformMeta );
    } else {
      error_message = BaseMessages.getString(PKG, "ExecProcessMeta.CheckResult.ProcessFieldOK");
      cr = new CheckResult(CheckResult.TYPE_RESULT_OK, error_message, transformMeta );
    }
    remarks.add(cr);

    // See if we have input streams leading to this transform!
    if (input.length > 0) {
      cr =
              new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(
                      PKG, "ExecProcessMeta.CheckResult.ReceivingInfoFromOtherTransforms"), transformMeta );
    } else {
      cr =
              new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(
                      PKG, "ExecProcessMeta.CheckResult.NoInpuReceived"), transformMeta );
    }
    remarks.add(cr);

  }

  @Override
  public ExecProcess createTransform( TransformMeta transformMeta, ExecProcessData transformDataInterface, int cnr,
                                      PipelineMeta pipelineMeta, Pipeline pipeline ) {
    return new ExecProcess( transformMeta, transformDataInterface, cnr, pipelineMeta, pipeline );
  }

  @Override
  public ExecProcessData getTransformData() {
    return new ExecProcessData();
  }

  @Override
  public boolean supportsErrorHandling() {
    return failwhennotsuccess;
  }

  public void setOutputLineDelimiter(String value) {
    this.outputLineDelimiter = value;
  }

  public String getOutputLineDelimiter() {
    return outputLineDelimiter;
  }

  public boolean isArgumentsInFields() {
    return argumentsInFields;
  }

  public void setArgumentsInFields(boolean argumentsInFields) {
    this.argumentsInFields = argumentsInFields;
  }

  public String[] getArgumentFieldNames() {
    return argumentFieldNames;
  }

  public void setArgumentFieldNames(String[] argumentFieldNames) {
    this.argumentFieldNames = argumentFieldNames;
  }

  @Override
  public String getDialogClassName(){
    return ExecProcessDialog.class.getName();
  }

}