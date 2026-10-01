#!/bin/sh

# Get the installation directory. 
# Works on Linux and Mac OS (as "readlink -f" doesn't work on Mac)  
TARGET_FILE=$0

cd "$(dirname "$TARGET_FILE")"
TARGET_FILE=$(basename "$TARGET_FILE")

# Iterate down a (possible) chain of symlinks
while [ -L "$TARGET_FILE" ]
do
    TARGET_FILE=$(readlink "$TARGET_FILE")
    cd "$(dirname "$TARGET_FILE")"
    TARGET_FILE=$(basename "$TARGET_FILE")
done

# Compute the canonicalized name by finding the physical path 
# for the directory we're in and appending the target file.
PHYS_DIR=`pwd -P`
SCRIPT=$PHYS_DIR/$TARGET_FILE

NODUS9_HOME=$(dirname "$SCRIPT")

# Set some dirs
LIBDIR="$NODUS9_HOME"/lib/*:"$NODUS9_HOME"/lib/groovy/*
JDBCDIR="$NODUS9_HOME"/jdbcDrivers/*
NODUSJAR="$NODUS9_HOME"/nodus9.jar

# Set classpath
NODUSCP="$NODUSJAR:$LIBDIR:$JDBCDIR:$NODUS9_HOME"

# Set default values for the JVM heap sizes if not yet set
java -cp "$NODUSCP" -DNODUS_HOME="$NODUS9_HOME" edu.uclouvain.core.nodus.utils.SetJVMArgs
. "$NODUS9_HOME"/jvmargs.sh

# Launch Nodus
java -cp "$NODUSCP" $JVMARGS -DNODUS_HOME="$NODUS9_HOME" edu.uclouvain.core.nodus.Nodus "$@"
