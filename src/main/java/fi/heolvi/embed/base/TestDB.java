/*
 * Copyright (c) 2016 Heikki Virkkunen.
 * Date: 5 April 2016
*/

package fi.heolvi.embed.base;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.sql.*;

/*
This is the demo implementation of the embed method.

General information
-------------------
This implementation makes possible to test the embed method with
arbitrary complex update examples.

The implementation tries to be easy to read, rather than being
efficient as an implementation.

The SQLite database is used as an underlying data store.
Therefore, the implementation is formally an object-relational
(O/R) database, but the underlying data store could be, for
example, a random access file.

For simplicity, error checkings are not done. It is assumed that
a user does not give erroneous input for the embed method.

The term "node" is often used in the place of the term "object".

Nodes (objects) in the run-time memory are marked as s, p, q.
Their corresponding nodes in the object database are marked as s',
p', q' if they exist. However, we mark the nodes in the object
database as s', p' or q' even though the appropriate node s, p or
q does not exist in the run-time memory. The node s always denotes
a modified object structure (its root node) in the run-time memory
for which the embed method is called.


About the node types and the node ids
-------------------------------------
Each non-null node in the database has a unique id greater
than zero. The ids are not reused.

The implementation supports two kinds of nodes, ordinary nodes
(fixed nodes) and list nodes. Java types of fixed nodes (like
Person, Friend etc.) are defined by the client program. Each
type of the fixed node has its own SQL table created by the
system automatically when the (Java) type is encountered for the
first time.

For simplicity, inheritance is not supported for the Java types
of the nodes.

Fields of the nodes are either of type scalar or pointer.

For simplicity, the allowed scalar types are only the Java types
int, Integer and String. In list nodes the type int is replaced
automatically with the type Integer, because a run-time list node
stores its items into a Java array list.

In the run-time memory a pointer field of a node contains
reference to the referred node (Java object). In the database
a pointer field contains the id of the referred node, or zero
if the field refers to a null node. Null nodes are not stored
explicitly in the database.

When a node p' is allocated in the object database, it gets
an id>0 which has not been used before in the database. At the
same time the same id is bound to p, i.e. stored in its id field.

In the run-time memory, the type of the field of a fixed node is
determined from the field but the type of the field of a list
node is determined from the content of the field. Single list
node can contain items of different types, fore example scalar and
pointer values. To avoid ambiguity, a null value in a run-time
list node is interpreted as a special value "None". In the
database, fields of the list nodes have explicit types.

Each run-time node has a compulsory id field of type int storing
the id of corresponding node in the object database if that
exists. If a node p in the run-time memory has id>0, it has a
corresponding node p' in the database having that id. Otherwise,
when the id is zero for p, then p does not have a corresponding
node in the database.
*/




public class TestDB
{

  //Name of the database.
  private String dbName;

  //Java JDBC connection of the database.
  private Connection connection;

  //Id of a null node is zero in the run-time memory and in the
  //database.
  public static int ID_NULL_NODE = 0;


  //Id value used for a non-null run-time node p if p does not
  //yet have a corresponding node p' in the database.
  public static int ID_ZERO = 0;

  //In the run-time memory, a null value in a list node
  //requires special handling because a null value does
  //not have explicit type in Java. Therefore, in this context,
  //the type of the null is interpreted as a special type "NONE".
  public static int FIELD_TYPE_NONE              = 0;

  //Here are the supported scalar types for the fields of the
  //nodes.
  //Java type int is supported only for fixed nodes.
  public static int FIELD_TYPE_INT     = 1;
  //Java type Integer is supported for fixed nodes and list nodes.
  public static int FIELD_TYPE_INTEGER = 2;
  //Java type String is supported for fixed nodes and list nodes.
  public static int FIELD_TYPE_STRING  = 3;

  //Supported pointer fields for the nodes are pointer to a fixed
  //node and pointer to a list node.
  public static int FIELD_TYPE_FIXED_NODE        = 4;
  public static int FIELD_TYPE_LIST_NODE         = 5;

  //The embed method is called for a modified object structure s
  //(with its root node) in the run-time memory. In the beginning
  //of the embed method non-null nodes in s are separated in two
  //groups, white nodes and gray nodes. A non-null node p in s is
  //a white node if its id is zero, i.e. if p does not yet have a
  //corresponding node in the the database. Otherwise, if id>0, p
  //already has a corresponding node in the database with that id
  //and p is a gray node. White nodes are collected in the list
  //whiteNodes and gray nodes are collected in the list GrayNodes.
  ArrayList<Object> whiteNodes;
  ArrayList<Object> grayNodes;

  //As a side effect, the update phase of the embed method
  //collects ids of potential garbage nodes, it finds, in the
  //set seedGarbageIds. When the update phase has been finished,
  //all possible garbage nodes belong to set or graph Z
  //consisting of the nodes reachable from the nodes having their
  //ids in seedGarbageIds. It is possible that only some, or none
  //of the nodes in Z are garbage. Z may also be empty.
  HashSet<Integer> seedGarbageIds;

  //The map countOfInternalReferencesInZ is used to count the
  //internal references inside Z. The key is id of a node and the
  //value is count of internal references to that node in Z.
  HashMap<Integer,Integer> countOfInternalReferencesInZ;

  //In Z, nodes which are referred to from outside the Z,
  //cannot be garbage. Ids of those nodes are collected in the
  //set idsOfNodesRefOutsideZ.
  HashSet<Integer> idsOfNodesRefOutsideZ;

  //This map is for a tool method search which can be used to load
  //(search) object structures from the object database into the
  //run-time memory.
  HashMap<Integer,Object> readNodes;



//////////////////////////////////////////////////////////////////
// Public methods.

