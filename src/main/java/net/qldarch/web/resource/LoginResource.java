package net.qldarch.web.resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

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
            
            Connection connection = null;
    		Statement stmt = null;
    		try
            {
    			Class.forName("com.mysql.jdbc.Driver");
                connection = DriverManager
                    .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
    			
                stmt = connection.createStatement();
    	    	ResultSet rs = stmt.executeQuery(
    	    			"SELECT users.email, user_roles.role_name " +
                        "FROM users, user_roles " +
                        "WHERE users.username = \'" + principal.toString() + "\' " +
                        "AND user_roles.username = users.username;"
    	    	);
    	    	while (rs.next()) {
    	            on.put("email", rs.getString("email"));
    	            on.put("role", rs.getString("role_name"));
    	    	}
    	    	rs.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    stmt.close();
                    connection.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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

                on.put("user", username);
                on.put("auth", true);

                Connection connection = null;
        		Statement stmt = null;
        		try
                {
        			Class.forName("com.mysql.jdbc.Driver");
                    connection = DriverManager
                        .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
        			
                    stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
        	    			"SELECT users.email, user_roles.role_name " +
                            "FROM users, user_roles " +
                            "WHERE users.username = \'" + username + "\' " +
                            "AND user_roles.username = users.username;"
        	    	);
        	    	while (rs.next()) {
        	            on.put("email", rs.getString("email"));
        	            on.put("role", rs.getString("role_name"));
        	    	}
        	    	rs.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        stmt.close();
                        connection.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
    	    	if (on.get("role").toString().contains(UserResource.ROLE_DISABLED)) {
                    logger.trace("Authentication for {} failed. Account disabled.", username);
        	    	currentUser.logout();
        	        return Response.status(Response.Status.FORBIDDEN)
        	            .entity("Account " + username + " disabled.")
        	            .build();
    	    	} else {
                    logger.trace("Successful authentication for {}", username);
    	    		return Response.ok()
    	    				.entity(on.toString())
    	    				.build();
    	    	}
            }
        } catch (AuthenticationException ea) {
            logger.debug("Failed authentication for {}", username);
        }

        on.put("user", "");
        on.put("auth", false);
        on.put("email", "");
        on.put("role", "");
        return Response.status(Response.Status.FORBIDDEN)
            .entity(on.toString())
            .build();
    }
}
