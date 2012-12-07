package net.qldarch.web.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.UriBuilder;

import org.openrdf.query.Binding;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/system")
public class QuerySystemResource {
    public static Logger logger = LoggerFactory.getLogger(QuerySystemResource.class);
    
    @GET
    @Produces("text/plain")
    public String hello() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            Repository myRepository = new HTTPRepository("http://localhost:8080/openrdf-sesame", "SYSTEM");
            myRepository.initialize();

            RepositoryConnection conn = myRepository.getConnection();
            String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o. }";
            pw.print("Result: \n");
            try {
                TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
                while (result.hasNext()) {
                    for (Binding b : result.next()) {
                        pw.print("\t" + b.getName() + ": " + b.getValue());
                    }
                    pw.print("\n");
                }
            } catch (QueryEvaluationException eq) {
                eq.printStackTrace(pw);
            } catch (MalformedQueryException em) {
                em.printStackTrace(pw);
            }
        } catch (RepositoryException er) {
            er.printStackTrace(pw);
        }
        pw.flush();
        return sw.toString();
    }

}
