package net.qldarch.web.service;

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
import java.util.HashMap;
import java.util.Map;

public class SparqlToJsonString {
    public static Logger logger = LoggerFactory.getLogger(SparqlToJsonString.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

    Repository myRepository;
    RepositoryConnection conn;
    Exception initError;

    public SparqlToJsonString() {
        try {
            initError = null;
            myRepository = new HTTPRepository("http://localhost:8080/openrdf-sesame", "QldarchMetadataServer");
            myRepository.initialize();

            conn = myRepository.getConnection();
        } catch (Exception e) {
            initError = e;
        }
    }

    public String cleanString(String in) {
        return in.replaceAll("\n", "").replaceAll("\r", "");
    }

    public String performQuery(String query) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        if (initError != null) {
            initError.printStackTrace(pw);
            pw.flush();

            return sw.toString();
        }

        try {
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
                    pw.print("        \"uri\": \"" + str + "\"");
                    for (String prop : propertyMap.keySet()) {
                        pw.println(",");

                        Value v = propertyMap.get(prop);
                        pw.print("        \"" + prop + "\": ");
                        if (v instanceof Literal) {
                            Literal l = (Literal)v;
                            String datatype = l.getDatatype() == null ? "" : l.getDatatype().toString();
                            if (datatype.equals(XSD_BOOLEAN)) {
                                pw.print(l.booleanValue());
                            } else if (datatype.equals(XSD_INTEGER)) {
                                pw.print(l.integerValue().intValue()); // Note: This truncates, but I don't have time to fix that atm.
                            } else {
                                pw.print("\"" + cleanString(l.getLabel()) + "\"");
                            }
                        } else {
                            pw.print("\"" + cleanString(v.toString()) + "\"");
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
