@echo off
rem Compile the sample plugin and generate the jar file

rem Add the Nodus main jar and libs to the classpath
set CLASSPATH=../nodus8.jar;../lib/*

rem Compile the source code of the plugin
javac -source 1.8 -target 1.8 NodusSamplePlugin.java 

rem Create the JAR file
jar cf NodusSamplePlugin.jar NodusSamplePlugin.class

rem Remove the compiled class file (not mandatory)
del NodusSamplePlugin.class
