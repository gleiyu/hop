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

package org.apache.hop.core.injection;

import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformDataInterface;
import org.apache.hop.pipeline.transform.TransformInterface;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;

@InjectionSupported( localizationPrefix = "", groups = { "FILENAME_LINES", "FILENAME_LINES2" }, hide = {
  "FLONG_HIDDEN" } )
public class MetaBeanLevel1 extends BaseTransformMeta implements TransformMetaInterface<TransformInterface, TransformDataInterface> {

  @InjectionDeep
  private MetaBeanLevel2 sub;

  @Injection( name = "FBOOLEAN" )
  public boolean fboolean;
  @Injection( name = "FINT" )
  public int fint;
  @Injection( name = "FLONG" )
  public long flong;
  @Injection( name = "FLONG_HIDDEN" )
  public long flong_hidden;

  public MetaBeanLevel2 getSub() {
    return sub;
  }

  @Override public void setDefault() {
  }

  @Override public TransformInterface createTransform( TransformMeta transformMeta, TransformDataInterface transformDataInterface, int copyNr, PipelineMeta pipelineMeta, Pipeline pipeline ) {
    return null;
  }

  @Override public TransformDataInterface getTransformData() {
    return null;
  }
}
