package net.qldarch.web.deprecated;

import net.qldarch.web.util.SparqlToJsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/lineDrawingSummary")
public class LineDrawingSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(LineDrawingSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";


    @GET
    @Produces("application/json")
    public String performGet() {
        return new SparqlToJsonString().performQuery(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o from <http://qldarch.net/ns/omeka-export/2013-02-06> where {" +
                "  ?s a :LineDrawing ." +
                "  ?s ?p ?o ." +
                " }");
    }
}
