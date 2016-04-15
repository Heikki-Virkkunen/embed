import java.util.*;

import fi.heolvi.embed.base.*;

/*
This example shows how the gray node, for which
embed is called, can disappear from the database
in a special case.
*/
public class Demo
{

  public static void main(String[] args)
  throws Exception
  {
    //Store network A -> B -> C -> D -> B
    String dbName = "friends.db";
    if (TestDB.existsDB(dbName))
      TestDB.deleteDB(dbName);
    TestDB db = new TestDB(dbName);
    Friend a = new Friend("A",22);
    Friend b = new Friend("B",23);
    Friend c = new Friend("C",24);
    Friend d = new Friend("D",25);
    a.fr1 = b;
    b.fr1 = c;
    c.fr1 = d;
    d.fr1 = b;
    db.embed(a);
    System.out.println(a);
    System.out.println(b);
    System.out.println(c);
    System.out.println(d);
    db.close();

    //Load structure A from db.
    //Set age of D as 30,
    //Set B referring to D, not to C anymore.
    //Call db.embed(c)
    //C disappears from the database even though embed was called for c!
    //It is ok if you think in the network level.
    //d.age gets the value 30 in db.
    db = new TestDB(dbName);
    ArrayList<Object>  objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");
    a = (Friend) objects.get(0);
    b = a.fr1;
    c = b.fr1;
    d = c.fr1;
    System.out.println();
    System.out.println(a);
    System.out.println(b);
    System.out.println(c);
    System.out.println(d);
    d.age = 30;
    b.fr1 = d;
    db.embed(c);
    db.close();

/*
Results in SQLite tables:

select * from nodeInstances;
id  orc  irc  className
--  ---  ---  ----------
1   1    0    Friend
2   0    2    Friend
4   0    1    Friend

select * from Friend;
id  instanceId  name  age  book  fr1  fr2  list
--  ----------  ----  ---  ----  ---  ---  ----
1   1           A     22   0     2    0    0
2   2           B     23   0     4    0    0
4   4           D     30   0     2    0    0
*/
  }
}
