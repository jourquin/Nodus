@echo off
rem Compile the sample plugin and generate the jar file

rem Add the Nodus main jar and libs to the classpath
set CLASSPATH=../nodus7.jar;../lib/*

rem Compile the source code of the plugin
javac NodusSamplePlugin.java 

rem Create the JAR file
jar cf NodusSamplePlugin.jar NodusSamplePlugin.class

rem Remove the compiled class file (not mandatory)
del NodusSamplePlugin.class
