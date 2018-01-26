# Compile the sample plugin and generate the jar file

# Add the Nodus main jar and libs to the classpath
export CLASSPATH=../BinaryDistribution/nodus7.jar:../lib/*

# Compile the source code of the plugin
javac NodusSamplePlugin.java 

# Create the JAR file
jar cf NodusSamplePlugin.jar NodusSamplePlugin.class

# Remove the compiled class file (not mandatory)
rm NodusSamplePlugin.class
