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

package edu.uclouvain.core.nodus.tools.console;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.swing.GUIUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Console that intercepts System.out and System.err and displays their output in batches.
 *
 * <p>Producers append to a shared buffer without waiting for Swing. A coalescing timer displays
 * bounded batches every 50 milliseconds, updating the caret only once per batch. Clear removes
 * queued text too, and Save includes messages still awaiting display. Closing restores the previous
 * streams and releases the buffer and timer. The complete log remains available until the user
 * clears or closes the console.
 *
 * @author Bart Jourquin
 */
public class NodusConsole extends WindowAdapter {
  static I18n i18n = Environment.getI18n();

  private JTextPane textArea;

  private static String thisComponentName = "NodusConsole";

  /**
   * Returns true if the console is displayed.
   *
   * @return True if the console is visible.
   */
  public static boolean isVisible() {
    Frame[] frames = Frame.getFrames();

    for (Frame element : frames) {
      if (element instanceof JFrame) {
        JFrame f = (JFrame) element;
        if (thisComponentName.equals(f.getName()) && f.isVisible()) {
          return true;
        }
      }
    }
    return false;
  }

  private String defaultDirectory;

  private StyledDocument doc;

  private JFrame frame;

  private JButton clearButton;

  private JButton saveButton;

  private ActionListener clearActionListener;

  private ActionListener saveActionListener;

  private ConsoleOutputBuffer output;

  private Timer displayTimer;

  private boolean disposed;

  private PrintStream previousOut;

  private PrintStream previousErr;

  private PrintStream redirectedOut;

  private PrintStream redirectedErr;

  private Style outputStyle;

  private Style errorStyle;

  /** Initializes a new console. */
  public NodusConsole() {
    this(null);
  }

  /**
   * Initializes a new console and set the default directory in which the output can be saved.
   *
   * @param defaultDirectory The default directory used to save the output.
   */
  public NodusConsole(String defaultDirectory) {
    if (SwingUtilities.isEventDispatchThread()) {
      initialize(defaultDirectory);
    } else {
      try {
        SwingUtilities.invokeAndWait(() -> initialize(defaultDirectory));
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while opening the console", failure);
      } catch (InvocationTargetException failure) {
        throw new IllegalStateException("Cannot open the console", failure.getCause());
      }
    }
  }

  /** Creates and connects the console entirely on Swing's event thread. */
  private void initialize(String defaultDirectory) {
    this.defaultDirectory = defaultDirectory;

    // Only create a console if none exists
    for (Frame existing : Frame.getFrames()) {
      if (thisComponentName.equals(existing.getName()) && existing.isVisible()) {
        existing.setState(Frame.NORMAL);
        existing.toFront();
        return;
      }
    }

    // create all components and add them
    frame = new JFrame();
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setTitle(i18n.get(NodusConsole.class, "Nodus_Console", "Nodus Console"));
    frame.setName(thisComponentName);
    frame.setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus.png")));
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension frameSize = new Dimension(screenSize.width / 3, screenSize.height / 4);
    int x = frameSize.width / 20;
    int y = frameSize.height / 20;
    frame.setBounds(x, y, frameSize.width, frameSize.height);

    textArea = new JTextPane();
    textArea.setEditable(false);
    textArea.setBackground(Color.WHITE);
    doc = (StyledDocument) textArea.getDocument();
    outputStyle = doc.addStyle("ConsoleOutput", null);
    StyleConstants.setFontFamily(outputStyle, "MonoSpaced");
    StyleConstants.setFontSize(outputStyle, 12);
    StyleConstants.setForeground(outputStyle, Color.BLACK);
    errorStyle = doc.addStyle("ConsoleError", outputStyle);
    StyleConstants.setForeground(errorStyle, Color.RED);
    ((DefaultCaret) textArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

    clearButton = new JButton(i18n.get(NodusConsole.class, "Clear", "Clear"));
    saveButton = new JButton(i18n.get(NodusConsole.class, "Save", "Save"));

    frame.getContentPane().setLayout(new BorderLayout(10, 10));
    JScrollPane sp = new JScrollPane(textArea);
    sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    frame.getContentPane().add(clearButton, BorderLayout.NORTH);
    frame.getContentPane().add(sp, BorderLayout.CENTER);
    frame.getContentPane().add(saveButton, BorderLayout.SOUTH);
    registerEscapeCloseAction();
    GUIUtils.installToolTips(this, frame);
    frame.setVisible(true);

    frame.addWindowListener(this);

    clearActionListener =
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            clear();
          }
        };
    clearButton.addActionListener(clearActionListener);

