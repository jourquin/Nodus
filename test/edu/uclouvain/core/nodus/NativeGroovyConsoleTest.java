/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
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

package edu.uclouvain.core.nodus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.uclouvain.core.nodus.mappanel.NativeGroovyConsole;
import groovy.console.ui.Console;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NativeGroovyConsoleTest {
  @Test
  void mouseSelectionCopiesReadOnlyOutputBeforeFocusArrives() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Console console = new Console();
          CopyPane input = new CopyPane();
          CopyPane output = new CopyPane();
          console.setInputArea(input);
          console.setOutputArea(output);
          output.setEditable(false);
          output.setText("Exception thrown\nNo such property: openProject");
          output.selectAll();
          console.setCopyFromComponent(input);
          NativeGroovyConsole.configureCopyTarget(console);

          // A popup Copy command can run before Swing delivers a focus change.
          MouseEvent press =
              new MouseEvent(
                  output, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON3);
          for (MouseListener listener : output.getMouseListeners()) {
            listener.mousePressed(press);
          }
          console.copy(new ActionEvent(output, ActionEvent.ACTION_PERFORMED, "Copy"));

          assertEquals(output.getSelectedText(), output.copied);
          assertNull(input.copied);
          assertFalse(output.isEditable());
        });
  }

  @Test
  void returningToEditorRestoresCopyAndSelectAllTarget() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Console console = new Console();
          CopyPane input = new CopyPane();
          CopyPane output = new CopyPane();
          console.setInputArea(input);
          console.setOutputArea(output);
          input.setText("println 'script'");
          output.setText("script output");
          output.selectAll();
          NativeGroovyConsole.configureCopyTarget(console);
          console.focusGained(new FocusEvent(output, FocusEvent.FOCUS_GAINED));

          FocusEvent focus = new FocusEvent(input, FocusEvent.FOCUS_GAINED);
          for (FocusListener listener : input.getFocusListeners()) {
            listener.focusGained(focus);
          }
          ActionEvent action = new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "Copy");
          console.selectAll(action);
          console.copy(action);

          assertEquals("println 'script'", input.copied);
          assertNull(output.copied);
        });
  }

  /** Captures the copy request without reading or modifying the system clipboard. */
  private static class CopyPane extends JTextPane {
    private static final long serialVersionUID = 8977758967994904915L;
    private String copied;

    @Override
    public void copy() {
      copied = getSelectedText();
    }
  }
}
