package net.qldarch.web.resource;

import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.util.SparqlToJsonString;
import net.qldarch.web.util.SparqlToString;

import org.codehaus.jackson.map.ObjectMapper;
import org.apache.shiro.SecurityUtils;
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

@Path("/compoundObject")
public class CompoundObjectResource {
    public static Logger logger = LoggerFactory.getLogger(CompoundObjectResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String SHARED_COMPOUND_OBJECT_GRAPH = "http://qldarch.net/rdf/2013-09/compoundObjects";

    private RdfDataStoreDao rdfDao;

    @GET
    @Produces("application/json")
    public Response performGet() {
        logger.debug("Querying for compound objects: {}");
     
        String result = new SparqlToJsonString().performQuery(formatQuery());
        
        return Response.ok()
            .entity(result)
            .build();
    }
    
    public static String formatQuery() {
        String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?s ?p ?o " +
                "where {" +
                "  {" + 
                "    ?user <http://qldarch.net/ns/rdf/2013-09/catalog#hasCompoundObjectGraph> ?g." +
                "  }" +
                "  GRAPH ?g {" +
                "    ?s ?p ?o." +
                "  }" + 
                "}").toString();

        logger.debug("CompoundObjectResource performing SPARQL query: {}", query);
        
        return query;
    }
    
    @GET
    @Path("id")
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

        logger.debug("Querying compound objects for: {}", id);
     
        String result = new SparqlToString().performQuery(
        		formatQueryById(id));
        
        return Response.ok()
            .entity(result)
            .build();
    }
    
    public static String formatQueryById(String id) {
        String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?r " +
                "where {" + 
                "  BIND ( <" + id + "> AS ?s ) ." + 
                "  ?s <http://qldarch.net/ns/rdf/2012-06/terms#jsonData> ?r." +
                "}").toString();

        logger.debug("CompoundObject performing SPARQL query: {}", query);
        
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

        logger.debug("Querying for compound objects from user: {}", username);
     
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
                "    ?user <http://qldarch.net/ns/rdf/2013-09/catalog#hasCompoundObjectGraph> ?g." +
                "  }" +
                "  GRAPH ?g {" +
                "    ?s ?p ?o." +
                "  }" + 
                "}").toString();

        logger.debug("CompoundObjectResource performing SPARQL query: {}", query);
        
        return query;
    }
        
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCompoundObject(@DefaultValue("") @QueryParam("ID") String id,
                              String json) throws IOException {
        User user = User.currentUser();
        
        if (!SecurityUtils.getSubject().isPermitted("compoundObject:update")
        		&& !user.isOwner(id)) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        URI userCompoundObjectGraph = user.getCompoundObjectGraph();
        RdfDescription rdf = new RdfDescription();
        
        try {        	
            rdf.setURI(new URI(id));
            rdf.addProperty("qldarch:jsonData", json);
            
            // Generate and Perform insertRdfDescription query
            this.getRdfDao().updateRdfDescription(rdf, userCompoundObjectGraph);
            
            String type = new SparqlToString().performQuery(
            		formatQueryTypeById(id));
            rdf.addProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", type);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing updateCompoundObject rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        } catch (URISyntaxException em) {
            logger.warn("Error performing updateCompoundObject rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
		}
        
        String compoundObject = new ObjectMapper().writeValueAsString(rdf);
        
        logger.trace("Returning successful compoundObject: {}", compoundObject);
        // Return
        return Response.created(rdf.getURI())
            .entity(compoundObject)
            .build();
    }
    
    public static String formatQueryTypeById(String id) {
    	String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?r " +
                "where {" + 
                "  BIND ( <" + id + "> AS ?s ) ." + 
                "  ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?r." +
                "}").toString();

        logger.debug("CompoundObject performing SPARQL query: {}", query);
        
        return query;
    }
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addCompoundObject(String json,
            @DefaultValue("http://qldarch.net/ns/rdf/2012-06/terms#CompoundObject") 
    			@QueryParam("type") String type) throws IOException {
        if (!SecurityUtils.getSubject().isPermitted("compoundObject:create")) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
    	
        User user = User.currentUser();

        URI userCompoundObjectGraph = user.getCompoundObjectGraph();
        RdfDescription rdf = new RdfDescription();
        
        try {
        	URI typeURI = new URI(type);
        	
            URI relId = user.newId(userCompoundObjectGraph, typeURI);

            rdf.setURI(relId);
            rdf.addProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", typeURI);
            rdf.addProperty("qldarch:jsonData", json);
            
            // Generate and Perform insertRdfDescription query
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_COMPOUND_OBJECT_GRAPH, userCompoundObjectGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertCompoundObject rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        } catch (URISyntaxException em) {
            logger.warn("Error performing insertCompoundObject rdf:{})", rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
		}

        String compoundObject = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful compoundObject: {}", compoundObject);
        // Return
        return Response.created(rdf.getURI())
            .entity(compoundObject)
            .build();
    }
    
    @DELETE
    public Response deleteCompoundObject(@DefaultValue("") @QueryParam("ID") String id) {
    	User user = User.currentUser();
    	if (!SecurityUtils.getSubject().isPermitted("compoundObject:delete")
        		&& !user.isOwner(id)) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
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
            .entity(String.format("CompoundObject %s deleted", id))
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
