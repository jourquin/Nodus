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

package com.bbn.openmap.layer;

import com.bbn.openmap.util.PropUtils;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * A LabelLayer with editable text.
 *
 * @author Bart Jourquin
 */
public class NodusLabelLayer extends LabelLayer {

  private static final long serialVersionUID = -679570445355697169L;

  /** Property prefix. */
  private String prefix;

  /** The used properties. */
  private Properties props;
  
  /** Default constructor. */
  public NodusLabelLayer() {
    super();
  }

  /**
   * Invites the user to enter a text label to display. The new text is stored in the properties for
   * later use.
   *
   * @return Component
   */
  @Override
  public java.awt.Component getGUI() {
    String newLabel =
        (String)
            JOptionPane.showInputDialog(
                null,
                "Enter the label to display",
                "Label layer",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                getLabelText());

    if (newLabel != null) {
      setLabelText(newLabel);

      // Store the new label in properties
      props.setProperty(PropUtils.getScopedPropertyPrefix(prefix) + labelProperty, getLabelText());

      prepare();
      repaint();
    }
    return null;
  }

  /**
   * Intercepts the layer prefix and properties before passing them to the super class.
   *
   * @hidden
   */
  @Override
  public void setProperties(String prefix, Properties props) {
    super.setProperties(prefix, props);

    this.props = props;
    this.prefix = prefix;
  }
}
