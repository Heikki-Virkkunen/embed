public class Book
{
  public int id;

  public String name;
  public int price;

  //Default costructor required.
  public Book(){}

  public Book(String name, int price)
  {
    this.name = name;
    this.price = price;
  }

  public String toString()
  {
    return
    "("

    +"id="+id

    +",name=" + name

    +",price=" + price

    +")"
    ;
  }
}