  //This method is constructor of the object database.
  //The client program calls it to create a new object database or
  //to open an existing database. The method call is like:
  //
  //  TestDB db = new TestDB(dbName);
  //
  //The method creates a new database if the database with a given
  //name does not exist yet. When the database is created, initial
  //system tables for it are created at the same time. This
  //includes creating two tables to store list nodes.
  //
  //Tables for different types of fixed nodes are created
  //dynamically on-fly in the method
  //methodcreateDBTableForFixedNodewhen when the appropriate type
  //is encountered for the first time.
  public TestDB(String dbName)
  throws Exception
  {
    this.dbName = dbName;

    boolean isOldDB = existsDB(dbName);

    try
    {
      Class.forName("org.sqlite.JDBC");
      connection =
        DriverManager.getConnection("jdbc:sqlite:"+dbName);
    }
    catch ( Exception e )
    {
      System.out.println( e.getClass().getName() +
        ": " + e.getMessage() );
      System.exit(0);
    }


    if (isOldDB)
      return;


    //Higher level structure of a node instance is stored in the
    //nodeInstances table.
    String str =
      "CREATE TABLE nodeInstances"
      +" ("

      //Unique id of the node in the object database.
      +"id INTEGER PRIMARY KEY AUTOINCREMENT,"

      //Count of outer references (orc) to the node.
      +"orc INTEGER,"

      //Count of internal references (irc) to the node.
      +"irc INTEGER,"

      //Full Java type name of the node.
      //For a list node the name is "testdb.ListNode"
      //For a fixed node the type name is, for example,
      //"userclasses.Friend".
      //(It is inefficient to store for each node instance the
      //full Java type name, but this is a demo implementation.)
      +"className TEXT"

      +")";
    executeStatement(str);


    //List node instances are stored in the tables "list" and
    //"listitems" in th database.

    str =
      "CREATE TABLE lists"
      +" ("

      //Primary key. The listItems table refers to this field.
      +"id INTEGER PRIMARY KEY AUTOINCREMENT,"

      //Refers to nodeInstances.id.
      +"instanceId INTEGER,"

      //Length of the list node, i.e. how many items the list node
      //contains currently.
      +"len INTEGER"

      +")";
    executeStatement(str);


    str =
      "CREATE TABLE listItems"
      +" ("

      //Primary key.
      +"id INTEGER PRIMARY KEY AUTOINCREMENT,"

      //Refers to lists.id.
      +"parent INTEGER,"

      //Position (0,1,..) of an item in the list.
      +"position INTEGER,"

      //Type code of an item in a list.
      //Defines explicitly the type of the item.
      +"type INTEGER,"

      //An item in a list. The item can be a scalar value or
      //pointer to node, to a fixed node or to a list node. If
      //an item is a scalar value, it is stored as such. Note that
      //SQLite database uses dynamic type system. Therefore also
      //a string scalar can be stored in this field. If item is
      //pointer to a node then item (integer value) refers to
      //nodeInstances.id.
      +"item INTEGER"

      +")";

    executeStatement(str);
  }


  //This method closes the database.
  public void close()
  throws Exception
  {
    connection.close();
  }


  //This method tests whether the database with a given name
  //exists.
  public static boolean existsDB(String dbName)
  throws Exception
  {
    return (new File(dbName)).exists();
  }


  //This method deletes the database with a given name.
  public static void deleteDB(String dbName)
  throws Exception
  {
    (new File(dbName)).delete();
  }


  //This is the famous embed method which a client program calls to
  //update the object database with a modified object structure s
  //in the run-time memory. The method is called with the root
  //object s of the modified object structure. The embed method
  //consists of two co-operating phases, the update phase and the
  //garbage collection phase.
  public void embed(Object s)
  throws Exception
  {
    update(s);
    garbageCollection();
  }


  //This is a tool method. Method incrORC can be called for a
  //non-null run-time node p having a corresponding node p' in the
  //object database. Method incrORC increments the outer reference
  //count, orc, of p' by one. Note that it is allowed that orc of
  //p' gets greater values than one. If orc>1 it can be thought
  //that a node p' has been saved several times from outside the
  //database or that many users refer to it from outside the
  //database.
  public void incrORC(Object p)
  throws Exception
  {
    incrORC(getId(p));
  }


  //This is a tool method. The method can be understood as a
  //delete operation for a node structure p' in the object
  //database. Method decrORC can be called for a non-null run-time
  //node p having a corresponding node p' in the object database.
  //It is assumed that the outer reference count, orc of p' is
  //greater than zero before calling the method. Error checking is
  //not done. Method decrORC decrements the orc of p' by one and
  //calls garbage collection for p' because now p' and some nodes
  //reachable from it can be garbage.
  public void decrORC(Object p)
  throws Exception
  {
    //Create the global set seedGarbageIds.
    seedGarbageIds = new HashSet<Integer>();

    decrORC(getId(p));

    seedGarbageIds.add(getId(p));

    garbageCollection();
  }


  //This is a tool method. A client program can use this method to
  //load (search) object structures from the object database back
  //to the run-time memory.
  //
  //Example invocation:
  //
  //db.searchFixedNodesFromDB(Friend.class,"name","A",
  //                         Friend.class,"age","23",
  //                         Book.class,"age","23"
  //                         )
  //
  //This invocation performs three searches and returns
  //ArrayList<Object> of three items, having type Friend, Friend
  //and Book, respectively. Also reachable nodes from these nodes
  //are returned, i.e. loading is a deep operation. If the same
  //node instance exists several times in the returned node
  //structures, it is returned only once.
  //
  //If a search condition match for several nodes, only the
  //first node that was found, and its reachable nodes, are
  //returned.
  //
  //It is possible that a search condition does not match
  //for any node.
  //
  //The search condition contains three items:
  //  The type of the node (for example Friend.class).
  //  The name of the scalar field (for example "name").
  //  The scalar value (compatible with the type of the search
  //  field) used to test equality in a search operation
  //(for example "A").
  public ArrayList<Object> searchFixedNodesFromDB
    (Object... searchRules)
  throws Exception
  {
    ArrayList<Object> resultNodes = new ArrayList<Object>();

    readNodes = new HashMap<Integer,Object>();
    int rootCount = searchRules.length/3;
    for (int i=0; i<rootCount; ++i)
    {
      int j = 3*i;
      Class<?> classOfFixedNode     = (Class<?>) searchRules[j];
      String fieldNameOfScalarField = (String)   searchRules[j+1];
      Object value                  =            searchRules[j+2];

      Object node = searchFixedNodeFromDB(classOfFixedNode,
                      fieldNameOfScalarField, value);
      if (node != null)
        resultNodes.add(node);
    }
    return resultNodes;
  }
// Public methods.
//////////////////////////////////////////////////////////////////



//////////////////////////////////////////////////////////////////
// Higher level private methods.

