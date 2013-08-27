package net.qldarch.web.service;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;

@Path("/entity")
public class EntitySummaryResource {
    public static Logger logger = LoggerFactory.getLogger(EntitySummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String USER_ENTITY_GRAPH_FORMAT "http://qldarch.net/users/%s/entities";

    public static String summaryQuery(Collection<String> types) {
        StringBuilder builder = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o " +
                "from <http://qldarch.net/rdf/user/entities> " +            // User added entities
                "from <http://qldarch.net/rdf/2012/12/resources#> " +     // Inferred entities
                "from named <http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "where { " + 
                "  ?s a ?t." +
                "  ?s ?p ?o ." +
                "  GRAPH <http://qldarch.net/ns/rdf/2012-06/terms#> { " +
                "    ?p :summaryProperty true . " +
                "  } " +
                "} BINDINGS ?t { (<");

        String query = Joiner.on(">) (<")
            .appendTo(builder, transform(types, toStringFunction()))
            .append(">) }")
            .toString();

        logger.debug("EntityResource performing SPARQL query: {}", query);
        
        return query;
    }

    @GET
    @Produces("application/json")
    @Path("summary/{type}")
    public String performGet(
            @PathParam("type") String type,
            @DefaultValue("") @QueryParam("TYPELIST") String typelist) {
        logger.debug("Querying type: {}, typelist: {}", type, typelist);

        Set<String> types = newHashSet(type);
        Iterables.addAll(types, Splitter.on(',').trimResults().omitEmptyStrings().split(typelist));

        logger.debug("Raw types: {}", types);

        return new SparqlToJsonString().performQuery(summaryQuery(types));
    }

    public static String descriptionQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o " +
                "from <http://qldarch.net/rdf/user/entities> " +             // User added entities
                "from <http://qldarch.net/rdf/2012/12/resources#> where {" + // Inferred entities
                "  ?s ?p ?o ." +
                "} BINDINGS ?s { (<");

        String query = Joiner.on(">) (<")
            .appendTo(builder, transform(ids, toStringFunction()))
            .append(">) }")
            .toString();

        logger.debug("EntityResource performing SPARQL query: {}", query);
        
        return query;
    }

    @GET
    @Produces("application/json")
    @Path("{type}/{id}")
    public String performGet(
            @PathParam("type") String type,
            @PathParam("id") String id,
            @DefaultValue("") @QueryParam("IDLIST") String idlist) {
        logger.debug("Querying id: {}, idlist: {}", id, idlist);

        Set<String> ids = newHashSet(id);
        Iterables.addAll(ids, Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));

        logger.debug("Raw ids: {}", ids);

        return new SparqlToJsonString().performQuery(descriptionQuery(ids));
    }

    @POST
    @Path("description/")
    @Produces("text/plain")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresPermissions("create:entity")
    public Response addEntity(RdfDescription rdf) {
        // Check User Authz
        Subject currentUser = SecurityUtils.getSubject();
        String username = Validators.username((String)currentUser.getPrincipal());

        URI userEntityGraph = URI.create(String.format(USER_ENTITY_GRAPH_FORMAT, username));

        // Check Entity type
        Collection<URI> types = rdf.getType();
        if (types.size() != 1) {
            error();
        }

        URI type = type.get(0);

        Multimap<URI, Object> entity = QldarchOntology.findByRdfType(type);

        // Generate id
        URI id = newEntityId(userEntityGraph, type);

        // Generate and Perform insert query
        performInsert(id, rdf);

        // Return id

        return Response.created())
            .entity("Hello this would be json")
            .build();
    }

    public static URI QLDARCH_TERMS = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#");
    public static URI FOAF_NS = URI.create("http://xmlns.com/foaf/0.1/");

    private URI newEntityId(String userEntityGraph, URI type) {
        URI typeFrag = QLDARCH_TERMS.relativize(type);
        if (typeFrag.equals(type)) {
            typeFrag = FOAF_NS.relativeize(type);
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

    private synchronized String getNextIdForUser(userEntityGraph) {
        long delta = System.currentTimeMillis() - START_DATE;
        Thread.sleep(1);
        return Long.toString(delta);
    }

    private void performInsert(URI id, RdfDescription rdf) {
    }
}
