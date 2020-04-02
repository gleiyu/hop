package org.apache.hop.ui.hopgui.file.pipeline.context;

import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiAction;
import org.apache.hop.core.gui.plugin.GuiActionLambdaBuilder;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.hopgui.context.BaseGuiContextHandler;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;

import java.util.ArrayList;
import java.util.List;

public class HopGuiPipelineTransformContext extends BaseGuiContextHandler implements IGuiContextHandler {

  public static final String CONTEXT_ID = "HopGuiPipelineTransformContext";

  private PipelineMeta pipelineMeta;
  private TransformMeta transformMeta;
  private HopGuiPipelineGraph pipelineGraph;
  private Point click;
  private GuiActionLambdaBuilder<HopGuiPipelineTransformContext> lambdaBuilder;

  public HopGuiPipelineTransformContext( PipelineMeta pipelineMeta, TransformMeta transformMeta, HopGuiPipelineGraph pipelineGraph, Point click ) {
    super();
    this.pipelineMeta = pipelineMeta;
    this.transformMeta = transformMeta;
    this.pipelineGraph = pipelineGraph;
    this.click = click;
    this.lambdaBuilder = new GuiActionLambdaBuilder<>();
  }

  public String getContextId() {
    return CONTEXT_ID;
  }

  /**
   * Create a list of supported actions on a pipeline.
   * We'll add the creation of every possible transform as well as the modification of the pipeline itself.
   *
   * @return The list of supported actions
   */
  @Override public List<GuiAction> getSupportedActions() {
    List<GuiAction> actions = new ArrayList<>();

    // Get the actions from the plugins, sorted by ID...
    //
    List<GuiAction> pluginActions = getPluginActions( true );
    if ( pluginActions != null ) {
      for ( GuiAction pluginAction : pluginActions ) {
        actions.add( lambdaBuilder.createLambda( pluginAction, pipelineGraph, this, pipelineGraph ) );
      }
    }

    return actions;
  }


  /**
   * Gets pipelineMeta
   *
   * @return value of pipelineMeta
   */
  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  /**
   * @param pipelineMeta The pipelineMeta to set
   */
  public void setPipelineMeta( PipelineMeta pipelineMeta ) {
    this.pipelineMeta = pipelineMeta;
  }

  /**
   * Gets transformMeta
   *
   * @return value of transformMeta
   */
  public TransformMeta getTransformMeta() {
    return transformMeta;
  }

  /**
   * @param transformMeta The transformMeta to set
   */
  public void setTransformMeta( TransformMeta transformMeta ) {
    this.transformMeta = transformMeta;
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

  /**
   * Gets click
   *
   * @return value of click
   */
  public Point getClick() {
    return click;
  }

  /**
   * @param click The click to set
   */
  public void setClick( Point click ) {
    this.click = click;
  }
}