  //The embed method calls the update method with the root node s
  //of the modified object data structure in the run-time memory.
  //After the update method has been finished, content of the
  //object structure s exists in the database.
  //
  //As a side effect, the update method produces the set of ids of
  //potential garbage nodes, it finds, into the set seedGarbageIds.
  //The set seedGarbageIds is input for the garbage collection
  //phase of the embed method.
  //
  //Below the terms "step 1", "step 2", "step 3" and "step 4"
  //refer to the steps described in the article, in section 4.
  private void update(Object s)
  throws Exception
  {
    //Global lists for the white nodes and the gray nodes in s.
    whiteNodes = new ArrayList<Object>();
    grayNodes = new ArrayList<Object>();

    //This boolean value dscribes whether the root node s is
    //white. This information is used in the step 4 of this
    //method.
    boolean rootIsWhite =
      getId(s) == ID_ZERO;

    //Create the global set seedGarbageIds.
    seedGarbageIds = new HashSet<Integer>();

//Step 1 and step 2 of the update method:
    //Step1: Collect white nodes in object the structure s in the
    //list whiteNodes and gray nodes in s in the list grayNodes.
    //Step 2: For each white node in s: Allocate the same type of
    //empty node in the database.
    collectWhiteAndGrayNodes(s);

//Step 3 of the update method:
    //Handle changes of internal reference counts of the nodes
    //caused by updating the database with the white nodes.
    handleReferencesFromWhiteNodesInDB();

    //Handle changes of internal reference counts of the nodes
    //caused by updating the database with the gray nodes.
    handleReferencesFromGrayNodesInDB();

    //Update the database with the white nodes.
    copyContentsOfWhiteNodesToDB();

    //Update the database with the gray nodes.
    copyContentsOfGrayNodesToDB();


//Step 4 of the update method:
    //For selected white nodes in s make their corresponding nodes
    //in the database persistent root nodes by setting them orc=1.
    //
    //We simply select only the root node s, if it is a white node.
    //Otherwise no nodes are selected.
    //
    //When a root node s is a white node it is obvious that the
    //user wants to make s' a persistent root node to make s' and
    //all the reachable nodes from it persistent in the database.
    //
    //There exist rare cases where a user may want that for a
    //white root node s the corresponding node s' gets orc=0 and
    //does not not become a persistent root node in the database.
    //For example if the run-time object structure s is a circular
    //structure s -> p -> s, where s is a white node and p is a
    //gray node, a user may want that persistence of s' depends on
    //the persistence of p'. Therefore orc could be zero for s in
    //this case.
    //
    //If the embed method is called for a gray node s, then the
    //orc of s' is kept as it was. This is a natural decision.
    //However, this can cause a strange but perhaps correct
    //effects in some cases; the node s', and perhaps some
    //reachable nodes from it can disappear from the object
    //database as a consequence of an executed embed method! The
    //following example describes this.
    //
    //Let s' originally refer to some node p' and p' refer back to
    //s' and suppose that no-one else in the database is referring
    //to s', and that orc=0 for s'. After that circular structure
    //s -> p -> s is modified in the run-time memory by setting p
    //to refer to a null node, i.e. not to s anymore. After that
    //the embed method is called for s. As a consequence s'
    //becomes garbage, because now irc=orc=0 for s'.
    if (rootIsWhite)
      incrORC(getId(s));

    //Free the lists reserved for the white nodes and the gray nodes.
    whiteNodes = null;
    grayNodes = null;
  }


  //Method collectWhiteAndGrayNodes is called from the method
  //update. This method separates white nodes and gray nodes in the
  //object structure s. White nodes are collected in the list
  //whiteNodes and gray nodes in the list grayNodes.
  //
  //In addition, the method allocates for each white node p in s a
  //corresponding empty node p' (having a corresponding type) in
  //the object database. The node p gets the id of p' into its
  //id field.
  private void collectWhiteAndGrayNodes(Object p)
  throws Exception
  {
    //Null nodes are note collected.
    if (p==null)
      return;

    //The same node instance is not collected twice.
    if (bufferContainsNode(whiteNodes,p)
        || bufferContainsNode(grayNodes,p))
      return;

    int id = getId(p);

    //If p is a white node, collect p and allocate a corresponding empty
    //node p' in the database. Assign id of p' to p.
    if (id == ID_ZERO)
    {
      id = allocateNodeInDB(p);
      setId(p,id);
      whiteNodes.add(p);
    }
    else //Collect a gray node p.
      grayNodes.add(p);

    //Collect non-null child nodes of p if not yet collected.
    ArrayList<FieldT> pointerFields = getFields(p.getClass(),p,1);
    for (FieldT f : pointerFields)
      collectWhiteAndGrayNodes(f.value);
  }


  //Handle changes of internal reference counts caused by updating
  //the object database with white nodes.
  private void handleReferencesFromWhiteNodesInDB()
  throws Exception
  {
    for(Object p:whiteNodes)
      handleReferencesFromWhiteNodeInDB(p);
  }
  //Here p is a white node. Updating p' with the p can increase
  //internal reference counts of some nodes in the database. These
  //changes are updated in this method.
  //
  //The following rule gives the result:
  //If a white node p refers with a pointer field f to a non-null
  //node q then the internal reference count of the node q'
  //increments by one. If p refers to q many times (with many
  //pointer fields), then the internal reference count of q'
  //increments as many times.
  private void handleReferencesFromWhiteNodeInDB(Object p)
  throws Exception
  {
    ArrayList<Integer> C1 = getIdsOfNonNullChildNodes(p);
    for(Integer id:C1)
      incrIRC(id);
  }


