package net.qldarch.web.resource;

import net.qldarch.web.model.QldarchOntology;
import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.*;
import net.qldarch.web.util.Functions;
import net.qldarch.web.util.SparqlToJsonString;
import org.codehaus.jackson.map.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Multimap;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;
import static javax.ws.rs.core.Response.Status;

import static net.qldarch.web.service.KnownURIs.*;

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
    @RequiresPermissions("create:entity")
    public Response addEntity(String json) throws IOException {
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);

        // Check User Authz
        User user = User.currentUser();

        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .type(MediaType.TEXT_PLAIN)
                .entity("Anonymous users are not permitted to create annotations")
                .build();
        }

        URI userEntityGraph = user.getEntityGraph();

        // Check Entity type
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
    @RequiresPermissions("delete:entity")
    public Response deleteEvidence(@DefaultValue("") @QueryParam("ID") String id,
                                   @DefaultValue("") @QueryParam("IDLIST") String idlist) {
        User user = User.currentUser();

        if (user.isAnon()) {
            return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Anonymous users are not permitted to delete entities")
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
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error confirming entity ids")
                    .build();
        }

        if (entityURIs.isEmpty()) {
            logger.info("Bad request received. No entity ids provided.");
            return Response
                    .status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("QueryParam ID/IDLIST missing or invalid")
                    .build();
        }

        for (URI entity : entityURIs) {
            try {
                this.getRdfDao().deleteRdfResource(entity);
            } catch (MetadataRepositoryException e) {
                logger.warn("Error performing delete entity:{})", entity);
                return Response
                        .status(Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("Error performing delete")
                        .build();
            }
        }

        return Response
                .status(Status.ACCEPTED)
                .type(MediaType.TEXT_PLAIN)
                .entity(String.format("Entity %s deleted", id))
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
