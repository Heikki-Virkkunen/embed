import java.util.ArrayList;

import fi.heolvi.embed.base.*;

public class Friend
{
  public int id;

  public String name;
  public Integer age;

  public Book book;

  public Friend fr1;

  public Friend fr2;

  public Friend fr3;

  public ListNode list;


  //Default costructor required.
  public Friend(){}


  public Friend(String name, int age){this.name = name; this.age = age;}

  public String toString()
  {
    return
    "("

    +"id=" + id

    +",name=" + name

    +",age=" + age

    +",fr1=" + (fr1==null?"null":fr1.name)

    +",fr2=" + (fr2==null?"null":fr2.name)

    +",fr3=" + (fr3==null?"null":fr3.name)

    + ")";
  }

}