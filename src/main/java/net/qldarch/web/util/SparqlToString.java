package net.qldarch.web.util;

import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SparqlToString {
    public static Logger logger = LoggerFactory.getLogger(SparqlToString.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";
    public static final String INDENT1 = "    ";
    public static final String INDENT2 = INDENT1 + INDENT1;
    public static final String INDENT3 = INDENT2 + INDENT1;

    Repository myRepository;
    RepositoryConnection conn;
    Exception initError;

    public SparqlToString() {
        try {
            initError = null;
            myRepository = new HTTPRepository("http://localhost:8080/openrdf-sesame",
                    "QldarchMetadataServer");
            myRepository.initialize();

            conn = myRepository.getConnection();
        } catch (Exception e) {
            initError = e;
        }
    }

    public String performQuery(String query) {
        TupleQueryResult result;
		try {
			result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
			
	        while (result.hasNext()) {
	            BindingSet bs = result.next();
	            Value r = bs.getValue("r");
	            
	            return r.toString();
	        }
		} catch (QueryEvaluationException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		} catch (MalformedQueryException e) {
			e.printStackTrace();
		}
        return null;
    }
}
