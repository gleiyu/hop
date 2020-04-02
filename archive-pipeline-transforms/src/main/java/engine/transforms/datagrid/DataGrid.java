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

package org.apache.hop.pipeline.transforms.datagrid;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformDataInterface;
import org.apache.hop.pipeline.transform.TransformInterface;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;

import java.util.List;

/**
 * Generates a number of (empty or the same) rows
 *
 * @author Matt
 * @since 4-apr-2003
 */
public class DataGrid extends BaseTransform implements TransformInterface {
  private static Class<?> PKG = DataGridMeta.class; // for i18n purposes, needed by Translator!!

  private DataGridMeta meta;
  private DataGridData data;

  public DataGrid( TransformMeta transformMeta, TransformDataInterface transformDataInterface, int copyNr, PipelineMeta pipelineMeta,
                   Pipeline pipeline ) {
    super( transformMeta, transformDataInterface, copyNr, pipelineMeta, pipeline );

    meta = (DataGridMeta) getTransformMeta().getTransformMetaInterface();
    data = (DataGridData) transformDataInterface;
  }

  public boolean processRow( TransformMetaInterface smi, TransformDataInterface sdi ) throws HopException {
    if ( data.linesWritten >= meta.getDataLines().size() ) {
      // no more rows to be written
      setOutputDone();
      return false;
    }

    if ( first ) {
      // The output meta is the original input meta + the
      // additional constant fields.

      first = false;
      data.linesWritten = 0;

      data.outputRowMeta = new RowMeta();
      meta.getFields( data.outputRowMeta, getTransformName(), null, null, this, metaStore );

      // Use these metadata values to convert data...
      //
      data.convertMeta = data.outputRowMeta.cloneToType( ValueMetaInterface.TYPE_STRING );
    }

    Object[] outputRowData = RowDataUtil.allocateRowData( data.outputRowMeta.size() );
    List<String> outputLine = meta.getDataLines().get( data.linesWritten );

    for ( int i = 0; i < data.outputRowMeta.size(); i++ ) {
      if ( meta.isSetEmptyString()[ i ] ) {
        // Set empty string
        outputRowData[ i ] = StringUtil.EMPTY_STRING;
      } else {

        ValueMetaInterface valueMeta = data.outputRowMeta.getValueMeta( i );
        ValueMetaInterface convertMeta = data.convertMeta.getValueMeta( i );
        String valueData = outputLine.get( i );

        if ( valueData != null && valueMeta.isNull( valueData ) ) {
          valueData = null;
        }
        outputRowData[ i ] = valueMeta.convertDataFromString( valueData, convertMeta, null, null, 0 );
      }
    }

    putRow( data.outputRowMeta, outputRowData );
    data.linesWritten++;

    if ( log.isRowLevel() ) {
      log.logRowlevel( toString(), BaseMessages.getString( PKG, "DataGrid.Log.Wrote.Row", Long
        .toString( getLinesWritten() ), data.outputRowMeta.getString( outputRowData ) ) );
    }

    if ( checkFeedback( getLinesWritten() ) ) {
      if ( log.isBasic() ) {
        logBasic( BaseMessages.getString( PKG, "DataGrid.Log.LineNr", Long.toString( getLinesWritten() ) ) );
      }
    }

    return true;
  }

  public boolean isWaitingForData() {
    return true;
  }

}
