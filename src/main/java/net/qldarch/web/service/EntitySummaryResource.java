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

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;

@Path("/entity")
public class EntitySummaryResource {
    public static Logger logger = LoggerFactory.getLogger(EntitySummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

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

    /*
    @POST
    @Produces("application/json")
    @Path("description/{type}")
    public String addEntity(
            @PathParam("type") String type,
            @PathParam("id") String id,
            @DefaultValue("") @QueryParam("IDLIST") String typelist) {
        logger.debug("Querying type: {}, typelist: {}", type, typelist);

        Set<String> types = newHashSet(type);
        Iterables.addAll(types, Splitter.on(',').trimResults().omitEmptyStrings().split(typelist));

        logger.debug("Raw types: {}", types);

        return new SparqlToJsonString().performQuery(formatQuery(types));
    }
    */
}
