import fi.heolvi.embed.base.*;

public class People
{
  public int id;

  public String name;
  public Integer age;

  public People fr1;

  public People fr2;

  //Default costructor required.
  public People(){}

  public People(String name, int age){this.name = name; this.age = age;}

  public String toString()
  {
    return
    "("

    +"id=" + id

    +",name=" + name

    +",age=" + age

    +",fr1=" + (fr1==null?"null":fr1.name)

    +",fr2=" + (fr2==null?"null":fr2.name)

    + ")";
  }
}