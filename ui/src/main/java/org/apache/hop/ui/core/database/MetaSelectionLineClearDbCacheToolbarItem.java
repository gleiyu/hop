/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hop.ui.core.database;

import org.apache.hop.core.DbCache;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.toolbar.GuiToolbarElementFilter;
import org.apache.hop.ui.core.widget.MetaSelectionLine;

@GuiPlugin
public class MetaSelectionLineClearDbCacheToolbarItem {
  public static final String TOOLBAR_ITEM_CLEAR_CACHE = "10100-metadata-clear-cache";

  public MetaSelectionLineClearDbCacheToolbarItem() {}

  @GuiToolbarElement(
      root = MetaSelectionLine.GUI_PLUGIN_TOOLBAR_PARENT_ID,
      id = TOOLBAR_ITEM_CLEAR_CACHE,
      toolTip = "Clear the database cache",
      image = "ui/images/clear.svg")
  public void clearDatabaseCache() {
    DbCache.getInstance().clear(null);
  }

  @GuiToolbarElementFilter(parentId = MetaSelectionLine.GUI_PLUGIN_TOOLBAR_PARENT_ID)
  public static boolean showForDatabaseMetaLines(String itemId, Object guiPluginInstance) {
    if (!TOOLBAR_ITEM_CLEAR_CACHE.equals(itemId)) {
      return true;
    }
    if (!(guiPluginInstance instanceof MetaSelectionLine<?>)) {
      return true;
    }
    MetaSelectionLine<?> line = (MetaSelectionLine<?>) guiPluginInstance;
    return line.getManagedClass().equals(DatabaseMeta.class);
  }
}
