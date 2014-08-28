package net.qldarch.web.shiro;

public class ShiroUser {

  private String username;

  private String email;

  private String role;

  public ShiroUser(String username, String email, String role) {
    super();
    this.username = username;
    this.email = email;
    this.role = role;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }
}
