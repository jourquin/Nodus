/*
 * Copyright (c) 1991-2025 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.gui;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.utils.BuildIdGenerator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;

/**
 * The splash screen to show while the main program is loading.
 *
 * @author Bart Jourquin
 */
public class Splash extends JWindow {

  /** This class loads and shows a picture. */
  private class SplashPicture extends JPanel {

    private static final long serialVersionUID = -2575505510842095637L;

    /** . */
    private Image img;

    /**
     * Get the picture.
     *
     * @param file Complete path to the file that contains the splash image
     */
    public SplashPicture(String file) {

      img = new ImageIcon(getClass().getResource(file)).getImage();

      width = img.getWidth(this);
      height = img.getHeight(this);

      this.repaint();
    }

    /**
     * .
     *
     * @hidden
     */
    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (img == null) {
        return;
      }

      // In case the image is bigger than the screen resolution...
      boolean zoom = width > getWidth() || height > getHeight();

      if (zoom) {
        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
      } else {
        g.drawImage(img, (getWidth() - width) / 2, (getHeight() - height) / 2, this);
      }
    }
  }

  /** . */
  private static final long serialVersionUID = 6796350856865644296L;

  /** . */
  private int height = 0;

  /** . */
  private int width = 0;

  /** Default constructor. */
  public Splash() {
    super();
  }

  /**
   * Creates a Splash that will appear until another frame hides it, but at least during "delay"
   * milliseconds.
   *
   * @param delay The delay in milliseconds.
   */
  public void display(int delay) {
    JPanel p = new JPanel();
    GridBagLayout gridBagLayout1 = new GridBagLayout();
    p.setLayout(gridBagLayout1);

    // Get Build ID
    BuildIdGenerator generator = new BuildIdGenerator();
    JLabel buildIdLabel = new JLabel(generator.getBuildId());
    buildIdLabel.setOpaque(false);
    buildIdLabel.setFont(new Font("Dialog", Font.BOLD, 12));
    buildIdLabel.setForeground(Color.white);
    p.add(
        buildIdLabel,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.SOUTHWEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 5, 5),
            0,
            0));

    // Display splash image
    String version = NodusC.VERSION;
    version = version.replace('.', '_');
    SplashPicture sp = new SplashPicture("NodusSplash" + version + ".png");
    sp.setPreferredSize(new Dimension(width, height));
    sp.setMinimumSize(new Dimension(width, height));
    sp.setMaximumSize(new Dimension(width, height));
    p.add(
        sp,
        new GridBagConstraints(
            0,
            0,
            1,
            2,
            0.1,
            0.1,
            GridBagConstraints.CENTER,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0));

    int frameBorder = 1;
    getContentPane().add(p);

    this.setSize(width + 2 * frameBorder, height + 2 * frameBorder);
    setLocationRelativeTo(null);

    // Display and set in foreground
    setVisible(true);
    setAlwaysOnTop(true);

    try {
      Thread.sleep(delay);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Hides the Splash.
   *
   * @param delay the delay in milliseconds.
   */
  public void dispose(int delay) {
    this.dispose();
  }
}
