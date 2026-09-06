package io.github.minh124199.viettemplate.tck.velocity.model;

/** JavaBean representing a person with nested address. */
public class PersonBean {
  private String name;
  private int age;
  private boolean active;
  private AddressBean address;

  public PersonBean() {}

  public PersonBean(String name, int age, boolean active, AddressBean address) {
    this.name = name;
    this.age = age;
    this.active = active;
    this.address = address;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getAge() {
    return age;
  }

  public void setAge(int age) {
    this.age = age;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public AddressBean getAddress() {
    return address;
  }

  public void setAddress(AddressBean address) {
    this.address = address;
  }

  public String sayHello(String recipient) {
    return "Hello, " + recipient + " from " + name;
  }
}