  //Handle changes of internal reference counts caused by updating
  //the object database with gray nodes.
  private void handleReferencesFromGrayNodesInDB()
  throws Exception
  {
    for(Object p:grayNodes)
      handleReferencesFromGrayNodeInDB(p);
  }
  //Here p is a gray node. Updating p' with the p can change
  //internal reference counts of some nodes in the database. These
  //changes are updated in this method.
  //
  //The following rules give the result:
  //
  //1)
  //If, before updating the node p' with a gray node p, the node
  //p' refers with a pointer field f' to a non-null node q' then
  //the internal reference count of q' decrements by one. If p'
  //refers to q' many times (with many pointer fields), then the
  //internal reference count of q' decrements as many times.
  //
  //2)
  //If a gray node p refers with a pointer field f to a non-null
  //node q then the internal reference count of the node q'
  //increments by one. If p refers to q many times (with many
  //pointer fields), then the internal reference count of q'
  //increments as many times.
  //
  //The algorithm does incrementing/decrementing in such a way
  //that the internal reference count of a node is only
  //incremented or decremented, not both.
  //
  //If, after updating, the node p' does not any more refer to a
  //non-null node q', then q' may be garbage. In this case the id
  //of q' is added conditionally to set seedGarbageIds, if it is
  //not yet there.
  private void handleReferencesFromGrayNodeInDB(Object p)
  throws Exception
  {

    //Construct the list C1 = (id(q1),..,id(qn)) of the ids of
    //non-null child nodes of the node p. If p refers several
    //times to the same non-null child node q then the id of q is
    //as many times in the list C1.
    ArrayList<Integer> C1 =
      getIdsOfNonNullChildNodes(p);


    //Construct the list C2 = (id(q'1),..,id(q'm)) of the ids of
    //non-null child nodes of the node p' (before p' has been
    //updated with p). If p' refers several times to the same
    //non-null child node q' then the id of q' is as many times
    //in the list C2.
    ArrayList<Integer> C2 =
      getIdsOfNonNullChildNodesOfDBNode(getId(p));

    //The set I will contain the intersection of C1 and C2. (The
    //same id is not twice in I).
    HashSet<Integer> I = new HashSet<Integer>();

    //Make lists C1 and C2 disjoint. The intersection of C1 and C2
    //is collected in the set I. Note that C1 can contain the
    //same id several times, before and after making C1 and C2
    //disjoint. The same is true for the list C2.
    //For example: Let
    //C1 = (1,2,1,3,4,1,2,2,2,3,3)
    //C2 = (1,1,2,2,3,2,2,2,3,3,5,5)
    //Then, after making C1 and C2 disjoint, C1, C2 and I are:
    //C1 = (4,1)
    //C2 = (2,5,5)
    //I = {1,2,3}
    int i=0;
    while (i < C1.size())
    {
      int id = C1.get(i);
      if (C2.remove((Integer)id))
      {
        C1.remove(i);
        I.add(id);
      }
      else
       ++i;
    }

    //Decrement the internal reference count, irc, of the nodes
    //that have their ids in C2. If id is not in set I, add id
    //conditionally to set seedGarbageIds.
    for(Integer id2:C2)
    {
      decrIRC(id2);

      //If set I contains the id2 then p' will still refere to the
      //node having the id value id2.
      if (I.contains(id2))
        continue;

      //Node p' does not any more refer to a node that has the id
      //value id2. If we are not certain that the node is not
      //garbage add the id2 to the set seedGarbageIds if it is not
      //yet there.
      if (!isNodeCertainlyNotGarabge(id2))
        seedGarbageIds.add(id2);

    }

    //Increment the internal reference counts, irc, of the nodes
    //having their ids in C1.
    for(Integer id1:C1)
      incrIRC(id1);
  }


  //This method updates the object database with the white nodes
  //in a flat way. If a field of a white node p is a pointer field
  //then the id of a node in the field is copied to p', not the
  //node itself.
  private void copyContentsOfWhiteNodesToDB()
  throws Exception
  {
    for(Object p:whiteNodes)
      copyContentOfNodeToDB(p);
  }


  //This method updates the object database with the gray nodes
  //in a flat way. If a field of a gray node p is a pointer field
  //then the id of a node in the field is copied to p', not the
  //node itself. For simplicity, contents of all fields are
  //copied, i.e. not only the changed fields. Also, to make
  //implementing easy, for a list node p the p' is first cleared
  //by making it an empty list.
  private void copyContentsOfGrayNodesToDB()
  throws Exception
  {
    for(Object p:grayNodes)
      copyContentOfGrayNodeToDB(p);
  }
  private void copyContentOfGrayNodeToDB(Object p)
  throws Exception
  {
    //This is a null operation for a fixed node.
    removeFieldsOfNodeInDB(p);

    copyContentOfNodeToDB(p);
  }


 //After the embed method has called the update method, it
 //calls the garbageCollection method to remove possible garbage
 //nodes from the object database.
 //
 //Real garbage nodes belong to the graph Z consisting of the
 //nodes reachable from nodes having their ids in the set
 //seedGarbageIds. Typically, only some or none of the nodes in Z
 //are garbage. Z may also be an empty set.
 //
 //The graph Z is examined by walking (traversing) it, each edge
 //in Z once. During walking, references for the reached nodes are
 //calculated. Walking produces for each node in Z the count of
 //incoming references in Z. By using this information and
 //reference count information (irc and orc) stored in the nodes
 //of Z it is determined which nodes in Z are referred to from
 //outside the Z. These nodes and reachable nodes from them are
 //not garbage. The remaining nodes in Z are real garbage.
 //
 //In some cases only part of the Z is needed to walk, i.e. Z can
 //be shrunk.
 private void garbageCollection()
  throws Exception
  {
    //The map used to count incoming internal references in Z.
    countOfInternalReferencesInZ
      = new HashMap<Integer,Integer>();
    idsOfNodesRefOutsideZ = new HashSet<Integer>();

    //Walk the Z and calculate incoming internal references in Z.
    calculateReferencesProducedByWalkingInZ();

    //Determine the nodes in Z referred to from outside the Z.
    collectIdsOfNodesReferecedOutsideZ();

    //Determine non-garbage nodes in Z. Remaining nodes in Z are
    //real garbage nodes to be removed from the object database.
    removeIdsOfNonGarbageNodesInZ();

    //Remove garbage nodes from the database.
    removeGarbageNodesFromDB();

    //Free the global structures.
    countOfInternalReferencesInZ = null;
    idsOfNodesRefOutsideZ = null;
    seedGarbageIds = null;
  }


  //Calculate for each node in Z the count of incoming references
  //in Z. For that we walk (traverse) the graph Z in the database,
  //each edge once. The counts of incoming references in Z are
  //collected in the map countOfInternalReferencesInZ where the
  //key is the id of the node and the value is the count of the
  //incoming references to that node in the Z.
  private void calculateReferencesProducedByWalkingInZ()
  throws Exception
  {
    for (Integer seedGarbageId:seedGarbageIds)
    {
      //A trick:
      //Let p' be the node having id value seedGarbageId. If the
      //method call "walk(seedGarbageId)" walks to p' then the
      //count of incoming references for p' must be decreased
      //afterward by one because the node p' is not reached through
      //a real edge in Z.
      if (walk(seedGarbageId))
        addToInternalReferencesInZ(seedGarbageId,-1);
    }
  }
  private boolean walk(Integer id)
  throws Exception
  {
    //Here we try to make the Z smaller, i.e. to the node having
    //"id" is not walked to if we are sure that this node is not
    //garbage.
    if (isNodeCertainlyNotGarabge(id))
      return false;

    boolean nodeReachedBefore =
      countOfInternalReferencesInZ.containsKey(id);

    if (nodeReachedBefore) //The node has been seen before.
    {
      addToInternalReferencesInZ(id,1);
      return true;
    }

    //The node has not been seen before.
    countOfInternalReferencesInZ.put(id,1);

    //Walk to non-null child nodes.
    ArrayList<Integer> childIds =
      getIdsOfNonNullChildNodesOfDBNode(id);
    for(Integer idChild : childIds)
    {
      walk(idChild);
    }
    return true;
  }


