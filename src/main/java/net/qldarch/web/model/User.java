package net.qldarch.web.model;

import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.util.SparqlToString;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.GregorianCalendar;
import java.util.Random;

public class User {
    public static Logger logger = LoggerFactory.getLogger(User.class);

    public static String USER_REFERENCE_GRAPH_FORMAT = "http://qldarch.net/users/%s/references";
    public static String USER_ANNOTATION_GRAPH_FORMAT = "http://qldarch.net/users/%s/annotations";
    public static String USER_COMPOUND_OBJECT_GRAPH_FORMAT = "http://qldarch.net/users/%s/compoundObjects";
    public static String USER_ENTITY_GRAPH_FORMAT = "http://qldarch.net/users/%s/entities";
    public static String USER_VOCABULARY_GRAPH_FORMAT = "http://qldarch.net/users/%s/vocabulary";
    public static String USER_EXPRESSION_GRAPH_FORMAT = "http://qldarch.net/users/%s/expressions";
    public static String USER_FILE_GRAPH_FORMAT = "http://qldarch.net/users/%s/files";
    public static String USER_URI_FORMAT = "http://qldarch.net/users/%s";

    private String username;
    
    // FIXME: Made public scope to allow testing.
    public User(String username) {
        this.username = username;
    }

    public static User currentUser() {
        Subject currentUser = SecurityUtils.getSubject();
        String username = (String)currentUser.getPrincipal();

        return new User(username);
    }

    public boolean isOwner(String id) {
    	String result = new SparqlToString().performQuery(getResourceGraph(id));
    	if (result.startsWith(String.format(USER_URI_FORMAT, username))) {
    		return true;
    	}
    	return false;
    }
    
    private static String getResourceGraph(String id) {
        String query = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?r " +
                "where {" +
                "  GRAPH ?r {" +
                "    BIND ( <" + id + "> AS ?s ) ." + 
                "    ?s ?p ?o." +
                "  }" +
                "}").toString();

        logger.debug("CompoundObject performing SPARQL query: {}", query);
        
        return query;
    }
    
    public boolean isAnon() {
        return username == null || username.isEmpty();
    }

    public String getUsername() {
        return username;
    }

    public URI getUserURI() {
        return URI.create(String.format(USER_URI_FORMAT, username));
    }

    public URI getReferenceGraph() {
        return URI.create(String.format(USER_REFERENCE_GRAPH_FORMAT, username));
    }

    public URI getAnnotationGraph() {
        return URI.create(String.format(USER_ANNOTATION_GRAPH_FORMAT, username));
    }

    public URI getCompoundObjectGraph() {
        return URI.create(String.format(USER_COMPOUND_OBJECT_GRAPH_FORMAT, username));
    }

    public URI getEntityGraph() {
        return URI.create(String.format(USER_ENTITY_GRAPH_FORMAT, username));
    }
    public URI getVocabularyGraph() {
        return URI.create(String.format(USER_VOCABULARY_GRAPH_FORMAT, username));
    }

    public URI getExpressionGraph() {
        return URI.create(String.format(USER_EXPRESSION_GRAPH_FORMAT, username));
    }

    public URI getFileGraph() {
        return URI.create(String.format(USER_FILE_GRAPH_FORMAT, username));
    }

    public URI newId(URI graphURI, URI type)
            throws MetadataRepositoryException {
        String shorttype = "UnknownTypeNS";
        for (URI ns : KnownPrefixes.getNamespaces()) {
            URI typeFrag = ns.relativize(type);
            if (!typeFrag.equals(type)) {
                if (typeFrag.getScheme() != null ||
                        typeFrag.getAuthority() != null ||
                        typeFrag.getQuery() != null) {
                    logger.warn("Unexpected resolved shorttype. type:{} ns:{} short:{}",
                            type, ns, typeFrag);
                    continue;
                }
                String fragment = typeFrag.getFragment();
                String path = typeFrag.getPath();
                if ((fragment == null || fragment.isEmpty()) && (path == null || path.isEmpty())) {
                    logger.info("No fragment or path found in shorttype. type:{} ns:{} short:{}",
                            type, ns, typeFrag);
                    continue;
                } else if (fragment == null || fragment.isEmpty()) {
                    shorttype = path;
                } else if (path == null || path.isEmpty()) {
                    shorttype = fragment;
                } else {
                    logger.info("Both fragment or path found in shorttype. " +
                        "type:{} ns:{} short:{} fragment:'{}' path:'{}'",
                            type, ns, typeFrag, fragment, path);
                }
            }
        }
        
        logger.info("Resolving {} against {}", shorttype, graphURI);
        URI entityBase = graphURI.resolve(shorttype);

        String id = getNextIdForUser(graphURI);

        try {
            return new URI(entityBase.getScheme(), entityBase.getSchemeSpecificPart(), id);
        } catch (URISyntaxException eu) {
            logger.error("Invalid URI generated by newAnnotationId", eu);
            throw new MetadataRepositoryException("Invalid URI generated by newAnnotationId", eu);
        }
    }

    private static long START_DATE =
        new GregorianCalendar(2012, 1, 1, 0, 0, 0).getTime().getTime();
    private static Random random = new Random();

    private synchronized String getNextIdForUser(URI graphURI) {
        long delta = System.currentTimeMillis() - START_DATE;
        try {
            Thread.sleep(1);
        } catch (InterruptedException ei) {
            logger.warn("ID delay interrupted for {}", graphURI.toString(), ei);
        }
            
        return Long.toString(delta);
    }
}
