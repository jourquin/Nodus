#! /bin/sh
NODUS7_HOME=%INSTALL_PATH

LIBDIR=${NODUS7_HOME}/lib/*
PLAFSDIR=${NODUS7_HOME}/plafs/*
JDBCDIR=${NODUS7_HOME}/jdbcDrivers/*

# Set default values for the JVM heap sizes
java -cp ${NODUS7_HOME}/nodus7.jar -DNODUS_HOME=$NODUS7_HOME edu.uclouvain.core.nodus.utils.SetDefaultHeapSizes
source ${NODUS7_HOME}/jvmargs.sh

NODUSCP="${NODUS7_HOME}/nodus7.jar:$LIBDIR:$PLAFSDIR:$JDBCDIR:$NODUS7_HOME"

java -cp $NODUSCP $JVMARGS -DNODUS_HOME=$NODUS7_HOME edu.uclouvain.core.nodus.Nodus7 "$@"
