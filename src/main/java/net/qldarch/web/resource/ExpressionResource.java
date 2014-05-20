package net.qldarch.web.resource;

import net.qldarch.av.parser.TranscriptParser;
import net.qldarch.web.model.QldarchOntology;
import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.*;
import net.qldarch.web.util.Functions;
import net.qldarch.web.util.SolrIngest;
import net.qldarch.web.util.SparqlToJsonString;

import org.codehaus.jackson.map.ObjectMapper;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Multimap;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;
import static javax.ws.rs.core.Response.Status;
import static net.qldarch.web.service.KnownURIs.*;
import static net.qldarch.web.util.ResourceUtils.badRequest;
import static net.qldarch.web.util.ResourceUtils.forbidden;
import static net.qldarch.web.util.ResourceUtils.internalError;

/* FIXME: Consider refactor with EntitySummaryResource */

@Path("/expression")
public class ExpressionResource {
    public static Logger logger = LoggerFactory.getLogger(ExpressionResource.class);

    public static String USER_EXPRESSION_GRAPH_FORMAT = "http://qldarch.net/users/%s/expressions";

    private static final STGroupFile EXPRESSION_QUERIES = new STGroupFile("queries/Expressions.sparql.stg");
    
    private RdfDataStoreDao rdfDao;

    public static String queryByTypes(Collection<URI> types, boolean summary) {
        if (types.size() < 1) {
            throw new IllegalArgumentException("Empty type collection passed to queryByTypes()");
        }

        StringBuilder builder = new StringBuilder(
            "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " + 
            "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " + 
            "select distinct ?s ?p ?o where  {" + 
            "  {" + 
            "    graph <http://qldarch.net/rdf/2013-09/catalog> {" + 
            "      ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasExpressionGraph> ?g." + 
            "    } ." + 
            "  } UNION {" + 
            "    BIND ( <http://qldarch.net/ns/omeka-export/2013-02-06> AS ?g ) ." + 
            "  } ." + 
            "  graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" + 
            "    ?transType rdfs:subClassOf ?type ." + 
            "  } ." + 
            "  graph ?g {" + 
            "    ?s a ?transType ." + 
            "  } ." + 
            "  graph ?g {" + 
            "    ?s ?p ?o ." + 
            "  } ." + 
            "%s" +
            "} BINDINGS ?type { ( <");

        String summaryRestriction =
            "  graph <http://qldarch.net/ns/rdf/2012-06/terms#> {" + 
            "    ?p a :SummaryProperty ." + 
            "  } ";

        String baseQuery = Joiner.on(">) (<")
            .appendTo(builder, transform(types, toStringFunction()))
            .append(">) }")
            .toString();

        String query = String.format(baseQuery, (summary ? summaryRestriction : ""));

        logger.debug("ExpressionResource performing SPARQL query: {}", query);

        return query;
    }

    public static String prepareSearchQuery(String searchString, Collection<URI> types, boolean restrictType) {
        String query = EXPRESSION_QUERIES.getInstanceOf("searchByLabelIds")
                .add("searchString", searchString)
                .add("types", types)
                .add("restrictType", restrictType)
                .render();

        logger.debug("ExpressionSummaryResource performing SPARQL query: {}", query);

        return query;
    }

    public static String prepareSearchByUserQuery(URI userExpressionID) {
        String query = EXPRESSION_QUERIES.getInstanceOf("searchByUserId")
                .add("id", userExpressionID)
                .render();

        logger.debug("EntitySummaryResource performing SPARQL query: {}", query);

        return query;
    }
    
