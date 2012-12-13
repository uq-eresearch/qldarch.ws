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

@Path("/system")
public class QuerySystemResource {
    public static Logger logger = LoggerFactory.getLogger(QuerySystemResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    @GET
    @Produces("application/json")
    public String performGet() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            Repository myRepository = new HTTPRepository("http://localhost:8080/openrdf-sesame",
                    "TestNativeInferencing");
            myRepository.initialize();

            RepositoryConnection conn = myRepository.getConnection();
            String query =
                "select ?s ?p ?o" +
                " from <http://qldarch.net/ns/rdf/2012/06/terms#>" +
                " where {" +
                " { ?s rdf:type owl:DatatypeProperty. ?s ?p ?o. }" +
                " union { ?s rdf:type owl:ObjectProperty. ?s ?p ?o. }" +
                " }";

            Map<String, Map<String, Value>> propertyGraph = new HashMap<String, Map<String, Value>>();
            try {
                TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    Value s = bs.getValue("s");
                    Value p = bs.getValue("p");
                    Value o = bs.getValue("o");
                    if (!propertyGraph.containsKey(s.toString())) {
                        propertyGraph.put(s.toString(), new HashMap<String, Value>());
                    }
                    Map<String, Value> property = propertyGraph.get(s.toString());

                    if (property.containsKey(p.toString())) {
                        logger.info("Multiple values found for ontology property {} on {}", p, s);
                    } else {
                        property.put(p.toString(), o);
                    }
                }

                pw.println("{");
                boolean firsta = true;
                for (String str : propertyGraph.keySet()) {
                    if (!firsta) {
                        pw.println(",");
                    } else {
                        firsta = false;
                    }

                    Map<String, Value> propertyMap = propertyGraph.get(str);
                    pw.println("    \"" + str + "\": {");
                    boolean firstb = true;
                    for (String prop : propertyMap.keySet()) {
                        if (!firstb) {
                            pw.println(",");
                        } else {
                            firstb = false;
                        }

                        Value v = propertyMap.get(prop);
                        pw.print("        \"" + prop + "\": ");
                        if (v instanceof Literal) {
                            Literal l = (Literal)v;
                            String datatype = l.getDatatype() == null ? "" : l.getDatatype().toString();
                            if (datatype.equals(XSD_BOOLEAN)) {
                                pw.print(l.booleanValue());
                            } else {
                                pw.print(l.toString());
                            }
                        } else {
                            pw.print("\"" + v.toString() + "\"");
                        }
                    }
                    pw.print("\n    }");
                }
                pw.println("\n}");
            } catch (QueryEvaluationException eq) {
                eq.printStackTrace(pw);
            } catch (MalformedQueryException em) {
                em.printStackTrace(pw);
            }
        } catch (RepositoryException er) {
            er.printStackTrace(pw);
        } catch (Exception e) {
            e.printStackTrace(pw);
        }
        pw.flush();
        return sw.toString();
    }

}
