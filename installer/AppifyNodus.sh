#!/bin/bash
#-------------------------------------------------------------------------------
# Copyright (c) 1991-2018 Université catholique de Louvain, 
# Center for Operations Research and Econometrics (CORE)
# http://www.uclouvain.be
# 
# This file is part of Nodus.
# 
# Nodus is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#-------------------------------------------------------------------------------

# This script is based on "appify". It is copied in the install dir (parsed by IzPack)
# and executed after installation. It is then deleted by the installer.
# At the end, Nodus.app is created, with an icon.  

NODUS7_HOME="$INSTALL_PATH"

SCRIPT="nodus7.sh"
APPNAME="Nodus7"
ICON="nodus7.icns"

cd "$NODUS7_HOME"

DIR="$APPNAME.app/Contents"
 
if [ -a "$APPNAME.app" ]; then
	rm -rf "$APPNAME.app"
fi
 
mkdir -p $DIR/{MacOS,Resources}

cd "$NODUS7_HOME/$DIR/Resources"
ln -s "../../../$ICON" $APPNAME.icns

cd "$NODUS7_HOME/$DIR/MacOS"
ln -s "../../../$SCRIPT" $APPNAME
chmod +x "$NODUS7_HOME/$SCRIPT"
 

cat <<EOF > "$NODUS7_HOME"/$DIR/Info.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleExecutable</key>
	<string>$APPNAME</string>
	<key>CFBundleGetInfoString</key>
	<string>$APPNAME</string>
	<key>CFBundleIconFile</key>
	<string>$APPNAME</string>
	<key>CFBundleName</key>
	<string>$APPNAME</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
</dict>
</plist>
EOF

