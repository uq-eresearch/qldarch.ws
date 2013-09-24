package net.qldarch.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
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
    public static final URI QA_EVIDENCE =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#evidence");
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
            "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " +
            "select distinct ?s ?p ?o  where {   " +
            "    graph <http://qldarch.net/rdf/2013-09/catalog> {" +
            "        ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasAnnotationGraph> ?g. " +
            "    } . " +
            "    graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" +
            "       ?t rdfs:subClassOf :Relationship ." +
            "    } . " +
            "    graph ?g {" +
            "        ?r a ?t ." +
            "        ?r :evidence ?e ." +
            "        ?e :documentedBy <%s> ." +
            "        ?e :timeFrom ?start ." +
            "        ?e :timeTo ?end ." +
            "    } . " +
            "    {   " +
            "        graph ?g {" +
            "            BIND (?r AS ?s) ." +
            "            ?s ?p ?o ." +
            "        } ." +
            "    } UNION {" +
            "        graph ?g {" +
            "            ?r :evidence ?s ." +
            "            ?s ?p ?o ." +
            "        } ." +
            "    } ." +
            "    FILTER ( xsd:decimal(\"%s\") <= ?end &&" +
            "             xsd:decimal(\"%s\") >= ?start ) . " +
            "} ";

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
            resource = KnownPrefixes.resolve(resourceStr);
            time = new BigDecimal(timeStr);
            duration = new BigDecimal(durationStr);
        } catch (MetadataRepositoryException em) {
            logger.info("Unable to resolve submitted URI: {}", resourceStr, em);
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
                URI evId = newAnnotationId(userAnnotationGraph, evType);
                
                ev.setURI(evId);
                ev.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
                ev.replaceProperty(QA_ASSERTION_DATE, new Date());

                performInsert(ev, user);
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
            URI relId = newAnnotationId(userAnnotationGraph, relType);

            rdf.setURI(relId);

            // Generate and Perform insert query
            performInsert(rdf, user);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insert graph:{}, rdf:{})", userAnnotationGraph, rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insert")
                .build();
        }

        String entity = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful entity: {}", entity);
        // Return
        return Response.created(rdf.getURI())
            .entity(entity)
            .build();
    }

    public static URI[] namespaces = {
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#"),
        URI.create("http://xmlns.com/foaf/0.1/"),
    };

    private URI newAnnotationId(URI userAnnotationGraph, URI type)
            throws MetadataRepositoryException {
        String shorttype = "UnknownTypeNS";
        for (URI ns : namespaces) {
            URI typeFrag = ns.relativize(type);
            if (!typeFrag.equals(type)) {
                if (typeFrag.getScheme() != null ||
                        typeFrag.getAuthority() != null ||
                        typeFrag.getQuery() != null) {
                    logger.warn("Unexpected resolved shorttype. type:{} ns:{} short:{}",
                            type, ns, typeFrag);
                    continue;
                }
                String fragment = typeFrag.getFragment();
                String path = typeFrag.getPath();
                if ((fragment == null || fragment.isEmpty()) && (path == null || path.isEmpty())) {
                    logger.info("No fragment or path found in shorttype. type:{} ns:{} short:{}",
                            type, ns, typeFrag);
                    continue;
                } else if (fragment == null || fragment.isEmpty()) {
                    shorttype = path;
                } else if (path == null || path.isEmpty()) {
                    shorttype = fragment;
                } else {
                    logger.info("Both fragment or path found in shorttype. " +
                        "type:{} ns:{} short:{} fragment:'{}' path:'{}'",
                            type, ns, typeFrag, fragment, path);
                }
            }
        }
        
        logger.info("Resolving {} against {}", shorttype, userAnnotationGraph);
        URI entityBase = userAnnotationGraph.resolve(shorttype);

        String id = getNextIdForUser(userAnnotationGraph);

        try {
            return new URI(entityBase.getScheme(), entityBase.getSchemeSpecificPart(), id);
        } catch (URISyntaxException eu) {
            logger.error("Invalid URI generated by newAnnotationId", eu);
            throw new MetadataRepositoryException("Invalid URI generated by newAnnotationId", eu);
        }
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
