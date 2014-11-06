package net.qldarch.web.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SparqlToJsonString {
    public static Logger logger = LoggerFactory.getLogger(SparqlToJsonString.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";
    public static final String INDENT1 = "    ";
    public static final String INDENT2 = INDENT1 + INDENT1;
    public static final String INDENT3 = INDENT2 + INDENT1;

    public static class PGraph extends HashMap<String, Map<String, List<Value>>> {
      private static final long serialVersionUID = 1L;

      public void add(String s, String p, Value o) {
        Map<String, List<Value>> property = get(s);
        if(property == null) {
          property = Maps.newHashMap();
          put(s, property);
        }
        List<Value> l = property.get(p);
        if(l == null) {
          l = Lists.newArrayList();
          property.put(p, l);
        }
        l.add(o);
      }
    }

    Repository myRepository;
    RepositoryConnection conn;
    Exception initError;

    public SparqlToJsonString() {
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

    public PGraph execute(String query)
        throws QueryEvaluationException, RepositoryException, MalformedQueryException {
      return execute(query, new PGraph());
    }

    private PGraph execute(String query, PGraph pgraph)
        throws QueryEvaluationException, RepositoryException, MalformedQueryException {
      TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
      while (result.hasNext()) {
        BindingSet bs = result.next();
        Value s = bs.getValue("s");
        Value p = bs.getValue("p");
        Value o = bs.getValue("o");
        pgraph.add(s.toString(), p.toString(), o);
      }
      return pgraph;
    }

    // FIXME why return the stacktrace on failure but pretend success?
    public String performQuery(String query) {
      if(initError != null) {
        return toString(initError);
      }
      try {
        return serialise(execute(query));
      } catch (Exception e) {
        return toString(e);
      }
    }

    private String toString(Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      pw.flush();
      return sw.toString();
    }

    public String serialise(Map<String, Map<String, List<Value>>> propertyGraph) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      if (initError != null) {
          initError.printStackTrace(pw);
          pw.flush();
          return sw.toString();
      }
      pw.println("{");
      boolean firsta = true;
      for (String str : propertyGraph.keySet()) {
          if (!firsta) {
              pw.println(",");
          } else {
              firsta = false;
          }
          Map<String, List<Value>> propertyMap = propertyGraph.get(str);
          pw.println(INDENT1 + "\"" + str + "\": {");
          pw.print(INDENT2 + "\"uri\": \"" + str + "\"");
          for (String prop : propertyMap.keySet()) {
              pw.println(",");
              pw.print(INDENT2 + "\"" + prop + "\": ");
              List<Value> values = propertyMap.get(prop);
              if (values.size() == 1) {
                  printValue(pw, values.get(0));
              } else {
                  pw.println("[");
                  boolean firstl = true;
                  for (Value v : values) {
                      if (!firstl) {
                          pw.println(",");
                      } else {
                          firstl = false;
                      }
                      pw.print(INDENT3);
                      printValue(pw, v);
                  }
                  pw.print("\n" + INDENT2 + "]");
              }
          }
          pw.print("\n" + INDENT1 + "}");
      }
      pw.println("\n}");
      pw.flush();
      return sw.toString();
    }

    private void printValue(PrintWriter pw, Value v) {
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

    private String cleanString(String in) {
        return in.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\\\\", "\\\\\\\\")
            .replaceAll("\"", "\\\\\"").replaceAll("\t", "").trim();
    }
}
