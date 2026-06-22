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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Simple console that intercepts the System.out and System.err.
 *
 * @author Bart Jourquin
 */
public class NodusConsole extends WindowAdapter
    implements WindowListener, ActionListener, Runnable {
  static I18n i18n = Environment.getI18n();

  private static JTextPane textArea;

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

  private PipedInputStream pin = new PipedInputStream();

  private PipedInputStream pin2 = new PipedInputStream();

  private boolean quit;

  private boolean disposed;

  private PrintStream previousOut;

  private PrintStream previousErr;

  private PrintStream redirectedOut;

  private PrintStream redirectedErr;

  private Thread reader;

  private Thread reader2;

  private Style style;

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

    this.defaultDirectory = defaultDirectory;

    // Only create a console if none exists
    if (isVisible()) {
      return;
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
    style = doc.addStyle("ConsoleStyle", null);
    StyleConstants.setFontFamily(style, "MonoSpaced");
    StyleConstants.setFontSize(style, 12);

    clearButton = new JButton(i18n.get(NodusConsole.class, "Clear", "Clear"));
    saveButton = new JButton(i18n.get(NodusConsole.class, "Save", "Save"));

    frame.getContentPane().setLayout(new BorderLayout(10, 10));
    JScrollPane sp = new JScrollPane(textArea);
    sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    frame.getContentPane().add(clearButton, BorderLayout.NORTH);
    frame.getContentPane().add(sp, BorderLayout.CENTER);
    frame.getContentPane().add(saveButton, BorderLayout.SOUTH);
    frame.setVisible(true);

    frame.addWindowListener(this);
    // clearButton.addActionListener(this);

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

    redirectStandardStreams();

    quit = false; // signals the Threads that they should exit

    // Starting two separate threads to read from the PipedInputStreams
    reader = new Thread(this);
    reader.setName("NodusConsole-stdout");
    reader.setDaemon(true);
    // reader.start();

    reader2 = new Thread(this);
    reader2.setName("NodusConsole-stderr");
    reader2.setDaemon(true);
    // reader2.start();

    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            if (!quit && reader != null && reader.getState() == Thread.State.NEW) {
              reader.start();
            }
            if (!quit && reader2 != null && reader2.getState() == Thread.State.NEW) {
              reader2.start();
            }
          }
        });
  }

  /** Redirects standard output and error streams to this console. */
  private void redirectStandardStreams() {
    previousOut = System.out;
    previousErr = System.err;

    try {
      PipedOutputStream pout = new PipedOutputStream(pin);
      redirectedOut = new PrintStream(pout, true);
      System.setOut(redirectedOut);
    } catch (java.io.IOException io) {
      showRedirectionError(
          "Couldn_t_redirect_STDOUT_to_this_console",
          "Couldn't redirect STDOUT to this console",
          io.getMessage());
    } catch (SecurityException se) {
      showRedirectionError(
          "Couldn_t_redirect_STDOUT_to_this_console",
          "Couldn't redirect STDOUT to this console",
          se.getMessage());
    }

    try {
      PipedOutputStream pout2 = new PipedOutputStream(pin2);
      redirectedErr = new PrintStream(pout2, true);
      System.setErr(redirectedErr);
    } catch (java.io.IOException io) {
      showRedirectionError(
          "Couldn_t_redirect_STDERR_to_this_console",
          "Couldn't redirect STDERR to this console",
          io.getMessage());
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
    style = null;
    clearButton = null;
    saveButton = null;
    clearActionListener = null;
    saveActionListener = null;
    defaultDirectory = null;
    frame = null;
    previousOut = null;
    previousErr = null;
    pin = null;
    pin2 = null;
    reader = null;
    reader2 = null;
  }

  /**
   * Action performed.
   *
   * @param evt ActionEvent
   * @hidden
   */
  @Override
  public synchronized void actionPerformed(ActionEvent evt) {}

  /** Clears the console. */
  public void clear() {
    if (textArea != null) {
      textArea.setText("");
    }
  }

  private synchronized String readLine(PipedInputStream in) throws IOException {
    String input = "";
    do {
      int available = in.available();
      if (available == 0) {
        break;
      }
      byte[] b = new byte[available];
      in.read(b);
      input += new String(b, 0, b.length);
    } while (!input.endsWith("\n") && !input.endsWith("\r\n") && !quit);
    return input;
  }

  /** Appends console output to the document on the Swing event thread. */
  private void appendToConsole(final String input, final Color color) {
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            try {
              StyleConstants.setForeground(style, color);
              doc.insertString(doc.getLength(), input, style);
              // Make sure the last line is always visible
              textArea.setCaretPosition(textArea.getDocument().getLength());
            } catch (Exception e) {
              if (textArea != null) {
                textArea.setText(
                    "\n"
                        + i18n.get(
                            NodusConsole.class,
                            "Console_reports_an_Internal_error",
                            "Console reports an Internal error.")
                        + "\n"
                        + MessageFormat.format(
                            i18n.get(NodusConsole.class, "The_error_is", "The error is: {0}"),
                            e.toString()));
              }
            }
          }
        });
  }

  /**
   * .
   *
   * @hidden
   */
  @Override
  public synchronized void run() {
    try {
      while (Thread.currentThread() == reader) {
        try {
          this.wait(100);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
        if (pin.available() != 0) {
          String input = readLine(pin);
          appendToConsole(input, Color.black);
        }
        if (quit) {
          return;
        }
      }

      while (Thread.currentThread() == reader2) {
        try {
          this.wait(100);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
        if (pin2.available() != 0) {

          String input = readLine(pin2);
          appendToConsole(input, Color.red);
        }
        if (quit) {
          return;
        }
      }
    } catch (Exception e) {
      if (textArea != null) {
        textArea.setText(
            "\n"
                + i18n.get(
                    NodusConsole.class,
                    "Console_reports_an_Internal_error",
                    "Console reports an Internal error.")
                + "\n"
                + MessageFormat.format(
                    i18n.get(NodusConsole.class, "The_error_is", "The error is: {0}"),
                    e.toString()));
      }
    }
  }

  /**
   * Saves the content of the consolde in a text file.
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

          try (FileWriter write = new FileWriter(fileName)) {
            write.write(textArea.getText().toCharArray());
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
    if (frame != null) {
      frame.setSize(width, height);
    }
  }

  /**
   * Closes the window and stops the "reader" threads.
   *
   * @param evt WindowEvent
   * @hidden
   */
  @Override
  public void windowClosed(WindowEvent evt) {
    Thread stdoutReader;
    Thread stderrReader;

    synchronized (this) {
      if (disposed) {
        return;
      }

      disposed = true;
      quit = true;
      notifyAll(); // stop all threads

      restoreStandardStreams();
      closeRedirectedStreams();

      stdoutReader = reader;
      stderrReader = reader2;

      closePipe(pin);
      closePipe(pin2);
    }

    joinReader(stdoutReader);
    joinReader(stderrReader);

    synchronized (this) {
      releaseReferences();
    }
  }

  /** Closes a pipe quietly during console shutdown. */
  private void closePipe(PipedInputStream pipe) {
    if (pipe == null) {
      return;
    }

    try {
      pipe.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Waits briefly for a reader thread to stop. */
  private void joinReader(Thread thread) {
    if (thread == null) {
      return;
    }

    try {
      thread.interrupt();
      thread.join(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Closes the console.
   *
   * @param evt WindowEvent.
   * @hidden
   */
  @Override
  public void windowClosing(WindowEvent evt) {
    if (frame != null) {
      frame.setVisible(false); // default behaviour of JFrame
      frame.dispose();
    }
  }
}
