/*
 * Copyright (c) 1991-2021 Université catholique de Louvain
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

package edu.uclouvain.core.nodus.tools.notepad;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.utils.NodusFileFilter;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * A simple note pad used to edit cost functions.
 *
 * @author Bart Jourquin
 */
public class NotePad extends JFrame {

  /** Handles ReDo actions. */
  private class RedoAction extends AbstractAction {
    static final long serialVersionUID = 6090244294501337377L;

    public RedoAction() {
      super(i18n.get(NotePad.class, "Redo", "Redo"), new ImageIcon("images/redo.png"));
      setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try {
        undoManager.redo();
      } catch (CannotRedoException ex) {
        System.err.println("Unable to redo: " + ex);
        ex.printStackTrace();
      }

      this.update();
      undoAction.update();
    }

    protected void update() {
      if (undoManager.canRedo()) {
        setEnabled(true);
        putValue("Redo", undoManager.getRedoPresentationName());
      } else {
        setEnabled(false);
        putValue(Action.NAME, i18n.get(NotePad.class, "Redo", "Redo"));
      }
    }
  }

  /** Handles Undo actions. */
  private class UndoAction extends AbstractAction {
    static final long serialVersionUID = -4552554923226162726L;

    public UndoAction() {
      super(i18n.get(NotePad.class, "Undo", "Undo"), new ImageIcon("images/undo.png"));
      setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try {
        undoManager.undo();
      } catch (CannotUndoException ex) {
        System.err.println("Unable to undo: " + ex);
        ex.printStackTrace();
      }

      this.update();
      redoAction.update();
    }

    protected void update() {
      if (undoManager.canUndo()) {
        setEnabled(true);
        putValue("Undo", undoManager.getUndoPresentationName());
      } else {
        setEnabled(false);
        putValue(Action.NAME, "Undo");
      }
    }
  }

  private static I18n i18n = Environment.getI18n();

  private static final long serialVersionUID = -3477494826088965908L;

  private static String thisComponentName = "Notepad";

  /** Convenient utility class with all the possible actions for the NotePad. */
  public NotePadActions actions = new NotePadActions(this);

  /** . */
  private JButton copyButton;

  /** . */
  private JMenuItem copyMenuItem;

  /** . */
  private JButton cutButton;

  /** . */
  private JMenuItem cutMenuItem;

  /** . */
  private boolean displayFullPath;

  /** . */
  private JMenu edit;

  /** . */
  private JMenuItem exitMenuItem;

  /** . */
  private JMenu file;

  /** . */
  private String fileName = "";

  /** . */
  private String filePath = "";

  /** . */
  private JButton findButton;

  /** . */
  private JMenuItem findMenuItem;

  /** . */
  private JMenuItem findNexT;

  /** . */
  private boolean isNewInstance = false;

  /** . */
  private JMenuBar menuBar;

  /** . */
  private JButton newButton;

  /** . */
  private JMenuItem newMenuItem;

  /** . */
  NodusFileFilter nodusFileFilter = new NodusFileFilter();

  /** . */
  private JButton openBuuton;

  /** . */
  private JMenuItem openMenuItem;

  /** . */
  private JButton pasteButton;

  /** . */
  private JMenuItem pasteMenuItem;

  /** . */
  private JButton printButton;

  /** . */
  private JMenuItem printMenuItem;

  /** . */
  private RedoAction redoAction = new RedoAction();

  /** . */
  private JButton redoButton;

  /** . */
  private JButton saveAsButton;

  /** . */
  private JMenuItem saveAsMenuItem;

  /** . */
  private JButton saveButton;

  /** . */
  private JMenuItem saveMenuItem;

  /** . */
  private JMenuItem selectAllMenuItem;

  /** . */
  private RSyntaxTextArea textPane;

  /** . */
  private JToolBar toolBar;

  /** . */
  private UndoAction undoAction = new UndoAction();

  /** . */
  private JButton undoButton;

  /** . */
  private UndoManager undoManager = new UndoManager();

  /**
   * Creates a new NotePad.
   *
   * @param nodusMapPanel The NodusMapPanel.
   * @param path The path to the default directory.
   * @param fileName The name of the file to open.
   */
  public NotePad(NodusMapPanel nodusMapPanel, String path, String fileName) {
    this(nodusMapPanel, path, fileName, thisComponentName);
  }

