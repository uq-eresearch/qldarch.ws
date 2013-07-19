package net.qldarch.web.service;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

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
    public String getLogin() {
        Subject currentUser = SecurityUtils.getSubject();
        Principal principal = currentUser.getPrincipal();

        if (principal != null) {
            return "{ user: " + principal.toString() + ", auth: true }";
        } else {
            return "{ user: \"\", auth: false }";
        }
    }

    @POST
    @Produces("application/json")
    public String login(
            @QueryParam("username") String username,
            @QueryParam("password") String password) {
        logger.debug("Login attempt received for username={}", username);
        logger.trace("Login attempt received for password={}", password);
        try {
            if (username != null && password != null) {
                UsernamePasswordToken token =
                    new UsernamePasswordToken(username, password.toCharArray());
                token.setRememberMe(false);
                Subject currentUser = SecurityUtils.getSubject();
                currentUser.login(token);
                logger.trace("Successful authentication for {}", username);

                return "{ user: " + username + ", auth: true }";
            }
        } catch (AuthenticationException ea) {
            logger.debug("Failed authentication for {}", username);
            logger.trace("Failed authentication <{},{}>", username, password);
        }

        return "{ user: \"\", auth: false }";
    }
}