  //This method returns true if we are sure that the node having
  //the id is not garbage. However, in this demo implementation
  //this method returns always false. Some checkings
  //could be done in real implementations. Some suggestions are in
  //comments.
  private boolean isNodeCertainlyNotGarabge(Integer id)
  throws Exception
  {
    //Possible checkings, for example:
    //1)
    //if "readORC(id) > 0" then the node is a persistent root node
    //and it cannot be garbage.
    //
    //2)
    //The node having the id can not be garbage if some node in the
    //node structure s has the same id and the root node s is a
    //white node. In this case the corresponding node s' is a
    //persistent root node and all reachable nodes from it are
    //persistent.
    //
    //3)
    //It is possible to implement an embed method which takes as a
    //parameter a list of ids of nodes which can not be garbage.
    //(The child program can know strategic nodes which are not
    //garbage) The parameter id of this method could be compared
    //to these ids.

    return false;
  }


  void addToInternalReferencesInZ(Integer id, int value)
  {
    int oldValue = countOfInternalReferencesInZ.get(id);
    countOfInternalReferencesInZ.put(id,oldValue+value);
  }


  //Determine in the Z the nodes, their ids, which are referred to
  //from outside the Z. These ids are collected in the set
  //idsOfNodesRefOutsideZ.
  private void collectIdsOfNodesReferecedOutsideZ()
  throws Exception
  {
    for (Map.Entry<Integer, Integer> e :
      countOfInternalReferencesInZ.entrySet())
    {
      int id = e.getKey();
      int countOfInternalReferences = e.getValue();
      int irc = readIRC(id);
      int orc = readORC(id);

      //Here we test if a node is referred to from outside the Z,
      //i.e. if a node is a persistent root node (orc >= 1) or
      //it is referred to from some node outside the Z
      //(countOfInternalReferences < irc). Note that always
      //countOfInternalReferences <= irc.
      //
      //Also note that if would filter (not done) in the method
      //isNodeCertainlyNotGarabge the nodes that have orc > 0 then
      //the test below ccould be replaced with the test
      //"if (countOfInternalReferences < irc)"
      if (countOfInternalReferences < orc + irc)
        idsOfNodesRefOutsideZ.add(id);
    }
  }


  //In the Z nodes reachable from nodes having they ids in the set
  //idsOfNodesRefOutsideZ are not garbage. In this method ids of
  //these nodes are removed from the map
  //countOfInternalReferencesInZ. The remaining nodes, having
  //their ids in the map countOfInternalReferencesInZ, are the
  //real garbage nodes.
  private void removeIdsOfNonGarbageNodesInZ()
  throws Exception
  {
    for(Integer id : idsOfNodesRefOutsideZ)
      removeIdOfNonGarbageNodeInZ(id);
  }
  private void removeIdOfNonGarbageNodeInZ(Integer id)
  throws Exception
  {
    if (countOfInternalReferencesInZ.remove(id) == null)
      return;

    ArrayList<Integer> childIds
      = getIdsOfNonNullChildNodesOfDBNode(id);
    for(Integer idChild : childIds)
      removeIdOfNonGarbageNodeInZ(idChild);
  }


  //Remove real garbage nodes from the database. These are the
  //nodes having their ids in the map
  //countOfInternalReferencesInZ.
  private void removeGarbageNodesFromDB()
  throws Exception
  {
    Set<Integer> keys = countOfInternalReferencesInZ.keySet();
    for(Integer id : keys)
      removeGarbageNodeFromDB(id);
  }
  private void removeGarbageNodeFromDB(int id)
  throws Exception
  {
    //Internal reference counts of (non-null) non-garbage
    //child nodes must be decremented accordingly.
    ArrayList<Integer> childIds =
      getIdsOfNonNullChildNodesOfDBNode(id);
    for(Integer idChild : childIds)
      if (!countOfInternalReferencesInZ.containsKey(idChild))
        decrIRC(idChild);

    Class<?> c = getClassOfDBNode(id);

    if (c != ListNode.class)
      removeFixedNodeFromDB(c,id);
    else
      removeListNodeFromDB(id);
  }


  //Remove a fixed node from the database, in a flat way.
  private void removeFixedNodeFromDB(Class<?> c, int id)
  throws Exception
  {
    String tableName =
      getFixedTableNameFromClassName(c.getName());
    executeDelete(tableName, "instanceId=?", id);
    executeDelete("nodeInstances", "id=?", id);
  }


  //Remove a list node from the database, in a flat way.
  private void removeListNodeFromDB(int id)
  throws Exception
  {
    int rowIdOfList = (Integer)
      readSingleValue("lists","id","instanceId=?",id);
    executeDelete("listItems", "parent=?", rowIdOfList);
    executeDelete("lists", "id=?", rowIdOfList);
    executeDelete("nodeInstances", "id=?", id);
  }
// Higher level private methods.
//////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////
// Tool methods for the run-time memory nodes.
  private int getId(Object p)
  throws Exception
  {
    if (p==null)
      return ID_NULL_NODE;

    Class c = p.getClass();
    Field f = c.getDeclaredField("id");
    f.setAccessible(true);
    return (Integer) f.get(p);
  }


  private void setId(Object p, int id)
  throws Exception
  {
    Class<?> c = p.getClass();
    Field f = c.getDeclaredField("id");
    f.setAccessible(true);
    f.set(p,id);
  }


  private ArrayList<Integer> getIdsOfNonNullChildNodes(Object p)
  throws Exception
  {
     ArrayList<Integer> ids = new ArrayList<Integer>();
     ArrayList<FieldT> fields = getFields(p.getClass(),p,1);
     for(FieldT f: fields)
     {
       int id = getId(f.value);
       if (id != ID_NULL_NODE)
         ids.add(id);
     }
     return ids;
  }


  //Fields of the run-time node p are returned in a ArrayList.
  //Both a field and its content is returned in the structure
  //FieldT. A caller selects with the selector if only scalar
  //fields or only pointer fields, or if both types of fields
  //are returned.
  //
  //The c is class of node (fixed or list node).
  //If method is called for a fixed node,
  //the null p may be given as a parameter.
  //In this case no values are returned in the fields, i.e.
  //FieldT.value is not filled in this case.
  //Values of the selector:
  //0 = Pointer fields and scalar fields are returned.
  //1 = Only pointer fields are returned.
  //2 = Only scalar fields are returned.
  private ArrayList<FieldT>
    getFields(Class<?> c, Object p, int selector)
  throws Exception
  {
    if (c != ListNode.class)
      return getFieldsOfFixedNode(c,p,selector);
    else
      return getFieldsOfListNode((ListNode)p,selector);
  }
  private ArrayList<FieldT>
    getFieldsOfFixedNode(Class<?> c, Object p, int selector)
  throws Exception
  {
    ArrayList<FieldT> fields = new ArrayList<FieldT>();
    Field[] allFields = c.getDeclaredFields();
    for(Field f: allFields)
    {
      if (f.getName().equals("id"))
        continue;

      Class<?> cf = f.getType();
      int typeCodeOfField = getTypeCodeOfFieldFromClass(cf);

      boolean addPointerField =
        isPointerField(typeCodeOfField) &&
          (selector == 0 || selector == 1);
      boolean addScalarField =
        isScalarField(typeCodeOfField) &&
          (selector == 0 || selector == 2);

      if (addPointerField || addScalarField)
      {
        Object value = null;
        if (p != null)
        {
          f.setAccessible(true);
          value = f.get(p);
        }
        fields.add(new FieldT(f,typeCodeOfField,value));
      }
    }
    return fields;
  }


