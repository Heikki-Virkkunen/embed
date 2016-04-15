import java.util.*;

import fi.heolvi.embed.base.*;
public class Demo
{

  public static void main(String[] args)
  throws Exception
  {
    //Store network consisting of parts:
    //A -> B -> C -> D -> B
    //D -> F
    //C -> E
    //G -> E
    //A and G are persistent root nodes of the network.
    String dbName = "friends.db";
    if (TestDB.existsDB(dbName))
      TestDB.deleteDB(dbName);
    TestDB db = new TestDB(dbName);
    Friend a = new Friend("A",22);
    Friend b = new Friend("B",23);
    Friend c = new Friend("C",24);
    Friend d = new Friend("D",25);
    Friend e = new Friend("E",26);
    Friend f = new Friend("F",27);
    Friend g = new Friend("G",28);
    //Form A -> B -> C -> D -> B
    a.fr1 = b;
    b.fr1 = c;
    c.fr1 = d;
    d.fr1 = b;
    //Form D -> F
    d.fr2 = f;
    //Form C -> E
    c.fr2 = e;
    //Form G -> E
    g.fr1 = e;
    db.embed(a);
    db.embed(g);
    System.out.println(a);
    System.out.println(b);
    System.out.println(c);
    System.out.println(d);
    System.out.println(e);
    System.out.println(f);
    System.out.println(g);
    db.close();

    //Load structure A from db.
    //Set B referring to null, not to C anymore.
    //Set age of B as 30.
    //Call db.embed(b)
    //Result network in db consists of
    //A -> B
    //G -> E
    //b.age gets the value 30 in db.
    db = new TestDB(dbName);
    ArrayList<Object>  objects  = db.searchFixedNodesFromDB(Friend.class,"name","B");
    b = (Friend) objects.get(0);
    c = b.fr1;
    d = c.fr1;
    f = d.fr2;
    System.out.println();
    System.out.println(b);
    System.out.println(c);
    System.out.println(d);
    System.out.println(f);
    b.fr1 = null;
    b.age = 30;
    db.embed(b);
    db.close();

    db = new TestDB(dbName);
    objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");
    a = (Friend) objects.get(0);
    b = a.fr1;
    System.out.println();
    System.out.println(a);
    System.out.println(b);
    db.close();

    db = new TestDB(dbName);
    objects  = db.searchFixedNodesFromDB(Friend.class,"name","G");
    g = (Friend) objects.get(0);
    e = g.fr1;
    System.out.println();
    System.out.println(g);
    System.out.println(e);
    db.close();

/*
Results in SQLite tables:

select * from nodeInstances;
id  orc  irc  className
--  ---  ---  ----------
1   1    0    Friend
2   0    1    Friend
6   0    1    Friend
7   1    0    Friend

select * from Friend;
id  instanceId  name  age  book  fr1  fr2  list
--  ----------  ----  ---  ----  ---  ---  ----
1   1           A     22   0     2    0    0
2   2           B     30   0     0    0    0
6   6           E     26   0     0    0    0
7   7           G     28   0     6    0    0





select * from nodeInstances;
id  orc  irc  className
--  ---  ---  ----------
1   1    0    Friend
2   0    1    Friend
6   1    0    Friend
7   0    1    Friend

select * from Friend;
id  instanceId  name  age  book  fr1  fr2  list
--  ----------  ----  ---  ----  ---  ---  ----
1   1           A     22   0     2    0    0
2   2           B     30   0     0    0    0
6   6           G     28   0     7    0    0
7   7           E     26   0     0    0    0
*/
  }
}
