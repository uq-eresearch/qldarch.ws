package net.qldarch.web.service;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
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

@Path("/entity")
public class EntitySummaryResource {
    public static Logger logger = LoggerFactory.getLogger(EntitySummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String USER_ENTITY_GRAPH_FORMAT = "http://qldarch.net/users/%s/entities";

    private QldarchOntology ontology = null;
    private SesameConnectionPool connectionPool = null;

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
            "      ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasEntityGraph> ?g." + 
            "    } ." + 
            "  } UNION {" + 
            "    BIND ( <http://qldarch.net/rdf/2012/12/resources#> AS ?g ) ." + 
            "  } ." + 
            "  graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" + 
            "    ?transType rdfs:subClassOf ?type ." + 
            "  } ." + 
            "  graph ?g {" + 
            "    ?s a ?transType ." + 
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

        logger.debug("EntityResource performing SPARQL query: {}", query);

        return query;
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
            throw new IllegalArgumentException("Empty id collection passed to queryByIds()");
        }

        StringBuilder builder = new StringBuilder(
            "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " + 
            "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " + 
            "select distinct ?s ?p ?o where  {" + 
            "  {" + 
            "    graph <http://qldarch.net/rdf/2013-09/catalog> {" + 
            "      ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasEntityGraph> ?g." + 
            "    } ." + 
            "  } UNION {" + 
            "    BIND ( <http://qldarch.net/rdf/2012/12/resources#> AS ?g ) ." + 
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

        return new SparqlToJsonString().performQuery(queryByIds(idURIs, summary));
    }
 
    @POST
    @Path("description")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresPermissions("create:entity")
    public Response addEntity(RdfDescription rdf) {
        // Check User Authz
        Subject currentUser = SecurityUtils.getSubject();
        String username = Validators.username((String)currentUser.getPrincipal());

        URI userEntityGraph = URI.create(String.format(USER_ENTITY_GRAPH_FORMAT, username));

        // Check Entity type
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

        // Generate id
        URI id = newEntityId(userEntityGraph, type);
        rdf.setURI(id);

        // Generate and Perform insert query
        try {
            performInsert(rdf, userEntityGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insert id:{}, graph:{}, rdf:{})",
                    id, userEntityGraph, rdf, em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insert")
                .build();
        }

        // Return
        return Response.created(id)
            .entity(rdf)
            .build();
    }

    public static URI QLDARCH_TERMS = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#");
    public static URI FOAF_NS = URI.create("http://xmlns.com/foaf/0.1/");

    private URI newEntityId(URI userEntityGraph, URI type) {
        URI typeFrag = QLDARCH_TERMS.relativize(type);
        if (typeFrag.equals(type)) {
            typeFrag = FOAF_NS.relativize(type);
            if (typeFrag.equals(type)) {
                return null;
            }
        }

        URI entityBase = userEntityGraph.resolve(typeFrag);

        String id = getNextIdForUser(userEntityGraph);

        return entityBase.resolve(id);
    }

    private static long START_DATE = new GregorianCalendar(2012, 1, 1, 0, 0, 0).getTime().getTime();
    private static Random random = new Random();

    private synchronized String getNextIdForUser(URI userEntityGraph) {
        long delta = System.currentTimeMillis() - START_DATE;
        try {
            Thread.sleep(1);
        } catch (InterruptedException ei) {
            logger.warn("ID delay interrupted for {}", userEntityGraph.toString(), ei);
        }
            
        return Long.toString(delta);
    }

    private void performInsert(final RdfDescription rdf, final URI userEntityGraph)
            throws MetadataRepositoryException {
        this.getConnectionPool().performOperation(new RepositoryOperation() {
            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                URIImpl context = new URIImpl(userEntityGraph.toString());
                conn.add(rdf.asStatements(), new URIImpl(userEntityGraph.toString()));
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