  private ArrayList<FieldT>
    getFieldsOfListNode(ListNode p, int selector)
  throws Exception
  {
    ArrayList<FieldT> fields = new ArrayList<FieldT>();

    int len = p.list.size();
    for(int pos=0;pos<len;++pos)
    {
      Object value = p.list.get(pos);
      int typeCodeOfField =
        getTypeCodeOfFieldFromValueInField(value);

      boolean addPointerField =
        isPointerField(typeCodeOfField) &&
          (selector == 0 || selector == 1);
      boolean addScalarField =
        isScalarField(typeCodeOfField) &&
          (selector == 0 || selector == 2);

      if (addPointerField || addScalarField)
        fields.add(new FieldT(pos,typeCodeOfField,value));

    }//for
    return fields;
  }


  private boolean
    bufferContainsNode(ArrayList<Object> buffer, Object p)
  {
    for (Object o:buffer)
      if (o == p) return true;
    return false;
  }


  private boolean grayNodesContainsId(int id)
  throws Exception
  {
    for(Object p:grayNodes)
      if (getId(p) == id)
        return true;
    return false;
  }
// Tool methods for the run-time memory nodes.
//////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////
// Tool methods for the nodes in DB.
  private int allocateNodeInDB(Object p)
  throws Exception
  {
    Class<?> c = p.getClass();
    if (c != ListNode.class)
      return allocateFixedNodeInDB(p);
    else
      return allocateListNodeInDB();

  }
  private int allocateFixedNodeInDB(Object p)
  throws Exception
  {
    Class<?> c = p.getClass();
    String tableName =
      getFixedTableNameFromClassName(c.getName());

    //If a node type is a new one, create the corresponding SQL
    //table.
    if (!tableExists(tableName))
      createDBTableForFixedNode(c);

    //Insert an empty fixed node. Set orc=irc=0 for the inserted
    //node.
    int id = doInsertReturnPrimaryKey
      ("INSERT INTO nodeInstances (id,orc,irc,className) VALUES(NULL,0,0,?)",c.getName());
    executeStatement
      ("INSERT INTO " + tableName + " (id,instanceId) VALUES(NULL,?)",id);

    return id;
  }
  private int allocateListNodeInDB()
  throws Exception
  {
   //Insert an empty list node. Set orc=irc=0 for the inserted
   //node.
   int id = doInsertReturnPrimaryKey
     ("INSERT INTO nodeInstances (id,orc,irc,className) VALUES(NULL,0,0,?)",
       ListNode.class.getName());

   executeStatement("INSERT INTO lists VALUES(NULL,?,0)",id);

   return id;
  }


  private void removeFieldsOfNodeInDB(Object p)
  throws Exception
  {
    Class<?> c = p.getClass();
    if (c != ListNode.class)
      ;
    else
      removeFieldsOfListNodeInDB(getId(p));
  }
  private void removeFieldsOfListNodeInDB(int id)
  throws Exception
  {
    int rowIdOfList = (Integer) readSingleValue
      ("lists","id","instanceId=?",id);

    executeDelete("listItems", "parent=?", rowIdOfList);

    executeStatement
      ("UPDATE lists SET len=0 WHERE id=?", rowIdOfList);
  }


  private void copyContentOfNodeToDB(Object p)
  throws Exception
  {
    Class<?> c = p.getClass();
    if (c != ListNode.class)
      copyContentsOfFixedNodeToDB(p);
    else
      copyContentsOfListNodeToDB((ListNode)p);
  }
  private void copyContentsOfFixedNodeToDB(Object p)
  throws Exception
  {
    ArrayList<FieldT> fields = getFields(p.getClass(),p,0);
    for (FieldT f : fields)
      writeValueToFieldOfDBNode(p,f);
  }
  private void copyContentsOfListNodeToDB(ListNode p)
  throws Exception
  {
    ArrayList<FieldT> fields = getFields(p.getClass(),p,0);
    for (FieldT f : fields)
      writeValueToFieldOfDBListNode(p,f);
  }


  private void writeValueToFieldOfDBNode(Object p, FieldT f)
  throws Exception
  {
    Class<?> c = p.getClass();
    if (c != ListNode.class)
      writeValueToFieldOfDBFixedNode(p,f);
    else
      writeValueToFieldOfDBListNode(p,f);
  }
  private void writeValueToFieldOfDBFixedNode(Object p, FieldT f)
  throws Exception
  {
    Object valueDB = f.value;

    if (isPointerField(f.typeCode))
      valueDB = getId(valueDB);

    Class<?> c = p.getClass();
    String tableName =
      getFixedTableNameFromClassName(c.getName());

    int rowId = (Integer) readSingleValue
      (tableName,"id","instanceId=?",getId(p));

    String fieldName = ((Field) f.field).getName();

    updateSingleValue(tableName,fieldName,"id=?",valueDB,rowId);
  }
  private void writeValueToFieldOfDBListNode(Object p, FieldT f)
  throws Exception
  {
    Object valueDB = f.value;

    if (isPointerField(f.typeCode))
      valueDB = getId(valueDB);

    int rowIdOfList = (Integer) readSingleValue
      ("lists","id","instanceId=?",getId(p));

    executeStatement("INSERT INTO listItems VALUES(NULL,?,?,?,?)",
                     rowIdOfList,
                     (Integer)f.field,
                     f.typeCode,
                     valueDB);

    executeStatement
      ("UPDATE lists SET len=len+1 WHERE id=?",rowIdOfList);
  }


  private ArrayList<Integer>
    getIdsOfNonNullChildNodesOfDBNode(int id)
  throws Exception
  {

     ArrayList<Integer> ids = new ArrayList<Integer>();
     ArrayList<FieldT> fields = readFieldsOfDBNode(id,1);
     for(FieldT f: fields)
     {
       int idChild = (Integer) f.value;
       if (idChild != ID_NULL_NODE)
         ids.add(idChild);
     }
     return ids;
  }


