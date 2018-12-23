/**
 * Copyright (c) 1991-2019 Université catholique de Louvain
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
 * not, see <http://www.gnu.org/licenses/>.
 */

package edu.uclouvain.core.nodus.tools.notepad;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.RepaintManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/**
 * Utility class that prints the content of the NotePad.
 *
 * @author Bart Jourquin
 */
public class Print implements Printable {

  private Component componentToBePrinted;

  private void disableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(false);
  }

  private void enableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(true);
  }

  /**
   * Prints the content of the NotePad.
   *
   * @param textPane The TextPane component of the NotePad.
   */
  public static void printNotePadContent(RSyntaxTextArea textPane) {

    // Do not print highlighting
    final boolean wasHighlighted = textPane.getHighlightCurrentLine();
    textPane.setHighlightCurrentLine(false);

    final int start = textPane.getSelectionStart();
    final int end = textPane.getSelectionEnd();

    textPane.setSelectionStart(0);
    textPane.setSelectionEnd(0);

    new Print(textPane).print();

    textPane.setSelectionStart(start);
    textPane.setSelectionEnd(end);

    textPane.setHighlightCurrentLine(wasHighlighted);
  }

  private Print(Component componentToBePrinted) {
    this.componentToBePrinted = componentToBePrinted;
  }

  /** Opens the print dialog and launches printing. */
  private void print() {
    PrinterJob printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);

    if (printJob.printDialog()) {
      try {
        printJob.print();
      } catch (PrinterException pe) {
        System.err.println("Error printing: " + pe);
      }
    }
  }

  /**
   * Print a graphic.
   *
   * @param g Graphics
   * @param pageFormat PageFormat
   * @param pageIndex int
   * @return int
   * @exclude
   */
  @Override
  public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
    if (pageIndex > 0) {
      return NO_SUCH_PAGE;
    } else {
      Graphics2D g2d = (Graphics2D) g;
      g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
      disableDoubleBuffering(componentToBePrinted);
      componentToBePrinted.paint(g2d);
      enableDoubleBuffering(componentToBePrinted);

      return PAGE_EXISTS;
    }
  }
}
