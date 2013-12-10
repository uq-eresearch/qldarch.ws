package net.qldarch.web.resource;

import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.util.ResourceUtils;
import net.qldarch.web.util.SparqlToJsonString;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Date;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import static javax.ws.rs.core.Response.Status;

import static net.qldarch.web.service.KnownURIs.*;

@Path("/annotation")
public class AnnotationResource {
    public static Logger logger = LoggerFactory.getLogger(AnnotationResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String SHARED_ANNOTATION_GRAPH = "http://qldarch.net/rdf/2013-09/annotations";

    private RdfDataStoreDao rdfDao;

    private static final STGroupFile ANNOTATION_QUERIES = new STGroupFile("queries/Annotations.sparql.stg");

    public static String prepareAnnotationByUtteranceQuery(URI annotation, BigDecimal time, BigDecimal duration) {
        BigDecimal end = time.add(duration);

        String query = ANNOTATION_QUERIES.getInstanceOf("byUtterance")
                .add("resource", annotation)
                .add("lower", time)
                .add("upper", end)
                .render();
        logger.debug("AnnotationResource performing SPARQL query: {}", query);

        return query;
    }

    @GET
    @Produces("application/json")
    public Response performGet(
            @DefaultValue("") @QueryParam("RESOURCE") String resourceStr,
            @DefaultValue("") @QueryParam("TIME") String timeStr,
            @DefaultValue("0.0") @QueryParam("DURATION") String durationStr) {

        logger.debug("Querying annotations for resource: {}, time: {}, duration: {}",
                resourceStr, timeStr, durationStr);

        if (resourceStr.isEmpty()) {
            logger.info("Bad request received. No resource provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam RESOURCE missing")
                .build();
        }
        if (timeStr.isEmpty()) {
            logger.info("Bad request received. No time provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam TIME missing")
                .build();
        }

        try {
            URI resource = resolveURI(resourceStr, "resource");
            BigDecimal time = parseDecimal(timeStr, "time");
            BigDecimal duration = parseDecimal(durationStr, "duration");

            logger.debug("Raw annotations query: {}, {}, {}", resource, time, duration);

            String result = new SparqlToJsonString().performQuery(
                    prepareAnnotationByUtteranceQuery(resource, time, duration));

            return Response.ok()
                .entity(result)
                .build();
        } catch (ResourceFailedException er) {
            return er.getResponse();
        }
    }

    public static String prepareAnnotationByRelationshipQuery(URI subject, URI predicate, URI object) {
        String query = ANNOTATION_QUERIES.getInstanceOf("byRelationship")
                .add("subject", subject)
                .add("predicate", predicate)
                .add("object", object)
                .render();

        logger.debug("Annotation by Relationship SPARQL: {}", query);

        return query;
    }

    @GET
    @Path("relationship")
    @Produces("application/json")
    public Response annotationsByRelationship(
            @DefaultValue("") @QueryParam("subject") String subjectStr,
            @DefaultValue("") @QueryParam("predicate") String predicateStr,
            @DefaultValue("") @QueryParam("object") String objectStr) {

        logger.debug("Querying annotations by relationship subject: {}, predicate: {}, object: {}",
                subjectStr, predicateStr, objectStr);

        try {
            URI subject = resolveURI(subjectStr, "subject");
            URI predicate = resolveURI(predicateStr, "predicate");
            URI object = resolveURI(objectStr, "object");

            logger.debug("Raw annotations query: {}, {}, {}", subject, predicate, object);

            String result = new SparqlToJsonString().performQuery(
                    prepareAnnotationByRelationshipQuery(subject, predicate, object));

            return Response.ok()
                    .entity(result)
                    .build();
        } catch (ResourceFailedException er) {
            return er.getResponse();
        }

    }

    private URI resolveURI(String uriStr, String description) throws ResourceFailedException {
        try {
            if (uriStr == null || uriStr.isEmpty()) return null;
            return KnownPrefixes.resolve(uriStr);
        } catch (MetadataRepositoryException em) {
            String msg = String.format("Unable to resolve %s URI: %s", description, uriStr);
            logger.info(msg, em);
            throw new ResourceFailedException(msg, em,
                    Response.status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(msg)
                    .build());
        }
    }

    private BigDecimal parseDecimal(String decimal, String description) throws ResourceFailedException {
        try {
            if (decimal == null || decimal.isEmpty()) return null;
            return new BigDecimal(decimal);
        } catch (NumberFormatException en) {
            String msg = String.format("Unable to parse %s as xsd:decimal: %s", description, decimal);
            logger.info(msg, en);
            throw new ResourceFailedException(msg, en,
                    Response.status(Status.BAD_REQUEST)
                            .type(MediaType.TEXT_PLAIN)
                            .entity(msg)
                            .build());
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresPermissions("create:annotation")
    public Response addAnnotation(String json) throws IOException {
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);

        // Check User Auth
        User user = User.currentUser();

        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .type(MediaType.TEXT_PLAIN)
                .entity("Anonymous users are not permitted to create annotations")
                .build();
        }

        URI userAnnotationGraph = user.getAnnotationGraph();

        try {
            List<RdfDescription> evidences = rdf.getSubGraphs(QA_EVIDENCE);
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
                URI evId = user.newId(userAnnotationGraph, evType);
                
                ev.setURI(evId);
                ev.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
                ev.replaceProperty(QA_ASSERTION_DATE, new Date());

                this.getRdfDao().performInsert(ev, user, QAC_HAS_ANNOTATION_GRAPH,
                        userAnnotationGraph);
            }

            List<URI> relTypes = rdf.getType();
            if (relTypes.size() == 0) {
                logger.info("Bad request received. No rdf:type provided: {}", rdf);
                return Response
                    .status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("No rdf:type provided")
                    .build();
            }
            URI relType = relTypes.get(0);
            URI relId = user.newId(userAnnotationGraph, relType);

            rdf.setURI(relId);

            // Generate and Perform insert query
            this.getRdfDao().performInsert(rdf, user, QAC_HAS_ANNOTATION_GRAPH,
                    userAnnotationGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insert graph:{}, rdf:{})", userAnnotationGraph, rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insert")
                .build();
        }

        String annotation = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful annotation: {}", annotation);
        // Return
        return Response.created(rdf.getURI())
            .entity(annotation)
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
