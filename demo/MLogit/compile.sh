# Compile the sample plugin and generate the jar file

# Add the Nodus main jar and libs to the classpath
export CLASSPATH=../../nodus8.jar:../../lib/*:../../lib/groovy/*:../../lib/groovy/extras-jaxb/*

# Compile the source code of the plugin
javac --release 11 MLogit.java

# Create the JAR file
jar cf MLogit.jar MLogit.class

# Remove the compiled class file (not mandatory)
rm MLogit.class
