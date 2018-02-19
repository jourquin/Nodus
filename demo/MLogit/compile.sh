# Compile the sample plugin and generate the jar file

# Add the Nodus main jar and libs to the classpath
export CLASSPATH=../../nodus7.jar:../../lib/*

# Compile the source code of the plugin
javac -source 1.7 -target 1.7 MLogit.java 

# Create the JAR file
jar cf MLogit.jar MLogit.class

# Remove the compiled class file (not mandatory)
rm MLogit.class
