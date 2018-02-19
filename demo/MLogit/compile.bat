@echo off
rem Compile the sample plugin and generate the jar file

rem Add the Nodus main jar and libs to the classpath
set CLASSPATH=../../nodus7.jar;../../lib/*

rem Compile the source code of the plugin
javac -source 1.7 -target 1.7 MLogit.java 

rem Create the JAR file
jar cf MLogit.jar MLogit.class

rem Remove the compiled class file (not mandatory)
del MLogit.class
