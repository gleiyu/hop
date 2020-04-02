package org.apache.hop.ui.hopgui.file.pipeline.delegates;

import org.apache.hop.core.Const;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformErrorMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.TransformMetaInterface;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.GUIResource;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HopGuiPipelineClipboardDelegate {
  private static final Class<?> PKG = HopGui.class; // i18n messages bundle location

  public static final String XML_TAG_PIPELINE_TRANSFORMS = "pipeline-transforms";
  private static final String XML_TAG_TRANSFORMS = "transforms";

  private HopGui hopGui;
  private HopGuiPipelineGraph pipelineGraph;
  private LogChannelInterface log;

  public HopGuiPipelineClipboardDelegate( HopGui hopGui, HopGuiPipelineGraph pipelineGraph ) {
    this.hopGui = hopGui;
    this.pipelineGraph = pipelineGraph;
    this.log = hopGui.getLog();
  }

  public void toClipboard( String clipText ) {
    try {
      GUIResource.getInstance().toClipboard( clipText );
    } catch ( Throwable e ) {
      new ErrorDialog( hopGui.getShell(), BaseMessages.getString( PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Title" ), BaseMessages
        .getString( PKG, "HopGui.Dialog.ExceptionCopyToClipboard.Message" ), e );
    }
  }

  public String fromClipboard() {
    try {
      return GUIResource.getInstance().fromClipboard();
    } catch ( Throwable e ) {
      new ErrorDialog(
        hopGui.getShell(), BaseMessages.getString( PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Title" ), BaseMessages
        .getString( PKG, "HopGui.Dialog.ExceptionPasteFromClipboard.Message" ), e );
      return null;
    }
  }

  public void pasteXML( PipelineMeta pipelineMeta, String clipcontent, Point loc ) {

    try {
      Document doc = XMLHandler.loadXMLString( clipcontent );
      Node pipelineNode = XMLHandler.getSubNode( doc, XML_TAG_PIPELINE_TRANSFORMS );
      // De-select all, re-select pasted transforms...
      pipelineMeta.unselectAll();

      Node transformsNode = XMLHandler.getSubNode( pipelineNode, "transforms" );
      int nr = XMLHandler.countNodes( transformsNode, "transform" );
      if ( log.isDebug() ) {
        // "I found "+nr+" transforms to paste on location: "
        log.logDebug( BaseMessages.getString( PKG, "HopGui.Log.FoundTransforms", "" + nr ) + loc );
      }
      TransformMeta[] transforms = new TransformMeta[ nr ];
      ArrayList<String> transformOldNames = new ArrayList<>( nr );

      // Point min = new Point(loc.x, loc.y);
      Point min = new Point( 99999999, 99999999 );

      // Load the transforms...
      for ( int i = 0; i < nr; i++ ) {
        Node transformNode = XMLHandler.getSubNodeByNr( transformsNode, "transform", i );
        transforms[ i ] = new TransformMeta( transformNode, hopGui.getMetaStore() );

        if ( loc != null ) {
          Point p = transforms[ i ].getLocation();

          if ( min.x > p.x ) {
            min.x = p.x;
          }
          if ( min.y > p.y ) {
            min.y = p.y;
          }
        }
      }

      // Load the hops...
      Node hopsNode = XMLHandler.getSubNode( pipelineNode, "order" );
      nr = XMLHandler.countNodes( hopsNode, "hop" );
      if ( log.isDebug() ) {
        // "I found "+nr+" hops to paste."
        log.logDebug( BaseMessages.getString( PKG, "HopGui.Log.FoundHops", "" + nr ) );
      }
      PipelineHopMeta[] hops = new PipelineHopMeta[ nr ];

      for ( int i = 0; i < nr; i++ ) {
        Node hopNode = XMLHandler.getSubNodeByNr( hopsNode, "hop", i );
        hops[ i ] = new PipelineHopMeta( hopNode, Arrays.asList( transforms ) );
      }

      // This is the offset:
      Point offset = new Point( loc.x - min.x, loc.y - min.y );

      // Undo/redo object positions...
      int[] position = new int[ transforms.length ];

      for ( int i = 0; i < transforms.length; i++ ) {
        Point p = transforms[ i ].getLocation();
        String name = transforms[ i ].getName();

        transforms[ i ].setLocation( p.x + offset.x, p.y + offset.y );

        // Check the name, find alternative...
        transformOldNames.add( name );
        transforms[ i ].setName( pipelineMeta.getAlternativeTransformName( name ) );
        pipelineMeta.addTransform( transforms[ i ] );
        position[ i ] = pipelineMeta.indexOfTransform( transforms[ i ] );
        transforms[ i ].setSelected( true );
      }

      // Add the hops too...
      for ( PipelineHopMeta hop : hops ) {
        pipelineMeta.addPipelineHop( hop );
      }

      // Load the notes...
      Node notesNode = XMLHandler.getSubNode( pipelineNode, "notepads" );
      nr = XMLHandler.countNodes( notesNode, "notepad" );
      if ( log.isDebug() ) {
        // "I found "+nr+" notepads to paste."
        log.logDebug( BaseMessages.getString( PKG, "HopGui.Log.FoundNotepads", "" + nr ) );
      }
      NotePadMeta[] notes = new NotePadMeta[ nr ];

      for ( int i = 0; i < notes.length; i++ ) {
        Node noteNode = XMLHandler.getSubNodeByNr( notesNode, "notepad", i );
        notes[ i ] = new NotePadMeta( noteNode );
        Point p = notes[ i ].getLocation();
        notes[ i ].setLocation( p.x + offset.x, p.y + offset.y );
        pipelineMeta.addNote( notes[ i ] );
        notes[ i ].setSelected( true );
      }

      // Set the source and target transforms ...
      for ( TransformMeta transform : transforms ) {
        TransformMetaInterface smi = transform.getTransformMetaInterface();
        smi.searchInfoAndTargetTransforms( pipelineMeta.getTransforms() );
      }

      // Set the error handling hops
      Node errorHandlingNode = XMLHandler.getSubNode( pipelineNode, PipelineMeta.XML_TAG_TRANSFORM_ERROR_HANDLING );
      int nrErrorHandlers = XMLHandler.countNodes( errorHandlingNode, TransformErrorMeta.XML_ERROR_TAG );
      for ( int i = 0; i < nrErrorHandlers; i++ ) {
        Node transformErrorMetaNode = XMLHandler.getSubNodeByNr( errorHandlingNode, TransformErrorMeta.XML_ERROR_TAG, i );
        TransformErrorMeta transformErrorMeta =
          new TransformErrorMeta( pipelineMeta.getParentVariableSpace(), transformErrorMetaNode, pipelineMeta.getTransforms() );

        // Handle pasting multiple times, need to update source and target transform names
        int srcTransformPos = transformOldNames.indexOf( transformErrorMeta.getSourceTransform().getName() );
        int tgtTransformPos = transformOldNames.indexOf( transformErrorMeta.getTargetTransform().getName() );
        TransformMeta sourceTransform = pipelineMeta.findTransform( transforms[ srcTransformPos ].getName() );
        if ( sourceTransform != null ) {
          sourceTransform.setTransformErrorMeta( transformErrorMeta );
        }
        sourceTransform.setTransformErrorMeta( null );
        if ( tgtTransformPos >= 0 ) {
          sourceTransform.setTransformErrorMeta( transformErrorMeta );
          TransformMeta targetTransform = pipelineMeta.findTransform( transforms[ tgtTransformPos ].getName() );
          transformErrorMeta.setSourceTransform( sourceTransform );
          transformErrorMeta.setTargetTransform( targetTransform );
        }
      }

      // Save undo information too...
      hopGui.undoDelegate.addUndoNew( pipelineMeta, transforms, position, false );

      int[] hopPos = new int[ hops.length ];
      for ( int i = 0; i < hops.length; i++ ) {
        hopPos[ i ] = pipelineMeta.indexOfPipelineHop( hops[ i ] );
      }
      hopGui.undoDelegate.addUndoNew( pipelineMeta, hops, hopPos, true );

      int[] notePos = new int[ notes.length ];
      for ( int i = 0; i < notes.length; i++ ) {
        notePos[ i ] = pipelineMeta.indexOfNote( notes[ i ] );
      }
      hopGui.undoDelegate.addUndoNew( pipelineMeta, notes, notePos, true );

    } catch ( HopException e ) {
      // "Error pasting transforms...",
      // "I was unable to paste transforms to this pipeline"
      new ErrorDialog( hopGui.getShell(), BaseMessages.getString( PKG, "HopGui.Dialog.UnablePasteTransforms.Title" ), BaseMessages
        .getString( PKG, "HopGui.Dialog.UnablePasteTransforms.Message" ), e );
    }
    pipelineGraph.redraw();
  }

  public void copySelected( PipelineMeta pipelineMeta, List<TransformMeta> transforms, List<NotePadMeta> notes ) {
    if ( transforms == null || transforms.size() == 0 ) {
      return;
    }

    StringBuilder xml = new StringBuilder( 5000 ).append( XMLHandler.getXMLHeader() );
    try {
      xml.append( XMLHandler.openTag( XML_TAG_PIPELINE_TRANSFORMS ) ).append( Const.CR );

      xml.append( XMLHandler.openTag( XML_TAG_TRANSFORMS ) ).append( Const.CR );
      for ( TransformMeta transform : transforms ) {
        xml.append( transform.getXML() );
      }
      xml.append( XMLHandler.closeTag( XML_TAG_TRANSFORMS ) ).append( Const.CR );

      // Also check for the hops in between the selected transforms...
      xml.append( XMLHandler.openTag( PipelineMeta.XML_TAG_ORDER ) ).append( Const.CR );
      for ( TransformMeta transform1 : transforms ) {
        for ( TransformMeta transform2 : transforms ) {
          if ( transform1 != transform2 ) {
            PipelineHopMeta hop = pipelineMeta.findPipelineHop( transform1, transform2, true );
            if ( hop != null ) {
              // Ok, we found one...
              xml.append( hop.getXML() ).append( Const.CR );
            }
          }
        }
      }
      xml.append( XMLHandler.closeTag( PipelineMeta.XML_TAG_ORDER ) ).append( Const.CR );

      xml.append( XMLHandler.openTag( PipelineMeta.XML_TAG_NOTEPADS ) ).append( Const.CR );
      if ( notes != null ) {
        for ( NotePadMeta note : notes ) {
          xml.append( note.getXML() );
        }
      }
      xml.append( XMLHandler.closeTag( PipelineMeta.XML_TAG_NOTEPADS ) ).append( Const.CR );

      xml.append( XMLHandler.openTag( PipelineMeta.XML_TAG_TRANSFORM_ERROR_HANDLING ) ).append( Const.CR );
      for ( TransformMeta transform : transforms ) {
        if ( transform.getTransformErrorMeta() != null ) {
          xml.append( transform.getTransformErrorMeta().getXML() ).append( Const.CR );
        }
      }
      xml.append( XMLHandler.closeTag( PipelineMeta.XML_TAG_TRANSFORM_ERROR_HANDLING ) ).append( Const.CR );

      xml.append( XMLHandler.closeTag( XML_TAG_PIPELINE_TRANSFORMS ) ).append( Const.CR );

      toClipboard( xml.toString() );
    } catch ( Exception ex ) {
      new ErrorDialog( hopGui.getShell(), "Error", "Error encoding to XML", ex );
    }
  }


  /**
   * Gets hopGui
   *
   * @return value of hopGui
   */
  public HopGui getHopGui() {
    return hopGui;
  }

  /**
   * @param hopGui The hopGui to set
   */
  public void setHopGui( HopGui hopGui ) {
    this.hopGui = hopGui;
  }

  /**
   * Gets pipelineGraph
   *
   * @return value of pipelineGraph
   */
  public HopGuiPipelineGraph getPipelineGraph() {
    return pipelineGraph;
  }

  /**
   * @param pipelineGraph The pipelineGraph to set
   */
  public void setPipelineGraph( HopGuiPipelineGraph pipelineGraph ) {
    this.pipelineGraph = pipelineGraph;
  }
}
