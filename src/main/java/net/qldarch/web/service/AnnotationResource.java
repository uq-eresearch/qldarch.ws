package net.qldarch.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;
import static javax.ws.rs.core.Response.Status;

@Path("/annotation")
public class AnnotationResource {
    public static Logger logger = LoggerFactory.getLogger(AnnotationResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final URI QA_ASSERTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertedBy");
    public static final URI QA_SUBJECT =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#subject");
    public static final URI QA_ASSERTION_DATE =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertionDate");
    public static final URI QA_DOCUMENTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#documentedBy");
    public static final URI QAC_HAS_ANNOTATION_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasAnnotationGraph");
    public static final URI QAC_CATALOG_GRAPH = 
        URI.create("http://qldarch.net/rdf/2013-09/catalog");

    public static String SHARED_ANNOTATION_GRAPH = "http://qldarch.net/rdf/2013-09/annotations";

    private QldarchOntology ontology = null;
    private SesameConnectionPool connectionPool = null;

    public static String annotationQuery(URI annotation, BigDecimal time, BigDecimal duration) {
        BigDecimal end = time.add(duration);

        String formatStr = 
                "PREFIX : <http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "PREFIX xsd:    <http://www.w3.org/2001/XMLSchema#> " +
                "select ?s ?p ?o " +
                "where { " + 
                "  graph <http://qldarch.net/rdf/2013-09/catalog> { " +
                "    ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasAnnotationGraph> ?g. " +
                "  } . " +
                "  graph ?g { " +
                "    ?s a ?t . " +
                "    ?t rdfs:subClassOf :Relationship . " +
                "    ?s :subject <%s> . " +
                "    ?s :regionStart ?start . " +
                "    ?s :regionEnd ?end . " +
                "    ?s ?p ?o ." +
                "  } . " +
                "  FILTER ( xsd:decimal(\"%s\") <= ?end && " +
                "           xsd:decimal(\"%s\") >= ?start ) " +
                "}";

        String query = String.format(formatStr, annotation, time, end);

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

        URI resource = null;
        BigDecimal time = null;
        BigDecimal duration = null;
        try {
            resource = new URI(resourceStr);
            time = new BigDecimal(timeStr);
            duration = new BigDecimal(durationStr);
        } catch (URISyntaxException eu) {
            logger.info("Malformed URI submitted to annotations request: {}", resourceStr, eu);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam RESOURCE malformed")
                .build();
        } catch (NumberFormatException en) {
            logger.info("Malformed xsd:decimal submitted to annotations request: {}/{}",
                timeStr, durationStr, en);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam TIME or DURATION malformed")
                .build();
        }

        logger.debug("Raw annotations query: {}, {}, {}", resource, time, duration);

        String result = new SparqlToJsonString().performQuery(
                annotationQuery(resource, time, duration));

        return Response.ok()
            .entity(result)
            .build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresPermissions("create:annotation")
    public Response addAnnotation(String json) throws IOException {
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

        // Check Annotation type
        List<URI> types = rdf.getType();
        if (types.size() == 0) {
            logger.info("Bad request received. No rdf:type provided: {}", rdf);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("No rdf:type provided")
                .build();
        } else if (types.size() > 1) {
            logger.info("Bad request received. Multiple rdf:types provided: {}", rdf);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("Multiple rdf:types provided")
                .build();
        }

        URI type = types.get(0);
        URI userAnnotationGraph = user.getAnnotationGraph();

        // Generate id
        URI id = newAnnotationId(userAnnotationGraph, type);

        rdf.setURI(id);
        rdf.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
        rdf.replaceProperty(QA_ASSERTION_DATE, new Date());
        rdf.replaceProperty(QA_DOCUMENTED_BY, rdf.getValues(QA_SUBJECT));

        // Generate and Perform insert query
        try {
            performInsert(rdf, user);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insert id:{}, graph:{}, rdf:{})",
                    id, userAnnotationGraph, rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insert")
                .build();
        }

        String entity = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful entity: {}", entity);
        // Return
        return Response.created(id)
            .entity(entity)
            .build();
    }

    public static URI QLDARCH_TERMS = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#");
    public static URI FOAF_NS = URI.create("http://xmlns.com/foaf/0.1/");

    private URI newAnnotationId(URI userAnnotationGraph, URI type) {
        URI typeFrag = QLDARCH_TERMS.relativize(type);
        if (typeFrag.equals(type)) {
            typeFrag = FOAF_NS.relativize(type);
            if (typeFrag.equals(type)) {
                return null;
            }
        }

        URI entityBase = userAnnotationGraph.resolve(typeFrag);

        String id = getNextIdForUser(userAnnotationGraph);

        return entityBase.resolve(id);
    }

    private static long START_DATE = new GregorianCalendar(2012, 1, 1, 0, 0, 0).getTime().getTime();
    private static Random random = new Random();

    private synchronized String getNextIdForUser(URI userAnnotationGraph) {
        long delta = System.currentTimeMillis() - START_DATE;
        try {
            Thread.sleep(1);
        } catch (InterruptedException ei) {
            logger.warn("ID delay interrupted for {}", userAnnotationGraph.toString(), ei);
        }
            
        return Long.toString(delta);
    }

    private void performInsert(final RdfDescription rdf, final User user)
            throws MetadataRepositoryException {
        this.getConnectionPool().performOperation(new RepositoryOperation() {
            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                URIImpl userURI = new URIImpl(user.getUserURI().toString());
                URIImpl hasAnnGraphURI = new URIImpl(QAC_HAS_ANNOTATION_GRAPH.toString());
                URIImpl contextURI = new URIImpl(user.getAnnotationGraph().toString());
                URIImpl catalogURI = new URIImpl(QAC_CATALOG_GRAPH.toString());
                conn.add(userURI, hasAnnGraphURI, contextURI, catalogURI);
                conn.add(rdf.asStatements(), contextURI);
            }
        });
    }

    public void setConnectionPool(SesameConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public synchronized SesameConnectionPool getConnectionPool() {
        if (this.connectionPool == null) {
            this.connectionPool = SesameConnectionPool.instance();
        }
        return this.connectionPool;
    }

    public void setOntology(QldarchOntology ontology) {
        this.ontology = ontology;
    }

    public synchronized QldarchOntology getOntology() {
        if (this.ontology == null) {
            this.ontology = QldarchOntology.instance();
        }
        return this.ontology;
    }
}
