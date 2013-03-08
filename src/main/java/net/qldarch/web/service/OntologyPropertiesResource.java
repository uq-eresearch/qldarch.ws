package net.qldarch.web.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
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

@Path("/properties")
public class OntologyPropertiesResource {
    public static Logger logger = LoggerFactory.getLogger(OntologyPropertiesResource.class);

    @GET
    @Produces("application/json")
    public String performGet() {
        return new SparqlToJsonString().performQuery(
                "select ?s ?p ?o" +
                " from <http://qldarch.net/ns/rdf/2012-06/terms#>" +
                " where {" +
                " { ?s rdf:type owl:DatatypeProperty. ?s ?p ?o. }" +
                " union { ?s rdf:type owl:ObjectProperty. ?s ?p ?o. }" +
                " }");
    }

}
