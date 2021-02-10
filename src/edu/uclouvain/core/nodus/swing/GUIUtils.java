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

package edu.uclouvain.core.nodus.swing;

import edu.uclouvain.core.nodus.utils.JavaVersionUtil;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.JDialog;
import sun.misc.Unsafe;

/**
 * A few convenient static methods used in Swing related java code.
 *
 * @author Bart Jourquin
 */
public class GUIUtils {

  /**
   * Recompute a new position of a dialog in order to keep it entirely in the screen.
   *
   * @param dialog The JDialog to control.
   */
  public static void keepDialogInScreen(JDialog dialog) {
    // Be sure the dialog is completely visible
    boolean move = false;
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    int x = dialog.getX();
    int y = dialog.getY();

    if (x + dialog.getWidth() > d.width) {
      x = d.width - dialog.getWidth();
      move = true;
    }

    if (y + dialog.getHeight() > d.height) {
      y = d.height - dialog.getHeight();
      move = true;
    }

    if (x < 0) {
      x = 0;
      move = true;
    }

    if (y < 0) {
      y = 0;
      move = true;
    }

    if (move) {
      dialog.setLocation(x, y);
    }
  }

  /**
   * Set the dock image when running on Mac OSX. This method uses reflection as the comm.apple.*
   * classes are not available on Windows and Linux.
   *
   * @param icon The icon image to set in the dock.
   */
  public static void setMacOSDockImage(Image icon) {

    try {
      if (JavaVersionUtil.isJavaVersionAtLeast(9.0f)) {
        Class<?> c = Class.forName("java.awt.Taskbar", false, null);
        Method m = c.getMethod("getTaskbar");
        Object applicationInstance = m.invoke(null);
        m = applicationInstance.getClass().getMethod("setIconImage", java.awt.Image.class);
        m.invoke(applicationInstance, icon);
      } else {
        Class<?> c = Class.forName("com.apple.eawt.Application", false, null);
        Method m = c.getMethod("getApplication");
        Object applicationInstance = m.invoke(null);
        m = applicationInstance.getClass().getMethod("setDockIconImage", java.awt.Image.class);
        m.invoke(applicationInstance, icon);
      }
    } catch (ClassNotFoundException e1) {
      e1.printStackTrace();
    } catch (NoSuchMethodException e1) {
      e1.printStackTrace();
    } catch (SecurityException e1) {
      e1.printStackTrace();
    } catch (IllegalAccessException e1) {
      e1.printStackTrace();
    } catch (IllegalArgumentException e1) {
      e1.printStackTrace();
    } catch (InvocationTargetException e1) {
      e1.printStackTrace();
    }
  }

  /**
   * Suppress the illegal reflective access operation warnings thrown by JVM 9 and newer when
   * com.apple.awt classes are used. The Nodus code should be able to call the java.desktop classes
   * that now natively support the Apple desktop, but this is not supported by JVM 8.
   */
  public static void supressIllegalReflectiveAccessOperationWarnings() {
    if (JavaVersionUtil.isJavaVersionAtLeast(9.0f)
        && System.getProperty("os.name").toLowerCase().startsWith("mac")) {
      try {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe u = (Unsafe) theUnsafe.get(null);

        Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
        Field logger = cls.getDeclaredField("logger");
        u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
      } catch (Exception e) {
        // ignore
      }
    }
  }
}