  //This method returns null if it does not find a node.
  private Object searchFixedNodeFromDB
   (Class<?> c, String fieldNameOfScalarField, Object value)
  throws Exception
  {
    String tableName =
      getFixedTableNameFromClassName(c.getName());

    if (!tableExists(tableName))
      return null;
    Integer instanceId = (Integer) readSingleValue
      (tableName,"instanceId", fieldNameOfScalarField+"=?",value);
    return readNodeFromDB(instanceId);
  }
  private Object readNodeFromDB(Integer id)
  throws Exception
  {
    if (id == ID_NULL_NODE)
      return null;

    //Test if the node has already been read.
    Object p = readNodes.get(id);
    if (p != null)
      return p;

    //Create the run-time node (object) corresponding the type of
    //the node in the object databse.
    p = createRunTimeNode(id);

    //Set that the node has been read now.
    readNodes.put(id,p);

    ArrayList<FieldT> fields = readFieldsOfDBNode(id,0);
    for(FieldT field : fields)
    {
      Object v = field.value;
      if (isPointerField(field.typeCode))
        v = readNodeFromDB((Integer) v); //Recursion.
      setValueInFieldOfObject(p,v,field);
    }
    return p;
  }


  private void setValueInFieldOfObject
   (Object o, Object value, FieldT field)
  throws Exception
  {
    Class<?> c = o.getClass();
    if (c != ListNode.class)
    {
      Field f = (Field) field.field;
      f.set(o,value);
    }
    else
      ((ListNode)o).list.add(value);
  }


  Object createRunTimeNode(int id)
  throws Exception
  {
    Class<?> c = getClassOfDBNode(id);
    Object p;
    if (c != ListNode.class)
    {
      Constructor cnstr = c.getDeclaredConstructor();
      cnstr.setAccessible(true);
      p = cnstr.newInstance();
      //Set the id value of the run-time fixed node.
      setId(p,id);
    }
    else
    {
      ListNode list = new ListNode();
      //Set the id value of the run-time list node.
      list.id = id;
      p = list;
    }
    return p;
  }


  //Fields of the database node are returned in a ArrayList.
  //Both a field and its content is returned in the structure
  //FieldT. A caller selects with the selector if only scalar
  //fields or only pointer fields, or if both types of fields
  //are returned.
  //
  //The id defines the node in the database.
  //
  //Values of the selector:
  //0 = Pointer fields and scalar fields are returned.
  //1 = Only pointer fields are returned.
  //2 = Only scalar fields are returned.
  ArrayList<FieldT> readFieldsOfDBNode(int id, int selector)
  throws Exception
  {
    Class<?> c = getClassOfDBNode(id);
    if (c != ListNode.class)
      return readFieldsOfDBFixedNode(c,id,selector);
    else
      return readFieldsOfDBListNode(id,selector);
  }
  private ArrayList<FieldT> readFieldsOfDBFixedNode
    (Class<?> c, int id, int selector)
  throws Exception
  {
    String tableName =
      getFixedTableNameFromClassName(c.getName());

    int rowId = (Integer) readSingleValue
      (tableName,"id","instanceId=?",id);

    //Get the fields from the corresponding run-time node type
    //(class). Do not fill the fields with any values yet.
    ArrayList<FieldT> fields = getFields(c,null,selector);

    //Fill the fields from the database.
    for(FieldT f : fields)
    {
      String fieldName = ((Field) f.field).getName();
      //Set the value in the field. The value is read from the
      //database.
      f.value = readSingleValue(tableName,fieldName,"id=?",rowId);
    }
    return fields;
  }
  private ArrayList<FieldT> readFieldsOfDBListNode
    (int id, int selector)
  throws Exception
  {
    ArrayList<FieldT> fields = new ArrayList<FieldT>();

    int rowIdOfList = (Integer) readSingleValue
      ("lists","id","instanceId=?",id);

    int len = (Integer) readSingleValue
      ("lists","len","id=?",rowIdOfList);

    for(int pos=0; pos<len; ++pos)
    {
      int typeCodeOfField = (Integer) readSingleValue
        ("listItems", "type", "parent=? AND position=?",
         rowIdOfList,pos);

      boolean addPointerField =
        isPointerField(typeCodeOfField) &&
          (selector == 0 || selector == 1);

      boolean addScalarField =
        isScalarField(typeCodeOfField) &&
          (selector == 0 || selector == 2);

      if (addPointerField || addScalarField)
      {
        Object value = readSingleValue
          ("listItems", "item", "parent=? AND position=?",
           rowIdOfList,pos);

        fields.add(new FieldT(pos,typeCodeOfField,value));
      }
    }
    return fields;
  }


//This method creates the table for a fixed node (for its type).
//These tables are created dynamically, when the system encounters
//the type of the fixed node for the first time.
//
//Below are two examples of Java types of fixed nodes, Friend and
//Book. Fields of the these nodes (their Java types) are public,
//but this is not a requirement. Every node (object) in the
//run-time memory contains a compulsory id field.
//
//
//  package userclasses;
//
//  public class Friend
//  {
//    //Id of the node. If id==0 the node does not have a
//    //corresponding node in the database. If id>0 the node has
//    //a corresponding node in the database identified by the
//    //id.
//    public int id;
//
//    //Scalar field of type String.
//    public String name;
//
//    //Scalar field of type Integer.
//    public Integer age;
//
//    //Pointer field referring to a list node. Contains Java
//    //reference to an object of type ListNode (or null).
//    public ListNode list;
//
//    //Pointer field referring to a fixed node of type Book.
//    //Contains Java reference to an object of type Book
//    (or null).
//    public Book book;
//
//    //Default constructor is required. The database uses
//    //this constructor to create a corresponding Java object
//    //when a node is loaded from the database with the search
//    //method.
//    public Friend(){}
//
//    <Possible other methods>
//  }
//
//  public class Book
//  {
//    public int id;
//
//    public String name;
//    public int price;
//
//    public Book(){}
//
//    <Possible other methods>
//  }
//
//
//Database tables created for the types of fixed nodes above:
//
//
//  CREATE TABLE userclasses_Friend
//  (
//    //Primary key.
//    id INTEGER PRIMARY KEY AUTOINCREMENT,
//
//    //Refers to nodeInstances.id.
//    instanceId INTEGER,
//
//    //Scalar field.
//    name TEXT,
//
//    //Scalar field.
//    age INTEGER,
//
//    //Pointer field referring to a list node. Refers to
//    //nodeInstances.id
//    //(or contains zero if refers to a null list node).
//    list INTEGER,
//
//    //Pointer field referring to a fixed node of type book.
//    //Refers to nodeInstances.id
//    //(or contains zero if refers to a null book node).
//    book INTEGER
//  )
//
//  CREATE TABLE userclasses_Book
//  (
//    id INTEGER PRIMARY KEY AUTOINCREMENT,
//    instanceId INTEGER,
//    name TEXT,
//    price INTEGER
//  )
  private void createDBTableForFixedNode(Class<?> c)
  throws Exception
  {
    String tableName =
      getFixedTableNameFromClassName(c.getName());

    String str = "CREATE TABLE "+tableName
               +" ("
               +"id INTEGER PRIMARY KEY AUTOINCREMENT"
               +",instanceId INTEGER";

    ArrayList<FieldT> fields = getFields(c,null,0);
    for (FieldT f : fields)
    {
      String fieldName = ((Field) f.field).getName();

      String sqlFieldType =
        getSQLFixedNodeFieldTypeFromTypeCode(f.typeCode);

      str += ("," + fieldName + " " + sqlFieldType);
    }
    str += ")";
    executeStatement(str);
  }