     @GET
     @Produces(MediaType.APPLICATION_JSON)
     @Path("search")
     public String search(
             @DefaultValue("") @QueryParam("searchString") String searchString,
             @DefaultValue("") @QueryParam("type") String type) {
         Set<String> typeStrs = newHashSet(
                 Splitter.on(',').trimResults().omitEmptyStrings().split(type));
         
         Collection<URI> typeURIs = transform(typeStrs, Functions.toResolvedURI());
         
         logger.debug("Querying search({},{},{})", searchString, typeURIs, !typeURIs.isEmpty());
         
         return new SparqlToJsonString().performQuery(
        		 prepareSearchQuery(searchString, typeURIs, !typeURIs.isEmpty()));
     }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("summary/{type}")
    public String summaryGet(
            @DefaultValue("") @PathParam("type") String type,
            @DefaultValue("") @QueryParam("TYPELIST") String typelist) {

        return findByType(type, typelist, true);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("user")
    public String search(
            @DefaultValue("") @QueryParam("ID") String id) {
    	User user = new User(id);
    	
    	URI userFileGraph = user.getExpressionGraph();
        
        return new SparqlToJsonString().performQuery(
        		prepareSearchByUserQuery(userFileGraph));
    }
    
    /**
     * Get detailed records for entity.
     *
     * Note: This is supposed to eventually take a filter query-param
     * that will allow this method to return a sub-graph of the entity/details
     * for this type.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("detail/{type}")
    public String detailGet(
            @DefaultValue("") @PathParam("type") String type,
            @DefaultValue("") @QueryParam("TYPELIST") String typelist) {

        return findByType(type, typelist, false);
    }

    public String findByType(String type, String typelist, boolean summary) {
        logger.debug("Querying summary({}) by type: {}, typelist: {}", summary, type, typelist);

        Set<String> typeStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(typelist));
        if (!type.isEmpty()) typeStrs.add(type);

        Collection<URI> typeURIs = transform(typeStrs, Functions.toResolvedURI());

        logger.debug("Raw types: {}", typeURIs);

        return new SparqlToJsonString().performQuery(queryByTypes(typeURIs, summary));
    }

    public static String queryByIds(Collection<URI> ids, boolean summary) {
        if (ids.size() < 1) {
            throw new IllegalArgumentException("Empty id collection passed to findEvidenceByIds()");
        }

        StringBuilder builder = new StringBuilder(
            "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " + 
            "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " + 
            "select distinct ?s ?p ?o where  {" + 
            "  {" + 
            "    graph <http://qldarch.net/rdf/2013-09/catalog> {" + 
            "      ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasExpressionGraph> ?g." + 
            "    } ." + 
            "  } UNION {" + 
            "    BIND ( <http://qldarch.net/rdf/2012/12/resources#> AS ?g ) ." + 
            "  } UNION {" + 
            "    BIND ( <http://qldarch.net/ns/omeka-export/2013-02-06> AS ?g ) ." + 
            "  } ." + 
            "  graph ?g {" + 
            "    ?s ?p ?o ." + 
            "  } ." + 
            "%s" +
            "} BINDINGS ?s { ( <");

        String summaryRestriction =
            "  graph <http://qldarch.net/ns/rdf/2012-06/terms#> {" + 
            "    ?p a :SummaryProperty ." + 
            "  } ";

        String baseQuery = Joiner.on(">) (<")
            .appendTo(builder, transform(ids, toStringFunction()))
            .append(">) }")
            .toString();

        String query = String.format(baseQuery, (summary ? summaryRestriction : ""));

        logger.debug("ExpressionResource performing SPARQL query: {}", query);

        return query;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("description")
    public String performGet(
            @DefaultValue("") @QueryParam("ID") String id,
            @DefaultValue("") @QueryParam("IDLIST") String idlist,
            @DefaultValue("false") @QueryParam("SUMMARY") boolean summary) {
        logger.debug("Querying summary({}) by id: {}, idlist: {}", summary, id, idlist);

        Set<String> idStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        if (!id.isEmpty()) idStrs.add(id);

        Collection<URI> idURIs = transform(idStrs, Functions.toResolvedURI());

        logger.debug("Raw ids: {}", idURIs);

        return new SparqlToJsonString().performQuery(queryByIds(idURIs, summary));
    }
 
    @POST
    @Path("description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addExpression(String json) throws IOException {
        if (!SecurityUtils.getSubject().isPermitted("expression:create")) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);
        User user = User.currentUser();
        URI userExpressionGraph = user.getExpressionGraph();

        // Check Expression type
        List<URI> types = rdf.getType();
        if (types.size() == 0) {
            logger.info("Bad request received. No rdf:type provided: {}", rdf);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("No rdf:type provided")
                .build();
        }
        try {
            List<RdfDescription> evidences = rdf.getSubGraphs(QA_EVIDENCE);
            if (evidences.isEmpty()) {
                RdfDescription ev = new RdfDescription();
                ev.addProperty(RDF_TYPE, QA_EVIDENCE_TYPE);
                rdf.addProperty(QA_EVIDENCE, ev);
            }
            evidences = rdf.getSubGraphs(QA_EVIDENCE);
            if (evidences.isEmpty()) {
                logger.error("Failed to add evidence to entity");
                throw new MetadataRepositoryException("Failed to add evidence to entity");
            }

            for (RdfDescription ev : evidences) {
                List<URI> evTypes = ev.getType();
                if (evTypes.size() == 0) {
                    logger.info("Bad request received. No rdf:type provided for evidence: {}", ev);
                    return Response
                        .status(Status.BAD_REQUEST)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("No rdf:type provided for evidence")
                        .build();
                }
                URI evType = evTypes.get(0);
                URI evId = user.newId(userExpressionGraph, evType);
                
                ev.setURI(evId);
                ev.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
                ev.replaceProperty(QA_ASSERTION_DATE, new Date());

                this.getRdfDao().insertRdfDescription(ev, user, QAC_HAS_EXPRESSION_GRAPH,
                        user.getExpressionGraph());
            }

            URI type = types.get(0);
            validateRequiredToCreate(rdf, type);

            URI id = user.newId(userExpressionGraph, type);

            rdf.setURI(id);

            // Generate and Perform insertRdfDescription query
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_EXPRESSION_GRAPH,
                    userExpressionGraph);

            if (type.toString().equalsIgnoreCase("http://qldarch.net/ns/rdf/2012-06/terms#Article")) {
            	SolrIngest.ingestArticle(rdf.getURI());
            }
            if (type.toString().equalsIgnoreCase("http://qldarch.net/ns/rdf/2012-06/terms#Interview")) {
            	SolrIngest.ingestTranscript(rdf.getURI());
            }
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertRdfDescription graph:{}, rdf:{})", userExpressionGraph, rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        }

        String entity = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful entity: {}", entity);

        // Return
        return Response.created(rdf.getURI())
            .entity(entity)
            .build();
    }
    
    @DELETE
    @Path("description")
    public Response deleteEvidence(@DefaultValue("") @QueryParam("ID") String id,
                                   @DefaultValue("") @QueryParam("IDLIST") String idlist) {
        User user = User.currentUser();
        
        if (!SecurityUtils.getSubject().isPermitted("expression:delete")
        		&& !user.isOwner(id)) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }

        Set<String> idStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        if (!id.isEmpty()) idStrs.add(id);

        Collection<URI> idURIs = transform(idStrs, Functions.toResolvedURI());

        List<URI> entityURIs = null;
        try {
            String query = EXPRESSION_QUERIES.getInstanceOf("confirmExpressionIds")
                    .add("ids", idURIs)
                    .render();

            logger.debug("EntityResource DELETE evidence performing SPARQL id-query:\n{}", query);

            entityURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error confirming entity ids: {})", idURIs);
            return internalError("Error confirming entity ids");
        }

        if (entityURIs.isEmpty()) {
            logger.info("Bad request received. No entity ids provided.");
            return badRequest("QueryParam ID/IDLIST missing or invalid");
        }

        for (URI entity : entityURIs) {
            try {
                SolrIngest.delete(entity);
                this.getRdfDao().deleteRdfResource(entity);
            } catch (MetadataRepositoryException e) {
                logger.warn("Error performing delete entity:{})", entity);
                return internalError("Error performing delete");
            }
        }

        return Response
                .status(Status.ACCEPTED)
                .type(MediaType.TEXT_PLAIN)
                .entity(String.format("Entity %s deleted", id))
                .build();
    }

    // FIXME: Refactor SesameConnectionPool to allow RdfDataStoreDao to offer user-delimited transactions
    @PUT
    @Path("description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addEntity(@DefaultValue("") @QueryParam("ID") String id,
                              String json) throws IOException {
    	User user = User.currentUser();
    	
        if (!SecurityUtils.getSubject().isPermitted("expression:update")
        		&& !user.isOwner(id)) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        Set<String> idStrs = newHashSet();
        if (!id.isEmpty()) {
        	idStrs.add(id);
        }

        Collection<URI> idURIs = transform(idStrs, Functions.toResolvedURI());
        
        List<URI> entityURIs = null;
        try {
        	String query = EXPRESSION_QUERIES.getInstanceOf("confirmExpressionIds")
                    .add("ids", idURIs)
                    .render();

            logger.debug("EntityResource PUT evidence performing SPARQL id-query:\n{}", query);

            entityURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error confirming expression id: {})", id);
            return internalError("Error confirming expression id");
        }

        if (entityURIs.isEmpty()) {
            logger.info("Bad request received. No entity id provided.");
            return badRequest("QueryParam ID missing or invalid");
        }
        
        List<URI> graphURIs = null;
        try {
            String query = EXPRESSION_QUERIES.getInstanceOf("extractGraphContext")
                    .add("ids", idURIs)
                    .render();

            logger.debug("EntityResource PUT evidence performing SPARQL id-query:\n{}", query);

            graphURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error extracting graph from entity id: {})", id);
            return internalError("Error extracting graph from entity id");
        }

        if (graphURIs.isEmpty()) {
            logger.info("Bad request received. No graph identified from expression id provided.");
            return badRequest("No graph identified from entity id provided");
        }
        
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);

        // Check Entity type
        List<URI> types = rdf.getType();
        if (types.size() == 0) {
            logger.info("Bad request received. No rdf:type provided: {}", rdf);
            return badRequest("No rdf:type provided");
        }
        URI uri = null;
        try {
        	uri = new URI(id);
        } catch (URISyntaxException em) {
            logger.warn("Error performing passing id as URI:{})", id);
            return internalError("Error performing update");
        }
        //-----------------------------------------------------------------------------------------
        
        for (URI entity : entityURIs) {
        	try {
	            URI type = types.get(0);
	            validateRequiredToCreate(rdf, type);
	
	            rdf.setURI(entity);
	            
	            this.getRdfDao().updateRdfDescription(rdf, graphURIs.get(0));
	        } catch (MetadataRepositoryException em) {
	            logger.warn("Error performing updating graph:{}, rdf:{})", graphURIs.get(0), rdf, em);
	            return internalError("Error performing insertRdfDescription");
	        }
	    }

        String entity = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful entity: {}", entity);

        // Return
        return Response.created(rdf.getURI())
            .entity(entity)
            .build();
    }
    
    private void validateRequiredToCreate(RdfDescription rdf, URI type)
            throws MetadataRepositoryException {
        QldarchOntology ont = this.getRdfDao().getOntology();

        Multimap<URI, Object> entity = ont.findByURI(type);
        Collection<Object> requiredPredicates = entity.get(QA_REQUIRED);
        for (Object o : requiredPredicates) {
            if (o instanceof URI) {
                if (rdf.getValues((URI)o).isEmpty()) {
                    logger.info("create:entity received missing required property: {}", o);
                    throw new MetadataRepositoryException("Missing required property " + o);
                }
            } else {
                logger.warn("Required property {} for type {} was not a URI", o, type);
            }
        }
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
