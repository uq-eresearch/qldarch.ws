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

import java.net.URI;
import java.net.URISyntaxException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;

public class QldarchOntology {
    public static Logger logger = LoggerFactory.getLogger(QldarchOntology.class);

    public static final String DEFAULT_SERVER_URI = "http://localhost:8080/openrdf-sesame";
    public static final String DEFAULT_REPO_NAME = "QldarchMetadataServer";
    public static final String DEFAULT_GRAPH = "http://qldarch.net/ns/rdf/2012-06/terms#" ;

    public static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    public static final String DESCRIBE_ENTITIES_QUERY = 
        "@PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
        "@PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "select distinct ?entity ?prop ?value " +
        "from <%s> " +
        "where { " +
        "  ?entity rdfs:subClassOf qldarch:Entity . " +
        "  ?entity ?prop ?value . " +
        "}";

    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

    /*
     * Factory field/method.
     */
    private static QldarchOntology singleton;

    public static synchronized QldarchOntology instance() {
        if (singleton != null) {
            return singleton;
        } else {
            singleton = new QldarchOntology();
            return singleton;
        }
    }

    /*
     * Fields
     */
    private String serverURI = DEFAULT_SERVER_URI;
    private String repoName = DEFAULT_REPO_NAME;

    private String entityQuery = DEFAULT_ENTITY_QUERY;
    private String entityGraph = DEFAULT_ENTITY_GRAPH;

    private String propertiesQuery = DEFAULT_PROPERTIES_QUERY;
    private String propertiesGraph = DEFAULT_PROPERTIES_GRAPH;

    private Map<URI, Multimap<URI, Object>> entities = null;
    private Map<URI, Multimap<URI, Object>> properties = null;

    public QldarchOntology() {}

    private interface MapLoader {
        public boolean isLoaded();
        public Map<URI, Multimap<URI, Object>> getLoadedMap();
        public void loadMap(RepositoryConnection conn)
                throws RepositoryException, MetadataRepositoryException;
    }

    private synchronized Map<URI, Multimap<URI,Object>> getEntities() {
        return getMap(new MapLoader() {
            public boolean isLoaded() {
                return (this.entities != null && this.entities.size() > 0);
            }

            public Map<URI, Multimap<URI, Object>> getLoadedMap() {
                QldarchOntology.this.entities;
            }

            public void loadMap(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                QldarchOntology.this.entities = loadStatements(conn,
                    QldarchOntology.this.getEntityQuery(),
                    QldarchOntology.this.getEntityGraph());
            }
        }
    }

    private synchronized Map<URI, Multimap<URI,Object>> getProperties() {
        return getMap(new MapLoader() {
            public boolean isLoaded() {
                return (this.properties != null && this.properties.size() > 0);
            }

            public Map<URI, Multimap<URI, Object>> getLoadedMap() {
                QldarchOntology.this.properties;
            }

            public void loadMap(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                QldarchOntology.this.properties = loadStatements(conn,
                    QldarchOntology.this.getPropretyQuery(),
                    QldarchOntology.this.getPropretyGraph());
            }
        }
    }

    private synchronized Map<URI, Multimap<URI,Object>> getMap(MapLoader loader) 
            throws MetadataRepositoryException {
        if (loader.isLoaded()) {
            return loader.getLoadedMap();
        }

        Throwable error = null;

        Repository repo = null;
        RepositoryConnection conn = null;
        try {
            repo = new HTTPRepository(this.getServerURI(), this.getRepoName());
            repo.initialize();

            conn = repo.getConnection();

            loader.loadMap(conn);
        } catch (Exception ei) {
            logger.error("Unable to initialize entities from store", ei);
        } finally {
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close();
                }
            } catch (RepositoryException erc) {
                logger.warn("Error closing repository connection", erc);
                error = erc;
            } finally {
                try {
                    if (repo != null && repo.isInitialized()) {
                        repo.shutDown();
                    }
                } catch (RepositoryException er) {
                    logger.warn("Error shutting down repository reference", er);
                    if (error != null) error = er;
                }
            }
        }

        if (loader.isLoaded()) {
            return loader.getLoadedMap();
        } else {
            throw new MetadataRepositoryException("Unable to load map");
        }
    }

