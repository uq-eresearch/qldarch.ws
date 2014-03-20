package net.qldarch.web.resource;

import net.qldarch.web.model.QldarchOntology;
import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.*;
import net.qldarch.web.util.Functions;
import net.qldarch.web.util.ResourceUtils;
import net.qldarch.web.util.SparqlToJsonString;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Multimap;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;
import org.openrdf.model.Statement;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status;

import static net.qldarch.web.service.KnownURIs.*;
import static net.qldarch.web.util.CollectionUtils.asIterable;
import static net.qldarch.web.util.ResourceUtils.*;

@Path("/entity")
public class EntitySummaryResource {
    public static Logger logger = LoggerFactory.getLogger(EntitySummaryResource.class);

    private RdfDataStoreDao rdfDao;

    private static final STGroupFile ENTITY_QUERIES = new STGroupFile("queries/Entities.sparql.stg");

    public static String prepareEntitiesByTypesQuery(Collection<URI> types, long since,
             boolean includeSubClass, boolean includeSuperClass, boolean summary) {

        String query = ENTITY_QUERIES.getInstanceOf("byType")
                .add("types", types)
                .add("incSubClass", includeSubClass)
                .add("incSuperClass", includeSuperClass)
                .add("summary", summary)
                .render();

        logger.debug("AnnotationResource performing SPARQL query: {}", query);

        return query;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("summary/{type : ([^/]+)?}")
    public String summaryGet(
            @DefaultValue("") @PathParam("type") String type,
            @DefaultValue("false") @QueryParam("INCSUBCLASS") boolean includeSubClass,
            @DefaultValue("false") @QueryParam("INCSUPERCLASS") boolean includeSuperClass,
            @DefaultValue("0") @QueryParam("since") long since,
            @DefaultValue("") @QueryParam("TYPELIST") String typelist) {

        return findByType(type, typelist, since, includeSubClass, includeSuperClass, true);
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
    @Path("detail/{type : ([^/]+)?}")
    public String detailGet(
            @DefaultValue("") @PathParam("type") String type,
            @DefaultValue("false") @QueryParam("INCSUBCLASS") boolean includeSubClass,
            @DefaultValue("false") @QueryParam("INCSUPERCLASS") boolean includeSuperClass,
            @DefaultValue("0") @QueryParam("since") long since,
            @DefaultValue("") @QueryParam("TYPELIST") String typelist) {

        return findByType(type, typelist, since, includeSubClass, includeSuperClass, false);
    }

    public String findByType(String type, String typelist, long since,
            boolean includeSubClass, boolean includeSuperClass, boolean summary) {
        logger.debug("Querying summary({}) by type: {}, typelist: {}", summary, type, typelist);

        if (since < 0) since = 0;  // Sanitise since

        Set<String> typeStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(typelist));
        if (!type.isEmpty()) typeStrs.add(type);

        Collection<URI> typeURIs = transform(typeStrs, Functions.toResolvedURI());

        logger.debug("Raw types: {}", typeURIs);

        return new SparqlToJsonString().performQuery(
                prepareEntitiesByTypesQuery(typeURIs, since, includeSubClass, includeSuperClass, summary));
    }

    public static String findByIds(Collection<URI> ids, boolean summary) {
        if (ids.size() < 1) {
            throw new IllegalArgumentException("Empty id collection passed to findEvidenceByIds()");
        }

        String query = ENTITY_QUERIES.getInstanceOf("byIds")
                .add("ids", ids)
                .add("summary", summary)
                .render();

        logger.debug("EntityResource performing SPARQL query: {}", query);

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

        return new SparqlToJsonString().performQuery(findByIds(idURIs, summary));
    }
 
    @POST
    @Path("description")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addEntity(String json) throws IOException {
        if (!SecurityUtils.getSubject().isPermitted("entity:create")) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);

        User user = User.currentUser();
        URI userEntityGraph = user.getEntityGraph();

        // Check Entity type
        List<URI> types = rdf.getType();
        if (types.size() == 0) {
            logger.info("Bad request received. No rdf:type provided: {}", rdf);
            return badRequest("No rdf:type provided");
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
                    return badRequest("No rdf:type provided for evidence");
                }
                URI evType = evTypes.get(0);
                URI evId = user.newId(userEntityGraph, evType);
                
                ev.setURI(evId);
                ev.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
                ev.replaceProperty(QA_ASSERTION_DATE, new Date());

                this.getRdfDao().insertRdfDescription(ev, user, QAC_HAS_ENTITY_GRAPH, userEntityGraph);
            }

            URI type = types.get(0);
            validateRequiredToCreate(rdf, type);

            URI id = user.newId(userEntityGraph, type);

            rdf.setURI(id);

            // Generate and Perform insertRdfDescription query
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_ENTITY_GRAPH, userEntityGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertRdfDescription graph:{}, rdf:{})", userEntityGraph, rdf, em);
            return internalError("Error performing insertRdfDescription");
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
        
        if (!SecurityUtils.getSubject().isPermitted("entity:delete")
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
            String query = ENTITY_QUERIES.getInstanceOf("confirmEntityIds")
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
    	
        if (!SecurityUtils.getSubject().isPermitted("entity:update")
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
            String query = ENTITY_QUERIES.getInstanceOf("confirmEntityIds")
                    .add("ids", idURIs)
                    .render();

            logger.debug("EntityResource PUT evidence performing SPARQL id-query:\n{}", query);

            entityURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error confirming entity id: {})", id);
            return internalError("Error confirming entity id");
        }

        if (entityURIs.isEmpty()) {
            logger.info("Bad request received. No entity id provided.");
            return badRequest("QueryParam ID missing or invalid");
        }
        
        List<URI> graphURIs = null;
        try {
            String query = ENTITY_QUERIES.getInstanceOf("extractGraphContext")
                    .add("ids", idURIs)
                    .render();

            logger.debug("EntityResource PUT evidence performing SPARQL id-query:\n{}", query);

            graphURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error extracting graph from entity id: {})", id);
            return internalError("Error extracting graph from entity id");
        }

        if (graphURIs.isEmpty()) {
            logger.info("Bad request received. No graph identified from entity id provided.");
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