  /**
   * Creates a new NotePad with a given component name. The latest is used to avoid the creation of
   * more than one instance of a NotePad.
   *
   * @param nodusMapPanel The NodusMapPanel.
   * @param path The path to the default directory.
   * @param fileName The name of the file to open.
   * @param componentName The name given to this NotePad component.
   */
  //@SuppressWarnings("deprecation")
  public NotePad(NodusMapPanel nodusMapPanel, String path, String fileName, String componentName) {

    // Only create an instance if none exists
    Frame[] frames = Frame.getFrames();

    for (Frame element : frames) {
      if (element instanceof JFrame) {
        JFrame f = (JFrame) element;
        if (f.getName().equals(componentName) && f.isVisible()) {
          f.requestFocus();
          return;
        }
      }
    }

    isNewInstance = true;

    setTitle(i18n.get(NotePad.class, "Nodus_editor", "Nodus editor"));
    setName(componentName);

    if (nodusMapPanel != null) {
      displayFullPath = nodusMapPanel.getDisplayFullPath();
    }

    // if (!JFrame.isDefaultLookAndFeelDecorated()) {
    setIconImage(
        Toolkit.getDefaultToolkit().createImage(NodusMapPanel.class.getResource("nodus.png")));
    // }

    this.setSize(800, 600);

    nodusFileFilter.setExtension(NodusC.TYPE_COSTS);
    nodusFileFilter.setDescription(
        i18n.get(NotePad.class, "Nodus_costs_functions", "Nodus costs functions"));

    Toolkit.getDefaultToolkit().getScreenSize();
    textPane = new RSyntaxTextArea();
    textPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
    textPane.setHighlightCurrentLine(true);
    textPane.setTabSize(4);

    // Apply theme
    try {
      InputStream in = NodusMapPanel.class.getResource("eclipse.xml").openStream();
      Theme theme = Theme.load(in);
      theme.apply(textPane);
    } catch (IOException ioe) { // Should never happen
      ioe.printStackTrace();
    }

    Container cp = getContentPane();
    cp.add(textPane);
    cp.add("North", toolBar = new JToolBar(i18n.get(NotePad.class, "Tool_Bar", "Tool Bar")));
    cp.add(new RTextScrollPane(textPane));

    setJMenuBar(menuBar = new JMenuBar());

    menuBar.add(file = new JMenu(i18n.get(NotePad.class, "File", "File")));
    menuBar.add(edit = new JMenu(i18n.get(NotePad.class, "Edit", "Edit")));

    // menuBar.add(format = new JMenu("Format"));
    file.add(
        newMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "New", "New"),
                new ImageIcon(getClass().getResource("images/new.png"))));
    file.add(
        openMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Open", "Open"),
                new ImageIcon(getClass().getResource("images/open.png"))));
    file.add(
        saveMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Save", "Save"),
                new ImageIcon(getClass().getResource("images/save.png"))));

    file.add(
        saveAsMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Save_As", "Save As"),
                new ImageIcon(getClass().getResource("images/saveAs.png"))));

    file.add(
        printMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Print", "Print"),
                new ImageIcon(getClass().getResource("images/print.png"))));
    file.add(exitMenuItem = new JMenuItem(i18n.get(NotePad.class, "Exit", "Exit")));

    file.insertSeparator(4);
    file.insertSeparator(6);

    edit.add(undoAction);
    edit.add(redoAction);
    edit.add(
        cutMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Cut", "Cut"),
                new ImageIcon(getClass().getResource("images/cut.png"))));
    edit.add(
        copyMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Copy", "Copy"),
                new ImageIcon(getClass().getResource("images/copy.png"))));
    edit.add(
        pasteMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Paste", "Paste"),
                new ImageIcon(getClass().getResource("images/paste.png"))));
    edit.add(
        findMenuItem =
            new JMenuItem(
                i18n.get(NotePad.class, "Find", "Find"),
                new ImageIcon(getClass().getResource("images/find.png"))));
    edit.add(findNexT = new JMenuItem(i18n.get(NotePad.class, "Find_Next", "Find Next")));
    edit.add(
        selectAllMenuItem = new JMenuItem(i18n.get(NotePad.class, "Select_All", "Select All")));

    edit.insertSeparator(2);
    edit.insertSeparator(6);
    edit.insertSeparator(9);

    file.setMnemonic('f');
    edit.setMnemonic('e');

    newMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    openMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    saveMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    printMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_P, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    exitMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    cutMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_X, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    copyMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    pasteMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    findMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    findNexT.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_F3, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    selectAllMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));

    toolBar.add(newButton = new JButton(new ImageIcon(getClass().getResource("images/new.png"))));
    toolBar.add(openBuuton = new JButton(new ImageIcon(getClass().getResource("images/open.png"))));
    toolBar.add(saveButton = new JButton(new ImageIcon(getClass().getResource("images/save.png"))));
    toolBar.add(
        saveAsButton = new JButton(new ImageIcon(getClass().getResource("images/saveAs.png"))));
    toolBar.add(
        printButton = new JButton(new ImageIcon(getClass().getResource("images/print.png"))));
    toolBar.addSeparator();
    undoButton = toolBar.add(undoAction);
    undoButton.setIcon(new ImageIcon(getClass().getResource("images/undo.png")));
    redoButton = toolBar.add(redoAction);
    redoButton.setIcon(new ImageIcon(getClass().getResource("images/redo.png")));
    toolBar.addSeparator();
    toolBar.add(cutButton = new JButton(new ImageIcon(getClass().getResource("images/cut.png"))));
    toolBar.add(copyButton = new JButton(new ImageIcon(getClass().getResource("images/copy.png"))));
    toolBar.add(
        pasteButton = new JButton(new ImageIcon(getClass().getResource("images/paste.png"))));
    toolBar.add(findButton = new JButton(new ImageIcon(getClass().getResource("images/find.png"))));

    newButton.setToolTipText(i18n.get(NotePad.class, "New", "New"));
    openBuuton.setToolTipText(i18n.get(NotePad.class, "Open", "Open"));
    saveButton.setToolTipText(i18n.get(NotePad.class, "Save", "Save"));
    saveAsButton.setToolTipText(i18n.get(NotePad.class, "Save_As", "Save As"));
    printButton.setToolTipText(i18n.get(NotePad.class, "Print", "Print"));
    cutButton.setToolTipText(i18n.get(NotePad.class, "Cut", "Cut"));
    copyButton.setToolTipText(i18n.get(NotePad.class, "Copy", "Copy"));
    pasteButton.setToolTipText(i18n.get(NotePad.class, "Paste", "Paste"));
    findButton.setToolTipText(i18n.get(NotePad.class, "Find", "Find"));

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            actions.exit();
          }
        });

    newMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.newText();
          }
        });

    openMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.open();
          }
        });

    saveMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.save();
          }
        });

    saveAsMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.saveFileAs();
          }
        });

    printMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.print();
          }
        });

    exitMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.exit();
          }
        });

    cutMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.cut();
          }
        });

    copyMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.copy();
          }
        });

    pasteMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.paste();
          }
        });

    selectAllMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.selectAll();
          }
        });

    findMenuItem.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.find();
          }
        });

    findNexT.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.findNext();
          }
        });

    newButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.newText();
          }
        });

    openBuuton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.open();
          }
        });

    saveButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.save();
          }
        });

    saveAsButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.saveFileAs();
          }
        });

    printButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.print();
          }
        });

    cutButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.cut();
          }
        });

    copyButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.copy();
          }
        });

    pasteButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.paste();
          }
        });

    findButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent ae) {
            actions.find();
          }
        });

    textPane
        .getDocument()
        .addUndoableEditListener(
            new UndoableEditListener() {
              @Override
              public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
                undoAction.update();
                redoAction.update();
              }
            });

    // Load file
    actions.setPath(path);

    if (!fileName.equals("")) {
      actions.loadFile(fileName);
    }

    setLocationRelativeTo(nodusMapPanel);

    setVisible(true);
  }

  /**
   * Returns the file filter used for this NotePad.
   *
   * @return The NodusFileFilter used for this NotePad instance.
   */
  public NodusFileFilter getFileFilter() {
    return nodusFileFilter;
  }

  /**
   * Returns the name of the current loaded file, with or without a prepending path.
   *
   * @param withPath If true, the file name will be prepended with its path.
   * @return The file name.
   */
  public String getFileName(boolean withPath) {
    if (withPath) {
      return filePath + fileName;
    } else {
      return fileName;
    }
  }

  /**
   * Gets the menu bar.
   *
   * @return The menu bar.
   */
  public JMenuBar getMenubar() {
    return menuBar;
  }

  /**
   * Gets the text panel, which is a RSyntaxTextArea.
   *
   * @return The RSyntaxTextArea uses as text panel.
   */
  public RSyntaxTextArea getTextPane() {
    return textPane;
  }

  /**
   * Get the tool bar of the NotePad.
   *
   * @return The tool bar.
   */
  public JToolBar getToolBar() {
    return toolBar;
  }

  /**
   * Returns true of the full path of a file must be displayed in the title bar.
   *
   * @return True if the full path must be displayed.
   */
  public boolean isDisplayFullPath() {
    return displayFullPath;
  }

  /**
   * Returns true if this instance of the NotePad is a new one. Can be used by derivative classes in
   * order to avoid multiple instances of a NotePad.
   *
   * @return True if this is a new instance.
   */
  public boolean isNewInstance() {
    return isNewInstance;
  }

  /**
   * Sets the file name and path.
   *
   * @param filePath The path to the file.
   * @param fileName The file name.
   */
  public void setFileName(String filePath, String fileName) {
    this.fileName = fileName;
    this.filePath = filePath;
  }
}
