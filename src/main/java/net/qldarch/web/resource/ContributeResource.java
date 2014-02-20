package net.qldarch.web.resource;

import javax.ws.rs.FormParam;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;

@Path("/contribute")
public class ContributeResource {
    public static Logger logger = LoggerFactory.getLogger(ContributeResource.class);
    
    @POST
    public Response contribute(
            @FormParam("firstName") String firstName,
            @FormParam("lastName") String lastName,
            @FormParam("email") String email,
            @FormParam("comment") String comment,
            @FormParam("sendMeNewsletter") String sendMeNewsletter) {
        logger.info("Contribution from " + email);

        try {
        	String host = "smtp.uq.edu.au";
        	String to = email;
        	String from = "no-reply@qldarch.net";   
        	Properties properties = System.getProperties();  
        	properties.setProperty("mail.smtp.host", host);  
        
        	Session session = Session.getDefaultInstance(properties);  
        	MimeMessage message = new MimeMessage(session);  
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        	message.setFrom(new InternetAddress(from));

        	message.setSubject("Contribution");  
        	message.setText(firstName + "\n" + lastName + "\n" +
                    email + "\n" + sendMeNewsletter + "\n" + comment);  
        	Transport.send(message);  
            logger.info("Contribution forwarded on.");
	        
	        return Response.ok().build();
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	        logger.info("Problem Sending Email", e);
	        return Response.serverError().build();
		}  
    }
}
