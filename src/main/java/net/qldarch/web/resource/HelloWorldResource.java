package net.qldarch.web.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/hello")
public class HelloWorldResource {
    public static Logger logger = LoggerFactory.getLogger(HelloWorldResource.class);
    
    public static String message = "Hello World!";

    @GET
    @Produces("text/plain")
    public String hello() {
        logger.warn("Returning {} from GET", message);
        logger.debug("Returning {} from GET at DEBUG", message);
        return message;
    }

}
