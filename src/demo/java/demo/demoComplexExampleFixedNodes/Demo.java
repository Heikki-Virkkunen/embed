import java.util.*;

import fi.heolvi.embed.base.*;
public class Demo
{

  public static void main(String[] args)
  throws Exception
  {
    //The demo is described in a PDF file
    //demoComplexExampleFixedNodes.pfd
    //in this same directory.

    //Set content of databaase as described in FIG. 1A.
    //A, S and X1 are persistent root nodes in the database.
    String dbName = "friends.db";
    if (TestDB.existsDB(dbName))
      TestDB.deleteDB(dbName);
    TestDB db = new TestDB(dbName);
    Friend a = new Friend("A",0);
    Friend b = new Friend("B",0);
    Friend d = new Friend("D",0);
    Friend e = new Friend("E",0);
    Friend f = new Friend("F",0);
    Friend g = new Friend("G",0);
    Friend h = new Friend("H",0);
    Friend i = new Friend("I",0);
    Friend j = new Friend("J",0);
    Friend k = new Friend("K",0);
    Friend l = new Friend("L",0);
    Friend m = new Friend("M",0);
    Friend n = new Friend("N",0);
    Friend o = new Friend("O",0);
    Friend p = new Friend("P",0);
    Friend q = new Friend("Q",0);
    Friend r = new Friend("R",0);
    Friend s = new Friend("S",0);
    Friend t = new Friend("T",0);
    Friend u = new Friend("U",0);
    Friend v = new Friend("V",0);
    Friend w = new Friend("W",0);
    Friend x = new Friend("X",0);
    Friend y = new Friend("Y",0);
    Friend z = new Friend("Z",0);

    Friend x1 = new Friend("X1",0);
    Friend x2 = new Friend("X2",0);
    Friend x3 = new Friend("X3",0);
    Friend x4 = new Friend("X4",0);
    Friend x5 = new Friend("X5",0);
    Friend x6 = new Friend("X6",0);
    Friend x7 = new Friend("X7",0);
    Friend x8 = new Friend("X8",0);

    a.fr1 = b;
    b.fr1 = d;
    b.fr2 = e;
    d.fr1 = j;
    d.fr2 = n;
    e.fr1 = g;
    f.fr1 = g;
    g.fr1 = f;
    g.fr2 = h;
    h.fr1 = i;
    i.fr1 = h;
    i.fr2 = z;
    j.fr1 = k;
    k.fr1 = l;
    k.fr2 = r;
    l.fr1 = m;
    m.fr1 = n;
    n.fr1 = o;
    n.fr2 = u;
    o.fr1 = p;
    p.fr1 = q;
    q.fr1 = j;
    q.fr2 = o;
    r.fr1 = t;
    s.fr1 = t;
    u.fr1 = v;
    v.fr1 = w;
    w.fr1 = u;
    w.fr2 = x;
    w.fr3 = y;
    x.fr1 = x;
    y.fr1 = x7;
    z.fr1 = y;

    x1.fr1 = x2;
    x2.fr1 = x3;
    x3.fr1 = x4;
    x4.fr1 = x5;
    x5.fr1 = x6;
    x6.fr1 = z;
    x7.fr1 = x6;
    x7.fr2 = x8;

    db.embed(a);
    db.embed(s);
    db.embed(x1);

    db.close();

    //Load structure A from database.
    //Set B referring to C (a new node), not to D anymore.
    //Set E referring to H, not to G anymore.
    //Set age of I as 25.
    //Set age of X8 as 27.
    //Call db.embed(a)
    db = new TestDB(dbName);
    ArrayList<Object>  objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");

    //Form references in object structure A, in the run-time memory.
    a = (Friend) objects.get(0);
    b = a.fr1;
    e = b.fr2;
    h = e.fr1.fr2;
    i = h.fr1;
    x8 = i.fr2.fr1.fr1.fr2;

    //Modify object structure A, in the run-time memory.
    Friend c = new Friend("C",0);
    b.fr1 = c;
    e.fr1 = h;
    i.age = 25;
    x8.age = 27;

    System.out.println();
    System.out.println("Nodes in the run-time memory before calling db.embed(a):");
    System.out.println("--------------------------------------------------------");
    System.out.println(b);
    System.out.println(c);
    System.out.println(e);
    System.out.println(i);
    System.out.println(x8);

    db.embed(a);

    db.close();


    //Print content of the database:

    db = new TestDB(dbName);

    objects  = db.searchFixedNodesFromDB(Friend.class,"name","A");
    a = (Friend) objects.get(0);
    b = a.fr1;
    c = b.fr1;
    e = b.fr2;
    h = e.fr1;
    i = h.fr1;
    z = i.fr2;
    y = z.fr1;
    x7 = y.fr1;
    x8 = x7.fr2;
    x6 = x7.fr1;
    System.out.println();
    System.out.println("Nodes in the database after calling db.embed(a):");
    System.out.println("------------------------------------------------");
    System.out.println(a);
    System.out.println(b);
    System.out.println(c);
    System.out.println(e);
    System.out.println(h);
    System.out.println(i);
    System.out.println(z);
    System.out.println(y);
    System.out.println(x7);
    System.out.println(x8);
    System.out.println(x6);

    objects  = db.searchFixedNodesFromDB(Friend.class,"name","S");
    s = (Friend) objects.get(0);
    t = s.fr1;
    System.out.println();
    System.out.println(s);
    System.out.println(t);

    objects  = db.searchFixedNodesFromDB(Friend.class,"name","X1");
    x1 = (Friend) objects.get(0);
    x2 = x1.fr1;
    x3 = x2.fr1;
    x4 = x3.fr1;
    x5 = x4.fr1;
    System.out.println();
    System.out.println(x1);
    System.out.println(x2);
    System.out.println(x3);
    System.out.println(x4);
    System.out.println(x5);

    db.close();
  }
}
