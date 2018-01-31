/*******************************************************************************
 * Copyright (c) 1991-2018 Université catholique de Louvain, 
 * Center for Operations Research and Econometrics (CORE)
 * http://www.uclouvain.be
 * 
 * This file is part of Nodus.
 * 
 * Nodus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/


/**
 * Just for fun: a few freeware Look And Feels are provided with Nodus
 * This is also an example of how to use a Beanshell script that doesn't use 
 * the Nodus API and doesn't use any variable passed by Nodus
 */

import javax.swing.UIManager;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import com.pagosoft.plaf.PlafOptions;
import com.easynth.lookandfeel.EaSynthLookAndFeel;

/*
 * https://pgslookandfeel.dev.java.net/
 */
com.pagosoft.plaf.PgsLookAndFeel pagoPlaf;
pagoPlaf = new com.pagosoft.plaf.PgsLookAndFeel();
PlafOptions.useBoldFonts(false);
PlafOptions.useBoldMenuFonts(false);
PlafOptions.setStyle(PlafOptions.MENUBARMENU, PlafOptions.GRADIENT_STYLE);
PlafOptions.setStyle(PlafOptions.MENUBAR, PlafOptions.GRADIENT_STYLE);
PlafOptions.setStyle(PlafOptions.MENU_ITEM, PlafOptions.GRADIENT_STYLE);
PlafOptions.useExtraMargin(true);
UIManager.installLookAndFeel(pagoPlaf.getName(), pagoPlaf.getClass().getName());


// Open the nodus properties file if it exists
Properties props = new Properties();
try {
    String home = System.getProperty("user.home") + "/";
    props.load(new FileInputStream(home + ".nodus7.properties"));
} catch (IOException ex) {
}

// Decorated frames?
boolean decorated = Boolean.parseBoolean(props.getProperty("decorated", "false"));


// Set Look & Feel (default to system l&f, but Nimbus for Linux)
String lookAndFeel = null;
lookAndFeel = props.getProperty("look&feel", null);
if (lookAndFeel == null) {
	if (!System.getProperty("os.name").toLowerCase().startsWith("linux")) {
		lookAndFeel = UIManager.getSystemLookAndFeelClassName();
	} else {
		lookAndFeel = new NimbusLookAndFeel().getClass().getName();
	}
	decorated=true;
}

JFrame.setDefaultLookAndFeelDecorated(decorated);
JDialog.setDefaultLookAndFeelDecorated(decorated);


try {
    UIManager.setLookAndFeel(lookAndFeel);
} catch (Exception ex) {
    System.out.println(ex.toString());
}



