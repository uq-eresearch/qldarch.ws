package net.qldarch.web.service;

import com.google.common.base.Joiner;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

@Path("/fileSummary")
public class FileSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(FileSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public String formatQuery(Set<String> ids) {
        StringBuilder builder = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select ?s ?p ?o from <http://qldarch.net/ns/omeka-export/2013-02-06> where {" +
                "  ?s a :DigitalFile ." +
                "  ?s ?p ?o ." +
                "  BIND ?s (<");

        String query = Joiner.on(">) (<").appendTo(builder, ids).append(">) }").toString();
        logger.debug("FileSummaryResource performing SPARQL query: {}", query);
        
        return query;
    }

    @GET
    @Produces("application/json")
    public String performGet(@QueryParam("IDLIST") Set<String> ids) {
        return new SparqlToJsonString().performQuery(formatQuery(ids));
    }
}
