/*
 * Copyright (c) 1991-2024 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

package com.bbn.openmap.layer.shape;

import com.bbn.openmap.Environment;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.util.I18n;
import com.bbn.openmap.util.PropUtils;
import edu.uclouvain.core.nodus.NodusC;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * This layer displays political boundaries, which ShapeFile is embedded in the Nodus .jar file.
 *
 * @author Bart Jourquin
 */
public class PoliticalBoundariesLayer extends ShapeLayer {

  private static I18n i18n = Environment.getI18n();

  private static PoliticalBoundariesLayer politicalBoundariesLayer = null;

  private static final long serialVersionUID = 7432485460581937500L;

  /**
   * Static method that returns a layer, initialized using the embedded ShapeFile.
   *
   * @param mapBean Map bean
   * @return A ShapeLayer containing the political boundaries.
   */
  public static PoliticalBoundariesLayer getLayer(MapBean mapBean) {

    // Load from resources if needed
    if (politicalBoundariesLayer == null) {
      // Load the political boundaries
      Properties props = new Properties();

      try {
        InputStream in =
            PoliticalBoundariesLayer.class
                .getResource("politicalBoundaries.properties")
                .openStream();
        props.load(in);
      } catch (IOException ioe) { // Should never happen
        ioe.printStackTrace();
      }

      // Get the filenames of the cntry02 map data
      String shpFileName = PoliticalBoundariesLayer.class.getResource("cntry02.shp").getFile();

      // Run from within Eclipse or standalone JAR ?
      if (shpFileName.contains("jar!")) {
        shpFileName = "jar:" + shpFileName;
      }

      props.setProperty("shapePolitical.shapeFile", shpFileName);
      String boundaryLayerName =
          i18n.get(PoliticalBoundariesLayer.class, "Political_boundaries", "Political boundaries");
      props.setProperty("shapePolitical.prettyName", boundaryLayerName);

      List<String> startuplayers;
      List<String> layersValue;

      String s = props.getProperty(NodusC.PROP_OPENMAP_LAYERS);
      layersValue = PropUtils.parseSpacedMarkers(s);
      s = props.getProperty(NodusC.PROP_OPENMAP_STARTUPLAYERS);
      startuplayers = PropUtils.parseSpacedMarkers(s);
      Layer[] layers = LayerHandler.getLayers(layersValue, startuplayers, props);
      politicalBoundariesLayer = (PoliticalBoundariesLayer) layers[0];
      politicalBoundariesLayer.setAddAsBackground(true);
    }

    return politicalBoundariesLayer;
  }
}
