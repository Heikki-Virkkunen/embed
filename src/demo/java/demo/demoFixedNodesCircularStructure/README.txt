Running the demo application
----------------------------

1)
Check that you have Java installed on your computer.


2)
Check that you have SQLite installed on your computer.
See for example
http://www.tutorialspoint.com/sqlite/sqlite_installation.htm


3)
Download (the latest version of) sqlite-jdbc-<version>.jar from sqlite-jdbc repository.
https://github.com/xerial/sqlite-jdbc


4)
Replace in the file run.bat the line (in Windows)
java  -classpath .;..\..\..\..\main\java;..\..\..\..\..\sqlitedriver\sqlite-jdbc-3.8.11.2.jar  Demo
with the line
java  -classpath .;..\..\..\..\main\java;..\..\..\..\..\sqlitedriver\sqlite-jdbc-<version>.jar  Demo
(-In Linux use appropriate separators ":" and "/")


5)
Run the program by entering
run.bat


6)
Check the results in the created database people.db
(-The results are also listed in comments, in a source file Demo.java.)

Start the command line prompt:
sqlite3 friends.db

Some useful SQLite commands to check the results:
-See https://www.sqlite.org/cli.html

.echo on
.mode column
.headers on
.nullvalue Null
.schema
select * from nodeInstances;
select * from Friend;
.quit