    Map<URI,MultiMap<URI,Object>>>
            loadStatements(RepositoryConnection conn, String baseQuery, String graph)
            throws RepositoryException, MetadataRepositoryException {
        String query = String.format(baseQuery, graph);
        logger.trace("Loading entities with query: {}", query);

        try {
            TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
            Map<URI, Multimap<URI, Object>> map = Maps.newHashMap();

            while (result.hasNext()) {
                BindingSet bs = result.next();
                Value rawSubject = bs.getValue("s");
                Value rawPred = bs.getValue("p");
                Value rawObject = bs.getValue("o");

                URI subject = validateSubject(rawSubject);
                URI predicate = validatePred(rawPred);
                Object object = validateObject(rawObject);

                if (subject == null || predicate == null || object == null) {
                    logger.debug("Error in ontology query result. Skipping entry ({}, {}, {})",
                            rawSubject, rawPred, rawObject);
                    continue;
                }

                if (!map.containsKey(subject)) {
                    map.put(subject, HashMultimap.create());
                } 

                map.get(subject).put(predicate, object);
            }

            return map;
        } catch (MalformedQueryException em) {
            throw new MetadataRepositoryException("Failed to load ontology from store", em);
        } catch (QueryEvaluationException eq) {
            throw new MetadataRepositoryException("Failed to load ontology from store", eq);
        }
    }

    private URI validateSubject(Value rawSubject) {
        if (rawSubject == null) {
            logger.warn("null subject from ontology query: {}", rawSubject);
            return null;
        } else if (!(rawEntity instanceof org.openrdf.model.URI)) {
            logger.warn("subject({}) is not a uri in ontology", rawSubject);
            return null;
        }

        try {
            return new URI(rawSubject.toString());
        } catch (URISyntaxException eu) {
            logger.warn("Invalid uri syntax for subject {}", rawSubject, eu);
            return null;
        }
    }

    private URI validatePredicate(Value rawPred) {
        if (rawPred == null) {
            logger.warn("null predicate from ontology query {}", rawPred);
            return null;
        } else if (!(rawPred instanceof org.openrdf.model.URI)) {
            logger.warn("predicate({}) is not a uri in entities", rawPred);
            return null;
        }

        try {
            return new URI(rawPred.toString());
        } catch (URISyntaxException eu) {
            logger.warn("Invalid uri syntax for predicate {}", rawPred, eu);
            return null;
        }
    }

    private Object validateObject(Value rawObject) {
        if (rawObject == null) {
            logger.warn("null object from ontology query {}", rawObject);
            return null;
        } else if (rawObject instanceof org.openrdf.model.URI) {
            try {
                return new URI(rawObject.toString());
            } catch (URISyntaxException eu) {
                logger.warn("Invalid uri syntax for returned object {}", rawObject, eu);
                return null;
            }
        } else if (rawObject instanceof BNode) {
            logger.warn("Blank-node object in ontology {}", rawObject, eu);
            return null;
        } else if (rawObject instanceof Literal) {
            Literal literal = (Literal)rawObject;
            URI datatype = literal.getDatatype();
            if (XMLDatatypeUtil.isCalendarDatatype(datatype)) {
                return literal.calendarValue().toGregorianCalendar().getTime();
            } else if (XMLDatatypeUtil.isIntegerDatatype(datatype)) {
                return literal.intValue();
            } else if (XMLDatatypeUtil.isValidBoolean(literal.toString())) {
                return literal.booleanValue();
            } else {
                return literal.toString();
            }
        } else {
            logger.warn("Unknown value type in ontology: {}", rawObject);
            return null;
        }
    }

    /**
     * Set the URI used to contact the metadata server.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public synchronized void setServerURI(String serverURI) {
        this.entities = null;
        this.properties = null;
        this.serverURI = serverURI;
    }

    public String getServerURI() {
        return this.serverURI;
    }

    /**
     * Set the name of the repository queried containing the prefix configuration.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public synchronized void setRepoName(String repoName) {
        this.entities = null;
        this.properties = null;
        this.repoName = repoName;
    }

    public String getRepoName() {
        return this.repoName;
    }

    /**
     * Set the graph containing the ontology entities.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public synchronized void setEntityGraph(String graph) {
        this.entities = null;
        this.entityGraph = graph;
    }

    public String getEntityGraph() {
        return this.entityGraph;
    }

    /**
     * Set the graph containing the ontology properties.
     *
     * Note: This will force a reload of the properties map on the next 
     *   lookup of a prefix or uri.
     */
    public synchronized void setPropertiesGraph(String graph) {
        this.properties = null;
        this.propertiesGraph = graph;
    }

    public String getPropertiesGraph() {
        return this.propertiesGraph;
    }

}
