# Several tools used to facilitate some development tasks
 
## [ExcludeDoclet.jar](http://www.oracle.com/technetwork/articles/javase/index-jsp-136712.html): 

Implements a "@exclude" tag to ignore API doc generation for some methods. Used by "ApiDoc" Ant task.

## [google-java-format-eclipse-plugin-1.3.0.jar](https://github.com/google/google-java-format):

Google code style code formatter. Can be put in the "dropins" dir of the Eclipse IDE and selected 
in the Java code format preferences as formatter implementation.

## [i18nedit.jar](https://sourceforge.net/projects/i18nedit/):

I18N properties editor. Used by "I18Nedit" ant task. Facilitates translation of the software.
To use it, open the "i18nedit.properties" located at the root of the "src" dir.

## qjcc.jar:

Library used by i18nedit
   
## jhall.jar:

Library used by i18nedit

## [JFlex.jar](http://jflex.de/):

Java lex generator. Used by "JFlex" ant task to generate Java lex file for Nodus specific 
SQL extensions syntax highlighting.
    
## NodusGoogleCheckstyle.xml: 

Can be imported in Eclipse Checkstyle. Only two constraints are relaxed 
compared to the Google style: More that one successive uppercase characters are allowed in class
or method names without throwing a warning and uppercases in package names are marked as
info instead of warnings (to avoid warnings for classes developed in OpenMap packages).    

## [IzPack dir](http://izpack.org/):

Some IzPack (v5) application installer libraries. Used by "Installer" ant task.

## [Bat_To_Exe_Converter.exe](http://www.f2ko.de/en/b2e.php):

A freeware .bat to .exe "compiler". Used at install time to generate the "Nodus7.exe" file.

