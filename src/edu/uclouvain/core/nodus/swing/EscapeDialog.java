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

package edu.uclouvain.core.nodus.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JDialog;

/**
 * Dialog that intercepts the escape key to close.
 *
 * @author Bart Jourquin, inspired from from
 *     http://www.javaworld.com/javaworld/javatips/jw-javatip69.html
 */
public class EscapeDialog extends JDialog implements ContainerListener, KeyListener {

  private static final long serialVersionUID = 5773054683167299636L;

  /** Just calls the JDialog constructor and attach a key listener to handle the Esc key. */
  public EscapeDialog() {
    super((Frame) null, "", false);
    addKeyAndContainerListenerRecursively(this);
  }

  /**
   * Just calls the JDialog constructor and attach a key listener to handle the Esc key.
   *
   * @param frame Parent frame
   * @param title Title of the dialog
   * @param modal modal/amodal toggle
   */
  public EscapeDialog(Frame frame, String title, boolean modal) {
    super(frame, title, modal);
    addKeyAndContainerListenerRecursively(this);
  }

  /**
   * Just calls the JDialog constructor and attach a key listener to handle the Esc key.
   *
   * @param dialog Parent dialog
   * @param title Title of the dialog
   * @param modal modal/amodal toggle
   */
  public EscapeDialog(JDialog dialog, String title, boolean modal) {
    super(dialog, title, modal);
    addKeyAndContainerListenerRecursively(this);
  }

  /**
   * The following function is recursive and is intended for internal use only. It is private to
   * prevent anyone calling it from other classes The function takes a Component as an argument and
   * adds this Dialog as a KeyListener to it. Besides it checks if the component is actually a
   * Container and if it is, there are 2 additional things to be done to this Container : 1 - add
   * this Dialog as a ContainerListener to the Container 2 - call this function recursively for
   * every child of the Container.
   *
   * @param c Component to look for
   */
  private void addKeyAndContainerListenerRecursively(Component c) {
    // To be on the safe side, try to remove KeyListener first just in case
    // it has been added before.
    // If not, it won't do any harm
    c.removeKeyListener(this);

    // Add KeyListener to the Component passed as an argument
    c.addKeyListener(this);

    if (c instanceof Container) {
      // Component c is a Container. The following cast is safe.
      Container cont = (Container) c;

      // To be on the safe side, try to remove ContainerListener first
      // just in case it has been added before.
      // If not, it won't do any harm
      cont.removeContainerListener(this);

      // Add ContainerListener to the Container.
      cont.addContainerListener(this);

      // Get the Container array of children Components.
      Component[] children = cont.getComponents();

      for (Component element : children) {
        addKeyAndContainerListenerRecursively(element);
      }
    }
  }

  /**
   * This function is called whenever a Component or a Container is added to another Container
   * belonging to this Dialog.
   */
  @Override
  public void componentAdded(ContainerEvent e) {
    addKeyAndContainerListenerRecursively(e.getChild());
  }

  /**
   * This function is called whenever a Component or a Container is removed from another Container
   * belonging to this Dialog.
   */
  @Override
  public void componentRemoved(ContainerEvent e) {
    removeKeyAndContainerListenerRecursively(e.getChild());
  }

  /**
   * This function is called whenever a Component belonging to this Dialog (or the Dialog itself)
   * gets the KEY_PRESSED event.
   */
  @Override
  public void keyPressed(KeyEvent e) {
    int code = e.getKeyCode();

    if (code == KeyEvent.VK_ESCAPE) {
      // Key pressed is the ESCAPE key. Hide this Dialog.
      setVisible(false);
    } else if (code == KeyEvent.VK_ENTER) {
      // Key pressed is the ENTER key. Redefine performEnterAction() in
      // subclasses to respond to depressing the ENTER key.
      performEnterAction(e);
    }

    // Insert code to process other keys here
  }

  /** We need the following functions to complete implementation of KeyListener. */
  @Override
  public void keyReleased(KeyEvent e) {

  }

  /** We need the following functions to complete implementation of KeyListener. */
  @Override
  public void keyTyped(KeyEvent e) {

  }

  /**
   * Default response to ENTER key pressed goes here Redefine this function in subclasses to respond
   * to ENTER key differently.
   *
   * @param e Event to handle
   */
  void performEnterAction(KeyEvent e) {

  }

  /**
   * The following function is the same as the function above with the exception that it does
   * exactly the opposite - removes this Dialog from the listener lists of Components.
   *
   * @param c Component to look for
   */
  private void removeKeyAndContainerListenerRecursively(Component c) {
    c.removeKeyListener(this);

    if (c instanceof Container) {
      Container cont = (Container) c;

      cont.removeContainerListener(this);

      Component[] children = cont.getComponents();

      for (Component element : children) {
        removeKeyAndContainerListenerRecursively(element);
      }
    }
  }
}
