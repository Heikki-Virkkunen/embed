cls
del ..\..\..\..\*.class /s >NUL
javac -classpath .;..\..\..\..\main\java  Demo.java
java  -classpath .;..\..\..\..\main\java;..\..\..\..\..\sqlitedriver\sqlite-jdbc-3.8.11.2.jar  Demo
del ..\..\..\..\*.class /s >NUL


