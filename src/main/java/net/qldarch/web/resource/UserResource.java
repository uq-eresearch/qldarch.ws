package net.qldarch.web.resource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.credential.DefaultPasswordService;
import org.apache.shiro.subject.Subject;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO refactor: use ShiroDb 
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

    private String AB = "123456789ABCDEFGHIJKLMNPQRSTWXYZ";
	private Random rnd = new Random();
    
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
    
    @GET
    @Path("activation")
    @Produces("text/html")
    public Response getAllUsers(
    		@DefaultValue("") @QueryParam("code") String confirmationString) {
    	if (confirmationString.isEmpty()) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
            		.entity("Invalid or Expired code given.").build();
    	}
    	Connection connection = null;
		Statement stmt = null;
		String username = "";
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
			
            stmt = connection.createStatement();
            logger.debug("Database query: SELECT username " +
                    "FROM users " +
                    "WHERE confirmationString = \'" + confirmationString + "\'" + 
                    "AND date_created > (NOW() - INTERVAL 30 MINUTE);");
	    	ResultSet rs = stmt.executeQuery(
	    			"SELECT username " +
                    "FROM users " +
                    "WHERE confirmationString = \'" + confirmationString + "\'" + 
                    "AND date_created > (NOW() - INTERVAL 30 MINUTE);"
	    	);
	    	while (rs.next()) {
	    		username = rs.getString("username");
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
		
		if (username.isEmpty()) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
            		.entity("Invalid or Expired code given.").build();
		}
		
		setUserRole(username, ROLE_AUTHORIZED);
    	
		connection = null;
		stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
             
            stmt = connection.createStatement();
            logger.debug("Database query: UPDATE users " 
   		         + "SET confirmationString='' " 
   		         + "WHERE username='" + username + "';");
            stmt.executeUpdate("UPDATE users " 
            		         + "SET confirmationString='' " 
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
				
        return Response.status(Response.Status.OK).entity("Account " + username + " activated.\n\n"
        		+ " Click <a href=\"/beta/\">here</a> to return to the homepage").build();
    }
    
    @GET
    @Path("forgotPassword")
    @Produces("application/json")
    public Response confirmEmailAddress(
    		@DefaultValue("") @QueryParam("username") String username) {
    	
    	Connection connection = null;
		Statement stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
			
            stmt = connection.createStatement();
            logger.debug("Database query: SELECT * FROM users WHERE username = '" + username + "'");
	    	ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE username = '" + username + "'");
	    	boolean accountFound = false;
	    	while (rs.next()) {
	    		accountFound = true;
	    	}
	    	rs.close();
	    	stmt.close();
	    	
	    	if (!accountFound) {
	            return Response.status(Response.Status.BAD_REQUEST)
	                    .entity("No account with given address found.")
	                    .build();
	    	}
             
			StringBuilder sb = new StringBuilder(30);
			for( int i = 0; i < 30; i++ ) {
		      sb.append(AB.charAt(rnd.nextInt(AB.length())));
			}
			String confirmationString = sb.toString();
	    	
            stmt = connection.createStatement();
            logger.debug("Database query: UPDATE users SET passwordConfirmationString = '" + confirmationString + "'"
            		+ ",passwordRequested=current_timestamp"
                    + " WHERE username='" + username + "'");
            stmt.execute("UPDATE users SET passwordConfirmationString = '" + confirmationString + "'"
            		+ ",passwordRequested=current_timestamp"
                    + " WHERE username='" + username + "'");
                        
    		try {
	    		String host = "smtp.uq.edu.au";    
	        	String to = username;
	        	String from = "no-reply@qldarch.net";   
	        	Properties properties = System.getProperties();  
	        	properties.setProperty("mail.smtp.host", host);  
	        
	        	Session session = Session.getDefaultInstance(properties);  
	        	MimeMessage message = new MimeMessage(session);  
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
	        	message.setFrom(new InternetAddress(from));
	
	        	message.setSubject("Qldarch Password Reset");  
	        	message.setContent("Click <a href=\"http://qldarch.net/ws/rest/user/resetPassword?code=" 
	        			+ confirmationString + "\">here</a> to reset the password for account " + username + ".", "text/html; charset=utf-8");
	        	Transport.send(message);  
	    	} catch (Exception e) {
				e.printStackTrace();
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
		
	    return Response.ok()
	        .entity("Confirmation email sent to " + username)
	        .build();
    }
    		
    @GET
    @Path("resetPassword")
    @Produces("text/html")
    public Response resetPassword(
    		@DefaultValue("") @QueryParam("code") String confirmationString) {
    	if (confirmationString.isEmpty()) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
            		.entity("Invalid or Expired code given.").build();
    	}
    	Connection connection = null;
		Statement stmt = null;
		String username = "";
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
			
            stmt = connection.createStatement();
            logger.debug("Database query: SELECT username " +
                    "FROM users " +
                    "WHERE passwordConfirmationString = \'" + confirmationString + "\'" + 
                    "AND passwordRequested > (NOW() - INTERVAL 30 MINUTE);");
	    	ResultSet rs = stmt.executeQuery(
	    			"SELECT username " +
                    "FROM users " +
                    "WHERE passwordConfirmationString = \'" + confirmationString + "\'" + 
                    "AND passwordRequested > (NOW() - INTERVAL 30 MINUTE);"
	    	);
	    	while (rs.next()) {
	    		username = rs.getString("username");
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
		
		if (username.isEmpty()) {
            return Response.status(Response.Status.NOT_ACCEPTABLE)
            		.entity("Invalid or Expired code given.").build();
		}
		
		StringBuilder sb = new StringBuilder(30);
		for( int i = 0; i < 8; i++ ) {
	      sb.append(AB.charAt(rnd.nextInt(AB.length())));
		}
		String password = sb.toString();
				
		DefaultPasswordService ps = new DefaultPasswordService();
		String encodedPassword = ps.encryptPassword(password);
		    	
		connection = null;
		stmt = null;
		try
        {
			Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager
                .getConnection("jdbc:mysql://localhost:3306/UserDB?autoReconnect=true", "auth", "tmppassword");
             
            stmt = connection.createStatement();
            logger.debug("Database query: UPDATE users " 
   		         + "SET password='" + encodedPassword + "' " 
   		         + "WHERE username='" + username + "';");
            stmt.executeUpdate("UPDATE users " 
            		         + "SET password='" + encodedPassword + "' " 
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
			
  
		try {
    		String host = "smtp.uq.edu.au";    
        	String to = username;
        	String from = "no-reply@qldarch.net";   
        	Properties properties = System.getProperties();  
        	properties.setProperty("mail.smtp.host", host);  
        
        	Session session = Session.getDefaultInstance(properties);  
        	MimeMessage message = new MimeMessage(session);  
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        	message.setFrom(new InternetAddress(from));

        	message.setSubject("Qldarch Password Reset");  
        	message.setContent("New password for account " + username + " is " + password + ".", "text/html; charset=utf-8");
        	Transport.send(message);  
    	} catch (Exception e) {
			e.printStackTrace();
		}
	
        return Response.status(Response.Status.OK).entity("New password emailed.").build();
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
             
			StringBuilder sb = new StringBuilder(30);
			for( int i = 0; i < 30; i++ ) {
		      sb.append(AB.charAt(rnd.nextInt(AB.length())));
			}
			String confirmationString = sb.toString();
	    	
            stmt = connection.createStatement();
            stmt.execute("INSERT INTO users (username,email,verified,password,confirmationString) "
                                + "VALUES ('" + username + "','" + email + "','0','" 
                                	+ encodedPassword + "','" + confirmationString + "')");
            setUserRole(username, ROLE_DISABLED);
                        
    		try {
	    		String host = "smtp.uq.edu.au";    
	        	String to = username;
	        	String from = "no-reply@qldarch.net";   
	        	Properties properties = System.getProperties();  
	        	properties.setProperty("mail.smtp.host", host);  
	        
	        	Session session = Session.getDefaultInstance(properties);  
	        	MimeMessage message = new MimeMessage(session);  
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
	        	message.setFrom(new InternetAddress(from));
	
	        	message.setSubject("Qldarch Account Activation");  
	        	message.setContent("Click <a href=\"http://qldarch.net/ws/rest/user/activation?code=" 
	        			+ confirmationString + "\">here</a> to activate your account.", "text/html; charset=utf-8");
	        	Transport.send(message);  
	    	} catch (Exception e) {
				e.printStackTrace();
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
		

	    return Response.ok()
	        .entity("Activation email sent to " + username)
	        .build();
    }
}