/*
 * Copyright (c) 2016 Heikki Virkkunen.
 * Date: 5 April 2016
*/

package fi.heolvi.embed.base;

import java.util.ArrayList;
import java.lang.reflect.*;

public class ListNode
{

  //Id of a list node. If id==0 the list node does not
  //have a corresponding node in the database.
  //If id>0 the list node has a corresponding node in the
  //database identified by the id.
  public int id;

  //ArrayList containing items of list.
  public ArrayList<Object> list = new ArrayList<Object>();

  //Default constructor. The database uses
  //this constructor to create a corresponding Java object
  //when a list node is loaded from the database with the
  //search method.
  public ListNode(){}

  //Helping wrapper methods for ArrayList.
  public void add(Object o)
  {
    list.add(o);
  }
  public Object get(int index)
  {
    return list.get(index);
  }
  public Object remove(int index)
  {
    return list.remove(index);
  }
  public Object set(int index, Object element)
  {
    return list.set(index,element);
  }

  public String toString()
  {
    if (list==null)
      return "null";

    String s = "";
    s+="[";
    int i = 0;
    for(Object o:list)
    {
      if (i>0)
        s += ",";
      ++i;

      if (o==null)
        s+="null";
      else if (o.getClass() == Integer.class  || o.getClass() == Float.class)
        s+=o;
      else if (o.getClass() == String.class)
        s+=o;
      else if (o.getClass() == ListNode.class)
        s += "size=" + ((ListNode)o).list.size();
      else
      {
       String name = null;
       try
       {
         Field f = o.getClass().getField("name");
         name = (String) f.get(o);
       }
       catch (Exception e)
       {
       }
       s += name;
      }


    }//for
    s += "]";

    return s;
  }
}