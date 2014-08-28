package net.qldarch.web.resource;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import net.qldarch.web.shiro.ShiroDb;
import net.qldarch.web.shiro.ShiroUser;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/login")
public class LoginResource {

  public static Logger logger = LoggerFactory.getLogger(LoginResource.class);

  private ShiroUser user(String username) {
    return username != null?new ShiroDb().get(username):null;
  }

  private ShiroUser user(Object principal) {
    return principal != null?user(principal.toString()):null;
  }

  private ObjectNode init() {
    ObjectNode on = JsonNodeFactory.instance.objectNode();
    on.put("user", "");
    on.put("auth", false);
    return on;
  }

  private ObjectNode init(ShiroUser su) {
    if(su != null) {
      ObjectNode on = JsonNodeFactory.instance.objectNode();
      on.put("user", su.getUsername());
      on.put("auth", true);
      on.put("email", su.getEmail());
      on.put("role", su.getRole());
      return on;
    } else {
      return init();
    }
  }

  @GET
  @Produces("application/json")
  @Path("/status")
  public String getLogin() {
    Subject currentUser = SecurityUtils.getSubject();
    Object principal = currentUser.getPrincipal();
    logger.trace("Checking current login status: " + principal);
    ShiroUser su = user(principal);
    return init(su).toString();
  }

  @POST
  @Produces("application/json")
  public Response login(
      @FormParam("username") String username,
      @FormParam("password") String password) {
    logger.info("Login attempt received for username={}", username);
    try {
      UsernamePasswordToken token =
          new UsernamePasswordToken(username, password.toCharArray());
      token.setRememberMe(false);
      Subject currentUser = SecurityUtils.getSubject();
      currentUser.login(token);
      ShiroUser su = user(username);
      ObjectNode on = init(su);
      if (on.get("role").toString().contains(UserResource.ROLE_DISABLED)) {
        logger.trace("Authentication for {} failed. Account disabled.", username);
        currentUser.logout();
        return Response.status(Response.Status.FORBIDDEN).entity(
            "Account " + username + " disabled.").build();
      } else {
        logger.trace("Successful authentication for {}", username);
        return Response.ok().entity(on.toString()).build();
      }
    } catch(Exception e) {
      logger.debug("Failed authentication for {}", username);
      return Response.status(Response.Status.FORBIDDEN).entity(init().toString()).build();
    }
  }
}
