/**
 * Copyright (c) 1991-2018 Université catholique de Louvain
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

import static javax.swing.JOptionPane.ERROR_MESSAGE;

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.utils.NodusFileFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Convenient class with all the possible actions in the NotePad. Makes the code of the latest
 * cleaner.
 *
 * @author Bart Jourquin
 */
public class NotePadActions {
  private static I18n i18n = Environment.getI18n();

  private String fileContent = "";

  private String findWord;

  private String lastPath = ".";

  private NotePad notePad;

  private int option;

  private int returnVal;

  /**
   * Initializes the actions.
   *
   * @param notePad The NotePad these actions are related to.
   */
  public NotePadActions(NotePad notePad) {
    this.notePad = notePad;
  }

  /** Asks the user if he wants to save changes. */
  public void askForSave() {
    if (!notePad.getTextPane().getText().equals("")
        && !notePad.getTextPane().getText().equals(fileContent)) {

      option =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  NotePadActions.class,
                  "Do_you_want_to_save_the_changes",
                  "Do you want to save the changes ?"));

      if (option == 0) {
        save();
      }
    }
  }

  /** Copy selected text in the clipboard. */
  public void copy() {
    notePad.getTextPane().copy();
  }

  /** Cut selected text. */
  public void cut() {
    notePad.getTextPane().cut();
  }

  /** Quit the NotePad, asking for save if needed. */
  public void exit() {
    if (!notePad.getTextPane().getText().equals("")
        && !notePad.getTextPane().getText().equals(fileContent)) {
      option =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  NotePadActions.class,
                  "Do_you_want_to_save_the_changes",
                  "Do you want to save the changes ?"));

      if (option == 0) {
        save();
        notePad.setFileName("", "");
        notePad.setVisible(false);
      }

      if (option == 1) {
        notePad.setFileName("", "");
        notePad.setVisible(false);
      }

    } else {
      notePad.setVisible(false);
    }
  }

  /** Opens a "find" dialog and searches for the given input. */
  public void find() {
    try {
      findWord =
          JOptionPane.showInputDialog(
              null,
              i18n.get(NotePadActions.class, "Type_the_word_to_find", "Type the word to find"));

      while (notePad.getTextPane().getText().indexOf(findWord) == -1) {
        JOptionPane.showMessageDialog(
            null,
            i18n.get(NotePadActions.class, "Word_not_found", "Word not found!"),
            i18n.get(NotePadActions.class, "No match", "No match"),
            JOptionPane.WARNING_MESSAGE);

        findWord =
            JOptionPane.showInputDialog(
                null,
                i18n.get(NotePadActions.class, "Type_the_word_to_find", "Type the word to find"));
      }

      notePad
          .getTextPane()
          .select(
              notePad.getTextPane().getText().indexOf(findWord),
              notePad.getTextPane().getText().indexOf(findWord) + findWord.length());
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(NotePadActions.class, "Search_canceled", "Search canceled"),
          i18n.get(NotePadActions.class, "Aborted", "Aborted"),
          JOptionPane.WARNING_MESSAGE);
    }
  }

  /** Finds next occurrence of searched words. */
  public void findNext() {
    notePad
        .getTextPane()
        .select(
            notePad
                .getTextPane()
                .getText()
                .indexOf(findWord, notePad.getTextPane().getText().indexOf(findWord) + 1),
            notePad
                .getTextPane()
                .getText()
                .indexOf(findWord, notePad.getTextPane().getText().indexOf(findWord) + 1));
  }

  /**
   * Loads a file in the NotePad
   *
   * @param fileName The name of the file to load (in the current directory).
   */
  public void loadFile(String fileName) {
    try {
      notePad.getTextPane().setText("");
      notePad.setFileName(lastPath, fileName);

      Reader in = new FileReader(lastPath + fileName);
      notePad.getTextPane().setText("p1=100");

      BufferedReader br = new BufferedReader(in);
      String str;

      String txt = br.readLine() + '\n';

      while ((str = br.readLine()) != null) {
        txt += str + '\n';
      }

      notePad.getTextPane().setText(txt);

      in.close();
      notePad.getTextPane().setCaretPosition(0);
      fileContent = notePad.getTextPane().getText();
    } catch (FileNotFoundException x) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(NotePadActions.class, "File_not_found", "File not found:") + " " + fileName,
          NodusC.APPNAME,
          ERROR_MESSAGE);
    } catch (IOException ioe) {
      JOptionPane.showMessageDialog(
          null,
          i18n.get(NotePadActions.class, "I_OError_on_Open", "I/O Error on Open"),
          NodusC.APPNAME,
          ERROR_MESSAGE);
    }

    setTitle(true);
  }

  /** Clears the NotePad after having asked for saving the current content if needed. */
  public void newText() {
    if (!notePad.getTextPane().getText().equals("")
        && !notePad.getTextPane().getText().equals(fileContent)) {
      option =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  NotePadActions.class,
                  "Do_you_want_to_save_the_changes",
                  "Do you want to save the changes ?"));

      if (option == 0) {
        save();
        notePad.getTextPane().setText("");
        notePad.setFileName("", "");
      }

      if (option == 1) {
        notePad.getTextPane().setText("");
        notePad.setFileName("", "");
      }
    } else {
      notePad.getTextPane().setText("");
      notePad.setFileName("", "");
    }

    setTitle(false);
  }

  /** Opens a file after having asked for saving the current content if needed. */
  public void open() {
    if (!notePad.getTextPane().getText().equals("")
        && !notePad.getTextPane().getText().equals(fileContent)) {
      option =
          JOptionPane.showConfirmDialog(
              null,
              i18n.get(
                  NotePadActions.class,
                  "Do_you_want_to_save_the_changes",
                  "Do you want to save the changes ?"));

      if (option == 0) {
        save();
        openFile();
      }

      if (option == 1) {
        openFile();
      }
    } else {
      openFile();
    }
  }

  /** Opens a file chooser and load a file. */
  private void openFile() {
    JFileChooser fileChooser = new JFileChooser(lastPath);
    fileChooser.setFileFilter(notePad.getFileFilter());
    returnVal = fileChooser.showOpenDialog(notePad);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      String s = fileChooser.getSelectedFile().getPath();
      String name = fileChooser.getSelectedFile().getName();
      int idx = s.indexOf(name);
      lastPath = s.substring(0, idx);

      notePad.setFileName(lastPath, name);
      loadFile(name);
    }
  }

  /** Paste the content of the clipboard. */
  public void paste() {
    notePad.getTextPane().paste();
  }

  /** Prints the content of the NotePad. */
  public void print() {
    Print.printNotePadContent(notePad.getTextPane());
  }

  /** Saves the content of the NotePad. */
  public void save() {
    if (notePad.getFileName(false).equals("")) {
      saveFileAs();
    } else {
      saveFile();
    }
  }

  /** Saves the file using its current name. */
  private void saveFile() {
    fileContent = notePad.getTextPane().getText();

    // If nothing to save...
    if (fileContent.equals("")) {
      return;
    }

    if (notePad.getFileName(false).equals("")) {
      saveFileAs();
    } else {
      try {
        PrintWriter fout = new PrintWriter(new FileWriter(notePad.getFileName(true)));

        fout.print(fileContent);
        fout.close();
      } catch (IOException ioe) {
        // System.err.println("I/O Error on Save");
        JOptionPane.showMessageDialog(
            null,
            i18n.get(NotePadActions.class, "I_OError_on_Save", "I/O Error on Save"),
            NodusC.APPNAME,
            ERROR_MESSAGE);
      }

      setTitle(true);
    }
  }

  /** Saves the content of the NotePad, asking for the name and location of the resulting file. */
  public void saveFileAs() {

    // If nothing to save...
    if (notePad.getTextPane().getText().equals("")) {
      return;
    }

    JFileChooser fileChooser = new JFileChooser(lastPath);
    fileChooser.setFileFilter(notePad.getFileFilter());
    returnVal = fileChooser.showSaveDialog(notePad);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      PrintWriter fout = null;

      try {
        String extension = "." + ((NodusFileFilter) fileChooser.getFileFilter()).getExtension();
        String fileName = fileChooser.getSelectedFile().getPath();
        String name = fileChooser.getSelectedFile().getName();

        if (!fileName.endsWith(extension)) {
          fileName += extension;
          name += extension;
        }
        int idx = fileName.indexOf(name);
        lastPath = fileName.substring(0, idx);

        // Ask to overwrite if needed
        File f = new File(fileName);
        int answer = JOptionPane.YES_OPTION;
        if (f.exists()) {
          answer =
              JOptionPane.showConfirmDialog(
                  notePad.getTextPane(),
                  i18n.get(NotePad.class, "Overwrite", "Overwrite?"),
                  i18n.get(NotePad.class, "File_exists", "File exists"),
                  JOptionPane.YES_NO_OPTION);
        }

        if (answer == JOptionPane.YES_OPTION) {
          fout = new PrintWriter(new FileWriter(fileName));
          fileContent = notePad.getTextPane().getText();
          fout.print(fileContent);
          fout.close();
        }

        notePad.setFileName(lastPath, name);

      } catch (IOException ioe) {
        JOptionPane.showMessageDialog(
            null,
            i18n.get(NotePadActions.class, "I_OError_on_Save", "I/O Error on Save"),
            NodusC.APPNAME,
            ERROR_MESSAGE);
      }
    }

    setTitle(true);
  }

  /** Select the whole content of the NotePad. */
  public void selectAll() {
    notePad.getTextPane().selectAll();
  }

  /**
   * Sets the current directory.
   *
   * @param path Default directory (path) to use.
   */
  public void setPath(String path) {
    lastPath = path;
  }

  /**
   * Sets the title of the NotePad window, with or without the name of the loaded file.
   *
   * @param withFileName If true, the name of the file will be included in the window title.
   */
  private void setTitle(boolean withFileName) {
    if (withFileName) {
      String name = notePad.getFileName(false);
      if (notePad.isDisplayFullPath()) {
        name = notePad.getFileName(true);
      }
      notePad.setTitle(
          i18n.get(NotePadActions.class, "Nodus_editor", "Nodus editor") + " [" + name + "]");
    } else {
      notePad.setTitle(i18n.get(NotePadActions.class, "Nodus_editor", "Nodus editor"));
    }
  }
}