  private int readORC(int id)
  throws Exception
  {
     return (Integer)
       readSingleValue("nodeInstances","orc" , "id=?",id);
  }
  private int readIRC(int id)
  throws Exception
  {
     return (Integer)
       readSingleValue("nodeInstances","irc" , "id=?",id);
  }
  private void incrORC(int id)
  throws Exception
  {
    executeStatement
      ("UPDATE nodeInstances SET orc=orc+1 WHERE id=?",id);
  }
  private void decrORC(int id)
  throws Exception
  {
    executeStatement
      ("UPDATE nodeInstances SET orc=orc-1 WHERE id=?",id);
  }
  private void incrIRC(int id)
  throws Exception
  {
    executeStatement
       ("UPDATE nodeInstances SET irc=irc+1 WHERE id=?",id);
  }
  private void decrIRC(int id)
  throws Exception
  {
    executeStatement
      ("UPDATE nodeInstances SET irc=irc-1 WHERE id=?",id);
  }


  private static String
    getFixedTableNameFromClassName(String className)
  {
    return className.replace('.','_');
  }


  private Class<?> getClassOfDBNode(int id)
  throws Exception
  {
    String className = (String)
      readSingleValue("nodeInstances","className","id=?",id);
    return  Class.forName(className);
  }


  private boolean tableExists(String tableName)
  throws Exception
  {
    int count  = (Integer)
      readSingleValue("sqlite_master",
                      "count(*)",
                      "type=? AND name=?",
                      "table",
                      tableName);

    return (count == 1);
  }
// Tool methods for the nodes in DB.
//////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////
// Lower level tool methods.
  private static boolean isPointerField(int typeCodeOfField)
  {
    return typeCodeOfField == FIELD_TYPE_FIXED_NODE
    || typeCodeOfField == FIELD_TYPE_LIST_NODE;
  }
  private static boolean isScalarField(int typeCodeOfField)
  {
    return typeCodeOfField == FIELD_TYPE_INT
    || typeCodeOfField == FIELD_TYPE_INTEGER
    || typeCodeOfField == FIELD_TYPE_STRING
    || typeCodeOfField == FIELD_TYPE_NONE;
  }


  private static int getTypeCodeOfFieldFromValueInField
    (Object value)
  {
    if (value==null)
      return FIELD_TYPE_NONE;
   Class<?> cf = value.getClass();
   if (cf == Integer.class) return FIELD_TYPE_INTEGER;
   else if (cf == String.class) return FIELD_TYPE_STRING;
   else if (cf == ListNode.class) return FIELD_TYPE_LIST_NODE;
   else return FIELD_TYPE_FIXED_NODE;
  }


  private static int getTypeCodeOfFieldFromClass(Class<?> cf)
  {
    if (cf == int.class) return FIELD_TYPE_INT;
    else if (cf == Integer.class) return FIELD_TYPE_INTEGER;
    else if (cf == String.class) return FIELD_TYPE_STRING;
    else if (cf == ListNode.class) return FIELD_TYPE_LIST_NODE;
    else return FIELD_TYPE_FIXED_NODE;
  }

  private static String getSQLFixedNodeFieldTypeFromTypeCode
    (int typeCode)
  {
    if (typeCode == FIELD_TYPE_INT) return "INTEGER";
    else if (typeCode == FIELD_TYPE_INTEGER) return "INTEGER";
    else if (typeCode == FIELD_TYPE_STRING) return "TEXT";
    else if (typeCode == FIELD_TYPE_FIXED_NODE) return "INTEGER";
    else if (typeCode == FIELD_TYPE_LIST_NODE) return "INTEGER";
    return null; //Should not happen.
  }

// Lower level tool methods.
//////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////
// Lower level SQL methods.
  //Returns null if nothing found.
  private Object readSingleValue(String table,
                                 String field,
                                 String wherePart,
                                 Object... parameters)
  throws Exception
  {
    PreparedStatement st = getPrepStatement
      ("SELECT "+field+" FROM "+table+" WHERE "+wherePart,
       parameters);

    ResultSet rs = st.executeQuery();

    Object obj = null;
    boolean found = rs.next();
    if (found)
      obj = rs.getObject(1);
    st.close();
    return obj;
  }


  private void updateSingleValue(String table,
                                 String field,
                                 String wherePart,
                                 Object... parameters)
  throws Exception
  {
    executeStatement
      ("UPDATE "+table+" SET "+field+"=? WHERE "+wherePart,
        parameters);
  }


  private int doInsertReturnPrimaryKey(String str,
                                       Object... parameters)
  throws Exception
  {
    PreparedStatement st = getPrepStatement(str, parameters);
    st.executeUpdate();
    ResultSet rs = st.getGeneratedKeys();
    rs.next();
    int id = rs.getInt(1);
    rs.close();
    st.close();
    return id;
  }

  private void executeDelete(String table,
                             String wherePart,
                             Object... parameters)
  throws Exception
  {
    executeStatement
      ("DELETE FROM "+table+" WHERE "+wherePart, parameters);
  }

  private void executeStatement(String str, Object... parameters)
  throws Exception
  {
    PreparedStatement st = getPrepStatement(str, parameters);
    st.executeUpdate();
    st.close();
  }

  private PreparedStatement getPrepStatement(String str,
                                             Object... parameters)
  throws Exception
  {
    PreparedStatement st = connection.prepareStatement(str);
    for(int i=0; i<parameters.length; ++i)
      st.setObject(i+1,parameters[i]);
    return st;
  }

  static private void p(Object o)
  {
    System.out.println(""+o);
  }
// Lower level SQL methods.
//////////////////////////////////////////////////////////////////
}