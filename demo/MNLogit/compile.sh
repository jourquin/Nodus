# Compile the sample plugin and generate the jar file

# Add the Nodus main jar and libs to the classpath
export CLASSPATH=../../nodus7.jar:../../lib/*

# Compile the source code of the plugin
javac -source 1.7 -target 1.7 MNLogit.java 

# Create the JAR file
jar cf MNLogit.jar MNLogit.class

# Remove the compiled class file (not mandatory)
rm MNLogit.class
