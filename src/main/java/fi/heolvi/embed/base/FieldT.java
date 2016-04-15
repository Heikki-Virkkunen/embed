/*
 * Copyright (c) 2016 Heikki Virkkunen.
 * Date: 5 March 2016
*/

package fi.heolvi.embed.base;

import java.lang.reflect.*;

//This is an abstraction for a field of a node.
//Used for fixed nodes and list nodes.
//Used for run-time nodes and nodes in the object database.
class FieldT
{
  int typeCode;  //Typecode of a field. Defines type of data in
                 //a field.
  Object field;  //For a fixed node java.lang.reflect.Field.
                 //For a list node position (integer 0,1,..) in
                 //a list.

  Object value;  //Value in a field.

  FieldT(Field f, int typeCode, Object value)
  {
    this.typeCode = typeCode;
    this.field = f;
    this.value = value;
  }

  FieldT(Integer pos, int typeCode, Object value)
  {
    this.typeCode = typeCode;
    this.field = pos;
    this.value = value;
  }

  public String toString()
  {
    return
    "FieldT"

    +"["

    +"field="+field

    +","
    +"isFixedNode="+typeCode

    +","
    +"value="+value

    +"]"
    ;
  }

}