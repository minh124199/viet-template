package io.github.minh124199.viettemplate.tck.velocity.model;

/** JavaBean representing an address. */
public class AddressBean {
  private String city;
  private String zip;

  public AddressBean() {}

  public AddressBean(String city, String zip) {
    this.city = city;
    this.zip = zip;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getZip() {
    return zip;
  }

  public void setZip(String zip) {
    this.zip = zip;
  }
}
