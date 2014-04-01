package net.qldarch.web.resource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.qldarch.web.util.SparqlToJsonString;

import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
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

    public static final String INDENT1 = "    ";
    public static final String INDENT2 = INDENT1 + INDENT1;
    public static final String INDENT3 = INDENT2 + INDENT1;
    public static final String ROLE_AUTHORIZED = "authorized";
    public static final String ROLE_DISABLED = "disabled";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_ROOT = "root";

    @GET
    @Produces("application/json")
    public Response getAllUsers() {
        if (!SecurityUtils.getSubject().isPermitted("user:list")) {
        	return Response.status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        logger.debug("Querying for all users: {}");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("{");
        
        Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
			
            stmt = connection.createStatement();
	    	ResultSet rs = stmt.executeQuery("SELECT users.username, users.email, user_roles.role_name  "
	    			+ "FROM users, user_roles "
	    			+ "WHERE user_roles.username = users.username;");
	    	while (rs.next()) {
	    		if (!rs.isFirst()) {
                    pw.println(",");
	    		}
	    		String username = rs.getString("username");
	    		String email = rs.getString("email");
	    		String role = rs.getString("role_name");
	    		
	    		pw.println(INDENT1 + "\"http://qldarch.net/users/" + username + "\": {");
                pw.println(INDENT2 + "\"username\": \"" + username + "\",");
                pw.println(INDENT2 + "\"email\": \"" + email + "\",");
                pw.println(INDENT2 + "\"role\": \"" + role + "\"");
	    		pw.print(INDENT1 + "}");
	    	}
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

        pw.println("\n}");
		pw.flush();
        return Response.ok()
            .entity(sw.toString())
            .build();
    }
    
    @DELETE
    @Produces("application/json")
    public Response deleteNewUser(
    		@DefaultValue("") @QueryParam("username") String username) {
    	if (!SecurityUtils.getSubject().isPermitted("user:delete")) {
        	return Response.status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
    	
    	if (username == null || username.trim().equals("") ) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mandatory field missing")
                    .build();
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
    @Path("role")
    public Response updateUserRole(
            @FormParam("username") String username,
            @FormParam("role") String role) {  
        if (!SecurityUtils.getSubject().isPermitted("user:update")) {
        	return Response.status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
    	
    	if (username == null || role == null || 
    			username.trim().equals("") || role.trim().equals("")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Mandatory field missing")
                    .build();
    	}
    	if (!(role.equals(ROLE_AUTHORIZED) || role.equals(ROLE_EDITOR) || 
    			role.equals(ROLE_ROOT) || role.equals(ROLE_DISABLED))) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid Role")
                    .build();
    	}
		
		setUserRole(username, role);
        
		ObjectNode on = JsonNodeFactory.instance.objectNode();
        on.put("user", username);
        
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
		
        return Response.status(Response.Status.OK).entity(on.toString()).build();
    }
    
    @PUT
    @Produces("application/json")
    public Response updateUser(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("confirmPassword") String confirmPassword,
            @FormParam("email") String email) {  
    	Subject currentUser = SecurityUtils.getSubject();
        if (!SecurityUtils.getSubject().isPermitted("user:update") && 
        		!((String)currentUser.getPrincipal()).equals(username)) {
        	return Response.status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
    	
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
    
    private void setUserRole(String username, String role) {
    	Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
             
            stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM user_roles WHERE username='" + username + "';");
            stmt.execute("INSERT INTO user_roles (username, role_name) "
                    + "VALUES ('" + username + "','" + role  + "');");
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
            setUserRole(username, ROLE_AUTHORIZED);
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
