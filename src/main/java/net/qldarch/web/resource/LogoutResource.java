package net.qldarch.web.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/logout")
public class LogoutResource {
    public static Logger logger = LoggerFactory.getLogger(LogoutResource.class);
    
    @POST
    @Produces("application/json")
    public String logout() {
        Subject currentUser = SecurityUtils.getSubject();
        Object principal = currentUser.getPrincipal();
        currentUser.logout();
        logger.info("Logged out: ?", principal);

        ObjectNode on = JsonNodeFactory.instance.objectNode();
        on.put("user", "");
        on.put("auth", false);
        
        return on.toString();
    }

    @GET
    @Produces("application/json")
    @Path("/viaget")
    public String browser() {
        Subject currentUser = SecurityUtils.getSubject();
        Object principal = currentUser.getPrincipal();
        currentUser.logout();
        logger.info("Logged out via GET: ?", principal);

        ObjectNode on = JsonNodeFactory.instance.objectNode();
        on.put("user", "");
        on.put("auth", false);
        
        return on.toString();
    }

}
