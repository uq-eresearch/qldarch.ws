package net.qldarch.web.service;

import javax.ws.rs.GET;
import javax.ws.rs.FormParam;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/login")
public class LoginResource {
    public static Logger logger = LoggerFactory.getLogger(LoginResource.class);
    
    @GET
    @Produces("application/json")
    @Path("/status")
    public String getLogin() {
        Subject currentUser = SecurityUtils.getSubject();
        Object principal = currentUser.getPrincipal();
        logger.trace("Checking current login status: " + principal);

        ObjectNode on = JsonNodeFactory.instance.objectNode();
        if (principal != null) {
            on.put("user", principal.toString());
            on.put("auth", true);
        } else {
            on.put("user", "");
            on.put("auth", false);
        }
        return on.toString();
    }

    @POST
    @Produces("application/json")
    public Response login(
            @FormParam("username") String username,
            @FormParam("password") String password) {
        logger.info("Login attempt received for username={}", username);
        ObjectNode on = JsonNodeFactory.instance.objectNode();

        try {
            if (username != null && password != null) {
                UsernamePasswordToken token =
                    new UsernamePasswordToken(username, password.toCharArray());
                token.setRememberMe(false);
                Subject currentUser = SecurityUtils.getSubject();
                currentUser.login(token);
                logger.trace("Successful authentication for {}", username);

                on.put("user", username);
                on.put("auth", true);

                return Response.ok()
                    .entity(on.toString())
                    .build();
            }
        } catch (AuthenticationException ea) {
            logger.debug("Failed authentication for {}", username);
        }

        on.put("user", "");
        on.put("auth", false);
        return Response.status(Response.Status.FORBIDDEN)
            .entity(on.toString())
            .build();
    }
}
