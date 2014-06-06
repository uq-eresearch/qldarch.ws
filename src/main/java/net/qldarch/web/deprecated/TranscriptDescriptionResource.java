package net.qldarch.web.deprecated;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import net.qldarch.web.util.SparqlToJsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;

@Path("/transcriptDescription")
public class TranscriptDescriptionResource {
    public static Logger logger = LoggerFactory.getLogger(TranscriptDescriptionResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String formatQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o from <http://qldarch.net/rdf/user/entities> where {" +
                "  ?s a :DigitalFile ." +
                "  ?s ?p ?o ." +
                "  } BINDINGS ?s { (<");

        String query = Joiner.on(">) (<").appendTo(builder, transform(ids, toStringFunction())).append(">) }").toString();
        logger.debug("TranscriptDescriptionResource performing SPARQL query: {}", query);
        
        return query;
    }

    @GET
    @Produces("application/json")
    public String performGet(
            @QueryParam("PREFIX") String prefix,
            @QueryParam("ID") Set<String> idParam,
            @DefaultValue("") @QueryParam("IDLIST") String idlist) {
        logger.debug("Querying PREFIX: " + prefix + ", ID: " + idParam + ", IDLIST: " + idlist);
        Set<String> ids = newHashSet(idParam);
        Iterables.addAll(ids, Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        logger.debug("Raw ids: {}", ids);
        return new SparqlToJsonString().performQuery(formatQuery(resolvePrefix(prefix, ids)));
    }

    private static Collection<String> resolvePrefix(String prefixString, Collection<String> ids) {
        Optional<String> prefix = fromNullable(prefixString);

        return prefix.isPresent() ? transform(ids, resolver(prefix.get())) : ids;
    }

    private static Function<String,String> resolver(final String prefix) {
        return new Function<String,String>() {
            public String apply(String s) {
                if (s.indexOf(':') == -1) {
                    return prefix + s;
                } else {
                    return s;
                }
            }
        };
    }
}
