package net.qldarch.web.resource;

import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.util.SparqlToJsonString;
import net.qldarch.web.util.SparqlToString;

import org.codehaus.jackson.map.ObjectMapper;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import static net.qldarch.web.service.KnownURIs.*;

@Path("/timeline")
public class TimelineResource {
    public static Logger logger = LoggerFactory.getLogger(TimelineResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String SHARED_TIMELINE_GRAPH = "http://qldarch.net/rdf/2013-09/timelines";

    private RdfDataStoreDao rdfDao;

    @GET
    @Produces("application/json")
    public Response performGet(
            @DefaultValue("") @QueryParam("ID") String id) {

        if (id.isEmpty()) {
            logger.info("Bad request received. No resource id provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam ID missing")
                .build();
        }

        logger.debug("Querying timelines for resource: {}", id);
     
        String result = new SparqlToString().performQuery(
        		formatQuery(id));
        // Remove leading and end quote
        result = result.substring(1, result.length()-1);
        
        return Response.ok()
            .entity(result)
            .build();
    }
    
    public static String formatQuery(String id) {
        String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?r " +
                "where {" + 
                "  BIND ( <" + id + "> AS ?s ) ." + 
                "  ?s <http://qldarch.net/ns/rdf/2012-06/terms#jsonData> ?r." +
                "}").toString();

        logger.debug("TimelineResource performing SPARQL query: {}", query);
        
        return query;
    }
    
    @GET
    @Path("user")
    @Produces("application/json")
    public Response queryByUser(
            @DefaultValue("") @QueryParam("username") String username) {

        if (username.isEmpty()) {
            logger.info("Bad request received. No username provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam username missing")
                .build();
        }

        logger.debug("Querying timelines from user: {}", username);
     
        String result = new SparqlToJsonString().performQuery(formatQueryFromUser(username));
        
        return Response.ok()
            .entity(result)
            .build();
    }
    
    public static String formatQueryFromUser(String username) {
        String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?s ?p ?o " +
                "where {" +
                "  {" + 
                "    BIND ( <http://qldarch.net/users/" + username + "> AS ?user ) ." + 
                "    ?user <http://qldarch.net/ns/rdf/2013-09/catalog#hasTimelineGraph> ?g." +
                "  }" +
                "  GRAPH ?g {" +
                "    ?s ?p ?o." +
                "  }" + 
                "}").toString();

        logger.debug("TimelineResource performing SPARQL query: {}", query);
        
        return query;
    }
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresPermissions("create:timeline")
    public Response addTimeline(String json) throws IOException {
        // Check User Auth
        User user = User.currentUser();

        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .type(MediaType.TEXT_PLAIN)
                .entity("Anonymous users are not permitted to create timelines")
                .build();
        }

        URI userTimelineGraph = user.getTimelineGraph();
        RdfDescription rdf = new RdfDescription();
        
        try {
        	URI type = new URI("http://qldarch.net/ns/rdf/2012-06/terms#Timeline");
        	
            URI relId = user.newId(userTimelineGraph, type);

            rdf.setURI(relId);
            rdf.addProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
            rdf.addProperty("qldarch:jsonData", json);
            
            // Generate and Perform insertRdfDescription query
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_TIMELINE_GRAPH, userTimelineGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertTimeline rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        } catch (URISyntaxException em) {
            logger.warn("Error performing insertTimeline rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
		}

        String timeline = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful timeline: {}", timeline);
        // Return
        return Response.created(rdf.getURI())
            .entity(timeline)
            .build();
    }
    
    @DELETE
    @RequiresPermissions("delete:timeline")
    public Response deleteEvidence(@DefaultValue("") @QueryParam("ID") String id) {
    	try {
			this.getRdfDao().deleteRdfResource(new URI(id));
		} catch (MetadataRepositoryException e) {
			e.printStackTrace();
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error performing delete")
                    .build();
		} catch (URISyntaxException e) {
			e.printStackTrace();
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error performing delete")
                    .build();
		}

        return Response
            .status(Status.ACCEPTED)
            .type(MediaType.TEXT_PLAIN)
            .entity(String.format("Timeline %s deleted", id))
            .build();
    }
    
    public void setRdfDao(RdfDataStoreDao rdfDao) {
        this.rdfDao = rdfDao;
    }

    public RdfDataStoreDao getRdfDao() {
        if (this.rdfDao == null) {
            this.rdfDao = new RdfDataStoreDao();
        }
        return this.rdfDao;
    }
}
