package net.qldarch.web.resource;

import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.util.SparqlToJsonString;
import org.codehaus.jackson.map.ObjectMapper;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
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
import javax.ws.rs.core.Response.Status;

import static com.google.common.collect.Sets.newHashSet;

@Path("/reference")
public class ReferenceSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(ReferenceSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final URI QA_ASSERTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertedBy");
    public static final URI QA_SUBJECT =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#subject");
    public static final URI QA_ASSERTION_DATE =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertionDate");
    public static final URI QA_DOCUMENTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#documentedBy");
    public static final URI QA_REFERENCE_RELATION_TYPE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#ReferenceRelation");
    public static final URI QAC_HAS_REFERENCE_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasReferenceGraph");
    public static final URI QAC_CATALOG_GRAPH = 
        URI.create("http://qldarch.net/rdf/2013-09/catalog");

    public static String SHARED_REFERENCE_GRAPH = "http://qldarch.net/rdf/2013-09/references";

    private RdfDataStoreDao rdfDao;

    public static String referenceQuery(URI reference, BigDecimal time, BigDecimal duration) {
        BigDecimal end = time.add(duration);

        String formatStr = 
                "PREFIX : <http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "PREFIX xsd:    <http://www.w3.org/2001/XMLSchema#> " +
                "select ?s ?p ?o " +
                "where { " + 
                "  graph <http://qldarch.net/rdf/2013-09/catalog> { " +
                "    ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasReferenceGraph> ?g. " +
                "  } . " +
                "  graph ?g { " +
                "    ?s a :ReferenceRelation . " +
                "    ?s :subject <%s> . " +
                "    ?s :regionStart ?start . " +
                "    ?s :regionEnd ?end . " +
                "    ?s ?p ?o ." +
                "  } . " +
                "  FILTER ( xsd:decimal(\"%s\") <= ?end && " +
                "           xsd:decimal(\"%s\") >= ?start ) " +
                "}";

        String query = String.format(formatStr, reference, time, end);

        logger.debug("ReferenceResource performing SPARQL query: {}", query);
        
        return query;
    }

    @GET
    @Produces("application/json")
    public Response performGet(
            @DefaultValue("") @QueryParam("RESOURCE") String resourceStr,
            @DefaultValue("") @QueryParam("TIME") String timeStr,
            @DefaultValue("0.0") @QueryParam("DURATION") String durationStr) {

        logger.debug("Querying references for resource: " + resourceStr 
        		+ ", time: " + timeStr + ", duration: " + durationStr);

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
            logger.info("Malformed URI submitted to references request: {}", resourceStr, eu);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam RESOURCE malformed")
                .build();
        } catch (NumberFormatException en) {
            logger.info("Malformed xsd:decimal submitted to references request: " + timeStr + "/" + durationStr, en);
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam TIME or DURATION malformed")
                .build();
        }

        logger.debug("Raw references query: " + resource + ", " + time + ", " + duration);

        String result = new SparqlToJsonString().performQuery(
                referenceQuery(resource, time, duration));

        return Response.ok()
            .entity(result)
            .build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addReference(String json) throws IOException, MetadataRepositoryException {
        if (!SecurityUtils.getSubject().isPermitted("annotation:create")) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);
        User user = User.currentUser();

        // Check Reference type
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
        URI userReferenceGraph = user.getReferenceGraph();

        // Generate id
        URI id = user.newId(userReferenceGraph, type);

        rdf.setURI(id);
        rdf.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
        rdf.replaceProperty(QA_ASSERTION_DATE, new Date());
        rdf.replaceProperty(QA_DOCUMENTED_BY, rdf.getValues(QA_SUBJECT));

        // Generate and Perform insertRdfDescription query
        try {
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_REFERENCE_GRAPH, userReferenceGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertRdfDescription id:" + id + ", graph:" 
            		+ userReferenceGraph + ", rdf:" + ")", em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        }

        String entity = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful entity: {}", entity);
        // Return
        return Response.created(id)
            .entity(entity)
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
