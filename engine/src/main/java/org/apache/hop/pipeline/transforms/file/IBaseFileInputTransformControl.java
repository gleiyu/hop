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

package org.apache.hop.pipeline.transforms.file;

import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowMetaInterface;

/**
 * Interface for some transform operations required for parse input file.
 */
public interface IBaseFileInputTransformControl {
  long incrementLinesInput();

  long getLinesWritten();

  void putRow( RowMetaInterface rowMeta, Object[] row ) throws HopTransformException;

  long getLinesInput();

  boolean checkFeedback( long lines );

  long incrementLinesUpdated();

  boolean failAfterBadFile( String errorMsg );

  void stopAll();

  long getErrors();

  void setErrors( long e );
}