    saveActionListener =
        new java.awt.event.ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (saveContent()) {
              System.out.println("--- Content saved ---");
            }
          }
        };
    saveButton.addActionListener(saveActionListener);

    output = new ConsoleOutputBuffer(Charset.defaultCharset());
    redirectStandardStreams();
    displayTimer =
        new Timer(
            50,
            event -> {
              if (!disposed) {
                appendToConsole(output.drain());
              }
            });
    displayTimer.start();
  }

  /** Makes the Escape key close the console. */
  private void registerEscapeCloseAction() {
    frame
        .getRootPane()
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
    frame
        .getRootPane()
        .getActionMap()
        .put(
            "close",
            new AbstractAction() {
              private static final long serialVersionUID = 8164810984853990830L;

              @Override
              public void actionPerformed(ActionEvent e) {
                closeConsole();
              }
            });
  }

  /** Redirects standard output and error streams to this console. */
  private void redirectStandardStreams() {
    previousOut = System.out;
    previousErr = System.err;

    try {
      redirectedOut = output.createStream(false);
      System.setOut(redirectedOut);
    } catch (SecurityException se) {
      showRedirectionError(
          "Couldn_t_redirect_STDOUT_to_this_console",
          "Couldn't redirect STDOUT to this console",
          se.getMessage());
    }

    try {
      redirectedErr = output.createStream(true);
      System.setErr(redirectedErr);
    } catch (SecurityException se) {
      showRedirectionError(
          "Couldn_t_redirect_STDERR_to_this_console",
          "Couldn't redirect STDERR to this console",
          se.getMessage());
    }
  }

  /** Displays a standard stream redirection error in the console text area. */
  private void showRedirectionError(String key, String defaultText, String message) {
    if (textArea != null) {
      textArea.setText(i18n.get(NodusConsole.class, key, defaultText) + "\n" + message);
    }
  }

  /** Restores the standard streams that were active before this console redirected them. */
  private void restoreStandardStreams() {
    if (previousOut != null && System.out == redirectedOut) {
      System.setOut(previousOut);
    }

    if (previousErr != null && System.err == redirectedErr) {
      System.setErr(previousErr);
    }
  }

  /** Closes redirected streams. */
  private void closeRedirectedStreams() {
    if (redirectedOut != null) {
      redirectedOut.close();
      redirectedOut = null;
    }

    if (redirectedErr != null) {
      redirectedErr.close();
      redirectedErr = null;
    }
  }

  /** Removes listeners installed by this console. */
  private void removeInstalledListeners() {
    if (clearButton != null && clearActionListener != null) {
      clearButton.removeActionListener(clearActionListener);
    }

    if (saveButton != null && saveActionListener != null) {
      saveButton.removeActionListener(saveActionListener);
    }

    if (frame != null) {
      frame.removeWindowListener(this);
    }
  }

  /** Clears references held by this console after the window has been disposed. */
  private void releaseReferences() {
    removeInstalledListeners();

    if (frame != null) {
      frame.getContentPane().removeAll();
    }

    textArea = null;
    doc = null;
    outputStyle = null;
    errorStyle = null;
    clearButton = null;
    saveButton = null;
    clearActionListener = null;
    saveActionListener = null;
    defaultDirectory = null;
    frame = null;
    previousOut = null;
    previousErr = null;
    output = null;
    displayTimer = null;
  }

  /** Clears both displayed and queued output on the event thread. */
  public void clear() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::clear);
      return;
    }
    if (textArea != null) {
      output.clear();
      textArea.setText("");
    }
  }

  /** Inserts a batch with fixed output/error styles and scrolls only after all insertions. */
  private void appendToConsole(List<ConsoleOutputBuffer.Chunk> batch) {
    if (disposed || batch.isEmpty()) {
      return;
    }
    try {
      for (ConsoleOutputBuffer.Chunk chunk : batch) {
        doc.insertString(doc.getLength(), chunk.text(), chunk.error ? errorStyle : outputStyle);
      }
      textArea.setCaretPosition(doc.getLength());
    } catch (BadLocationException failure) {
      // Report to the original stream, avoiding recursive logging into the failing document.
      failure.printStackTrace(previousErr);
    }
  }

  /**
   * Saves the content of the console in a text file.
   *
   * @return True on success.
   */
  private boolean saveContent() {
    if (textArea == null) {
      return false;
    }

    JFileChooser f = new JFileChooser(".");

    f.setDialogTitle(i18n.get(NodusConsole.class, "Save", "Save"));

    if (defaultDirectory != null) {
      f.setCurrentDirectory(new File(defaultDirectory));
    }

    int option = f.showSaveDialog(frame);

    if (option == JFileChooser.APPROVE_OPTION) {
      File file = f.getSelectedFile();

      if (file != null) {
        int answer = JOptionPane.YES_OPTION;
        if (file.exists()) {
          answer =
              JOptionPane.showConfirmDialog(
                  frame,
                  i18n.get(NodusConsole.class, "Overwrite", "Overwrite?"),
                  i18n.get(NodusConsole.class, "File_exists", "File exists"),
                  JOptionPane.YES_NO_OPTION);
        }
        if (answer == JOptionPane.YES_OPTION) {
          String extension = ".log";
          String fileName = file.getAbsolutePath();

          if (!fileName.endsWith(extension)) {
            fileName += extension;
          }

          appendToConsole(output.drainAll());
          try (FileWriter write = new FileWriter(fileName)) {
            write.write(textArea.getText());
            return true;
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
    return false;
  }

  /**
   * Sets the size of the console.
   *
   * @param width The width, expressed in pixels.
   * @param height The height, expressed in pixels.
   */
  public void setSize(int width, int height) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> setSize(width, height));
      return;
    }
    if (frame != null) {
      frame.setSize(width, height);
    }
  }

  /**
   * Stops display updates, restores standard streams and releases the console resources.
   *
   * @param evt WindowEvent
   * @hidden
   */
  @Override
  public void windowClosed(WindowEvent evt) {
    disposeConsole();
  }

  /** Releases stream ownership before another console can be opened on the event thread. */
  private void disposeConsole() {
    if (disposed) {
      return;
    }
    disposed = true;
    displayTimer.stop();
    restoreStandardStreams();
    output.close();
    closeRedirectedStreams();
    releaseReferences();
  }

  /**
   * Closes the console.
   *
   * @param evt WindowEvent.
   * @hidden
   */
  @Override
  public void windowClosing(WindowEvent evt) {
    closeConsole();
  }

  /** Closes the console frame. */
  private void closeConsole() {
    if (frame != null) {
      JFrame closingFrame = frame;
      disposeConsole();
      closingFrame.dispose();
    }
  }
}
