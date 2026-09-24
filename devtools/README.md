#Several tools used to facilitate some development tasks


##[google-java-format-eclipse-plugin-1.6.0.jar](https://github.com/google/google-java-format):

Google code style code formatter. Can be put in the "dropins" dir of the Eclipse IDE and selected 
in the Java code format preferences as formatter implementation.

##[JFlex.jar](http://jflex.de/):

Java lex generator. Used by "JFlex" ant task to generate Java lex file for Nodus specific 
SQL extensions syntax highlighting.
    
##NodusChecks.xml: 

Can be imported in Eclipse Checkstyle. Only two constraints are relaxed 
compared to the Google style: More that one successive uppercase characters are allowed in class
or method names without throwing a warning and uppercases in package names are marked as
info instead of warnings (to avoid warnings for classes developed in OpenMap packages).    

## IzPack dir](http://izpack.org/):

IzPack (v5) application installer libraries. Used by the "Installer" Ant task.

## [Pandoc](https://pandoc.org/):

Not provided, but used to generate HTML help pages from Markdown sources. The service lines workflow help page can
be regenerated with the ``ServicesWorkflowHtml`` Ant target.