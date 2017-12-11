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

NODUS7_HOME=$(dirname "$SCRIPT")

# Set some dirs
LIBDIR="$NODUS7_HOME"/lib/*
PLAFSDIR="$NODUS7_HOME"/plafs/*
JDBCDIR="$NODUS7_HOME"/jdbcDrivers/*
NODUSJAR="$NODUS7_HOME"/nodus7.jar

# Set default values for the JVM heap sizes if not yet set
java -cp "$NODUS7_HOME"/nodus7.jar -DNODUS_HOME="$NODUS7_HOME" edu.uclouvain.core.nodus.utils.SetDefaultHeapSizes
source "$NODUS7_HOME"/jvmargs.sh

# Launch Nodus
NODUSCP="$NODUSJAR:$LIBDIR:$PLAFSDIR:$JDBCDIR:"$NODUS7_HOME""
java -cp "$NODUSCP" $JVMARGS -DNODUS_HOME="$NODUS7_HOME" edu.uclouvain.core.nodus.Nodus7 "$@"

