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

@Path("/logout")
public class LogoutResource {
    public static Logger logger = LoggerFactory.getLogger(LogoutResource.class);
    
    @GET
    @Produces("application/json")
    public String logout() {
        Subject currentUser = SecurityUtils.getSubject();
        Object principal = currentUser.getPrincipal();
        currentUser.logout();
        logger.trace("Logged out ?", principal);

        return "{ user: \"\", auth: false }";
    }
}
