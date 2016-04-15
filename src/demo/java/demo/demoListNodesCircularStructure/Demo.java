import java.util.*;

import fi.heolvi.embed.base.*;

public class Demo
{

  public static void main(String[] args)
  throws Exception
  {
    //Store network consisting of parts:
    //A -> B -> list1
    //list1(0) -> list3
    //list1(1) -> list2
    //list2(0) -> C
    //list2(1) -> list1
    //list2(0) = "cat"
    //D -> list3
    //list3(0) -> D
    //list3(1) = null
    //D -> list2
    //A and D are persistent root nodes of the network.
    String dbName = "friends.db";
    if (TestDB.existsDB(dbName))
      TestDB.deleteDB(dbName);
    TestDB db = new TestDB(dbName);
    Friend a = new Friend("A",22);
    Friend b = new Friend("B",23);
    Friend c = new Friend("C",24);
    Friend d = new Friend("D",25);
    ListNode list1 = new ListNode();
    ListNode list2 = new ListNode();
    ListNode list3 = new ListNode();
    a.fr1 = b;
    b.list = list1;
    list1.add(list3); //List refers to list.
    list1.add(list2); //List refers to list.
    list2.add(c);
    list2.add(list2); //List refers to itself (circular structure).
    list2.add("cat");
    c.list = list3;
    list3.add(d);
    list3.add(null); //Null reference in list.
    d.list = list3;
    db.embed(a);
    db.incrORC(d);
    System.out.println(a);
    System.out.println(b);
    System.out.println("list1="+list1);
    System.out.println("list2="+list2);
    System.out.println(c);
    System.out.println("list3="+list3);
    System.out.println(d);
    db.close();

    //Load structure A from db.
    //Remove item 1 (referring to list2) from list1.
    //Replace item 0 (referring to list3) with null value.
    //Add item "dog1" to list1.
    //Add item "dog2" to list1.
    //Call db.embed(b).
    db = new TestDB(dbName);
    ArrayList<Object>  objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");
    a = (Friend) objects.get(0);
    b = a.fr1;
    list1 = b.list;
    list2 = (ListNode) list1.get(1);
    c = (Friend) list2.get(0);
    list3 = c.list;
    System.out.println();
    System.out.println(a);
    System.out.println(b);
    System.out.println("list1="+list1);
    System.out.println("list2="+list2);
    System.out.println(c);
    System.out.println("list3="+list3);
    list1.remove(1); //Remove item referring to list2.
    list1.set(0,null); //Replace reference to list3 as null.
    list1.add("dog1");
    list1.add("dog2");
    db.embed(b);
    db.close();

    //Load structure A from db.
    //Load structure D from db.
    //Show results.
    db = new TestDB(dbName);
    objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");
    a = (Friend) objects.get(0);
    b = a.fr1;
    list1 = b.list;
    objects  = db.searchFixedNodesFromDB(Friend.class,"name","D");
    d = (Friend) objects.get(0);
    list3 = d.list;
    System.out.println();
    System.out.println(a);
    System.out.println(b);
    System.out.println("list1="+list1);
    System.out.println(d);
    System.out.println("list3="+list3);
    db.close();

/*
Results in SQLite tables:

select * from nodeInstances;
id  orc  irc   className
--  ---  ----  --------------------
1   1    0     Friend
2   0    1     Friend
3   0    1     fi.heolvi.embed.base.ListNode
4   0    1     fi.heolvi.embed.base.ListNode
5   1    1     Friend

select * from Friend;
id  instanceId  name  age  book fr1  fr2  list
--  ----------  ----  ---  ---- ---  ---  ----
1   1           A     22   0    2    0    0
2   2           B     23   0    0    0    3
3   5           D     25   0    0    0    4

select * from lists;
id  instanceId  len
--  ----------  ---
1   3           3
2   4           2

select * from listItems;
id  parent  position  type  item
--  ------  --------  ----  ----
3   2       0         4     5
4   2       1         0     Null
8   1       0         0     Null
9   1       1         3     dog1
10  1       2         3     dog2
*/
  }
}
