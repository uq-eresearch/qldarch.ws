package net.qldarch.web.resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.DefaultPasswordService;
import org.apache.shiro.subject.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/user")
public class UserResource {
    public static Logger logger = LoggerFactory.getLogger(UserResource.class);

    @DELETE
    @Produces("application/json")
    public Response deleteNewUser(
    		@DefaultValue("") @QueryParam("username") String username) {  
    	if (username == null || username.trim().equals("") ) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mandatory field missing")
                    .build();
    	}
    	
        Subject currentUser = SecurityUtils.getSubject();
        if (currentUser == null || currentUser.getPrincipal() == null 
        		|| !((String)currentUser.getPrincipal()).equals("admin")) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    			
		Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
             
            stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM users " 
            		         + "WHERE username='" + username + "';");
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
        
        return Response.status(Response.Status.OK).build();
    }
    
    @PUT
    @Produces("application/json")
    public Response updateNewUser(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("confirmPassword") String confirmPassword,
            @FormParam("email") String email) {  
    	if (username == null || password == null || 
    			confirmPassword == null || email == null || 
    			username.trim().equals("") || password.trim().equals("") || 
    			confirmPassword.trim().equals("") || email.trim().equals("")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mandatory field missing")
                    .build();
    	}
    	if (!password.equals(confirmPassword)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Passwords do not match")
                    .build();
    	}
    	
        Subject currentUser = SecurityUtils.getSubject();
        if (currentUser == null || currentUser.getPrincipal() == null 
        		|| (!((String)currentUser.getPrincipal()).equals("admin") 
        				&&!((String)currentUser.getPrincipal()).equals(username))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    	
		DefaultPasswordService ps = new DefaultPasswordService();
		String encodedPassword = ps.encryptPassword(password);
		
		Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
             
            stmt = connection.createStatement();
            stmt.executeUpdate("UPDATE users " 
            		         + "SET email='" + email + "',"
                             + "    password='" + encodedPassword + "'" 
            		         + "WHERE username='" + username + "';");
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
        
        return Response.status(Response.Status.OK).build();
    }
    
    @POST
    @Produces("application/json")
    public Response createNewUser(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("confirmPassword") String confirmPassword,
            @FormParam("email") String email) {     
    	if (username == null || password == null || 
    			confirmPassword == null || email == null || 
    			username.trim().equals("") || password.trim().equals("") || 
    			confirmPassword.trim().equals("") || email.trim().equals("")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mandatory field missing")
                    .build();
    	}
    	if (!password.equals(confirmPassword)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Passwords do not match")
                    .build();
    	}
    	
		DefaultPasswordService ps = new DefaultPasswordService();
		String encodedPassword = ps.encryptPassword(password);
		
		Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
			
            stmt = connection.createStatement();
	    	ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE username = '" + username + "'");
	    	while (rs.next()) {
	            return Response.status(Response.Status.BAD_REQUEST)
	                    .entity("Username not available.")
	                    .build();
	    	}
	    	rs.close();
	    	stmt.close();
             
            stmt = connection.createStatement();
            stmt.execute("INSERT INTO users (username,email,verified,password) "
                                + "VALUES ('" + username + "','" + email + "','0','" + encodedPassword + "')");
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
		
        ObjectNode on = JsonNodeFactory.instance.objectNode();

        try {
            UsernamePasswordToken token =
                new UsernamePasswordToken(username, password.toCharArray());
            token.setRememberMe(false);
            Subject currentUser = SecurityUtils.getSubject();
            currentUser.login(token);
            logger.trace("Successful authentication for {}", username);

            on.put("user", username);
            on.put("auth", true);
            on.put("email", email);

            return Response.ok()
                .entity(on.toString())
                .build();
        } catch (AuthenticationException ea) {
            logger.debug("Failed authentication for {}", username);
        }
        
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
