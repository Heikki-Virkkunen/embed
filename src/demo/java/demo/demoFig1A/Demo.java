import java.util.*;

import fi.heolvi.embed.base.*;

/*
This example is the same as in FIG 1A in the article, in section 3.
*/
public class Demo
{

  public static void main(String[] args)
  throws Exception
  {
    //Store network consisting of parts:
    //X1 -> X2
    //A -> B -> C -> D -> B
    //C -> E
    //X1 and A are persistent root nodes in db.
    String dbName = "people.db";
    if (TestDB.existsDB(dbName))
      TestDB.deleteDB(dbName);
    TestDB db = new TestDB(dbName);

    People x1 = new People("X1",34);
    People x2 = new People("X2",35);
    x1.fr1 = x2;
    db.embed(x1);

    People a = new People("A",30);
    People b = new People("B",31);
    People c = new People("C",32);
    People d = new People("D",33);
    People e = new People("E",23);
    a.fr1 = b;
    b.fr1 = c;
    c.fr1 = d;
    c.fr2 = e;
    d.fr1 = b;
    db.embed(a);
    db.close();

/*
Results in the database:

 select * from nodeInstances;
 id  orc  irc  className
 --  ---  ---  ---------
 1   1    0    People
 2   0    1    People
 3   1    0    People
 4   0    2    People
 5   0    1    People
 6   0    1    People
 7   0    1    People

 select * from People;
 id  instanceId  name  age  fr1  fr2
 --  ----------  ----  ---  ---  ---
 1   1           X1    34   2    0
 2   2           X2    35   0    0
 3   3           A     30   4    0
 4   4           B     31   5    0
 5   5           C     32   6    7
 6   6           D     33   4    0
 7   7           E     23   0    0
*/

    //Load structure X1 from db.
    db = new TestDB(dbName);
    ArrayList<Object>  objects  = db.searchFixedNodesFromDB(People.class,"name","X1");
    x1 = (People) objects.get(0);
    x2 = x1.fr1;
    System.out.println();
    System.out.println(x1);
    System.out.println(x2);
    db.close();


    //Load structure A from db.
    //Create F.
    //Set A referring to F.
    //Set age of E as 25.
    //Call db.embed(a)
    //
    //Result network in db consists of parts:
    //A -> F -> E
    //e.age gets the value 25 in db.
    db = new TestDB(dbName);
    objects  = db.searchFixedNodesFromDB(People.class,"name","A");
    a = (People) objects.get(0);
    b = a.fr1;
    c = b.fr1;
    e = c.fr2;
    d = b.fr1;
    System.out.println();
    System.out.println(a);
    System.out.println(b);
    System.out.println(c);
    System.out.println(d);
    System.out.println(e);
    People f = new People("F",36);
    a.fr1 = f;
    f.fr1 = e;
    e.age = 25;
    db.embed(a);
    db.close();

/*
Results in the database:

select * from nodeInstances;
id  orc  irc  className
--  ---  ---  ---------
1   1    0    People
2   0    1    People
3   1    0    People
7   0    1    People
8   0    1    People

select * from People;
id  instanceId  name  age  fr1  fr2
--  ----------  ----  ---  ---  ---
1   1           X1    34   2    0
2   2           X2    35   0    0
3   3           A     30   8    0
7   7           E     25   0    0
8   8           F     36   7    0
*/

    //Load structure A from db.
    db = new TestDB(dbName);
    objects  = db.searchFixedNodesFromDB(People.class,"name","A");
    a = (People) objects.get(0);
    f = a.fr1;
    e = f.fr1;
    System.out.println();
    System.out.println(a);
    System.out.println(f);
    System.out.println(e);
    db.close();
  }
}
