package net.qldarch.web.deprecated;

import net.qldarch.web.util.SparqlToJsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/contentSummary")
public class ContentSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(ContentSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";


    @GET
    @Produces("application/json")
    public String performGet() {
        return new SparqlToJsonString().performQuery(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o from <http://qldarch.net/ns/rdf/2012-06/terms#> where {" +
                "  ?s a owl:Class ." +
                "  ?s :toplevel true ." +
                "  ?s ?p ?o ." +
                " }");
    }
}
