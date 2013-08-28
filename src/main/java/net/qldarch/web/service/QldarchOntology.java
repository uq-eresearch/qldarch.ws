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

    public static final String DEFAULT_GRAPH = "http://qldarch.net/ns/rdf/2012-06/terms#" ;

    public static final URI RDF_TYPE =
        URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    private static final URI RDFS_RANGE = URI.create("http://www.w3.org/2000/01/rdf-schema#range");
    public static final OWL_OBJECT_PROPERTY =
        URI.create("http://www.w3.org/2002/07/owl#ObjectProperty");
    public static final URI XSD_STRING = URI.create("http://www.w3.org/2001/XMLSchema#string");
    private static final URI XSD_DATE = URI.create("http://www.w3.org/2001/XMLSchema#date");
    private static final URI XSD_INTEGER = URI.create("http://www.w3.org/2001/XMLSchema#integer");
    public static final URI XSD_BOOLEAN = URI.create("http://www.w3.org/2001/XMLSchema#boolean");

    public static final String DEFAULT_ENTITY_QUERY = 
        "@PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
        "@PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "select distinct ?entity ?prop ?value " +
        "from <%s> " +
        "where { " +
        "  ?entity rdfs:subClassOf qldarch:Entity . " +
        "  ?entity ?prop ?value . " +
        "}";

    public static final String DEFAULT_PROPERTIES_QUERY = 
        "@PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
        "@PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
        "select distinct ?predicate ?prop ?value " +
        "from <%s> " +
        "where { " +
        "  ?predicate rdf:type rdf:Property . " +
        "  ?predicate ?prop ?value . " +
        "}";

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
    private String entityQuery = DEFAULT_ENTITY_QUERY;
    private String entityGraph = DEFAULT_ENTITY_GRAPH;

    private String propertiesQuery = DEFAULT_PROPERTIES_QUERY;
    private String propertiesGraph = DEFAULT_PROPERTIES_GRAPH;

    private Map<URI, Multimap<URI, Object>> entities = null;
    private Map<URI, Multimap<URI, Object>> properties = null;

    private SesameConnectionPool connectionPool = null;

    public QldarchOntology() {}

    private interface MapLoader extends RepositoryOperation {
        public boolean isLoaded();
        public Map<URI, Multimap<URI, Object>> getLoadedMap();
    }

    private synchronized Map<URI, Multimap<URI,Object>> getEntities() {
        return getMap(new MapLoader() {
            public boolean isLoaded() {
                return (this.entities != null && this.entities.size() > 0);
            }

            public Map<URI, Multimap<URI, Object>> getLoadedMap() {
                QldarchOntology.this.entities;
            }

            public void perform(RepositoryConnection conn)
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

            public void perform(RepositoryConnection conn)
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

        getConnectionPool().performOperation(loader);

        if (loader.isLoaded()) {
            return loader.getLoadedMap();
        } else {
            throw new MetadataRepositoryException("Unable to load map");
        }
    }

    private Map<URI,MultiMap<URI,Object>>>
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
                return literal.calendarValue();
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

    public void setConnection(SesameConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public synchronized SesameConnectionPool getConnection() {
        if (this.connectionPool == null) {
            this.connectionPool = SesameConnectionPool.instance();
        }
        return this.connectionPool;
    }

    public Value convertObject(URI property, Object object) throws MetadataRespositoryException {
        if (!properties.containsKey(property)) {
            return new LiteralImpl(object.toString());
        } else {
            Multimap<URI, Object> propertyDesc = properties.get(property);

            if (propertyDesc.get(RDF_TYPE).contains(OWL_OBJECT_PROPERTY)) {
                try {
                    return new URIImpl(new URI(object.toString()));
                } catch (URISyntaxException eu) {
                    logger.debug("ObjectProperty value({}) could not be converted to URI",
                            object, eu);
                    return new LiteralImpl(object.toString());
                }
            } else {
                Collection<Object> ranges = properties.get(property).get(RDFS_RANGE);
                for (Object rng: ranges) {
                    if (XSD_STRING.equals(rng)) {
                        return new LiteralImpl(object.toString());
                    } else if (XSD_DATE.equals(rng)) {
                        try {
                            return new CalendarLiteralImpl(object.toString());
                        } catch (IllegalArgumentException ei) {
                            logger.debug("Range of date, but object did not convert {}", object, ei);
                            continue;
                        }
                    } else if (XSD_BOOLEAN.equals(rng)) {
                        if (object instanceof Boolean) {
                            return new BooleanLiteralImpl(object.booleanValue());
                        } else if (object instanceof String) {
                            String s = ((String)object).toLowerCase();
                            if (s.equals("true") || s.equals("false")) {
                                return new BooleanLiteralImpl(Boolean.parseBoolean(s));
                            }
                        }
                        logger.debug("Range of boolean, but object did not convert {}", object);
                        continue;
                    } else if (XSD_INTEGER.equals(rng)) {
                        if (object instanceof Number) {
                            return new IntegerLiteralImpl(
                                    BigInteger.valueOf(((Number)object).longValue()));
                        } else {
                            try {
                                return new IntegerLiteralImpl(new BigInteger(object.toString()));
                            } catch (NumberFormatException en) {
                                logger.debug("Range of int, but object did not convert {}", object, en);
                                continue;
                            }
                        }
                    }
                }

                logger.info("Input({}) did not match any range declaration in {}", object, ranges);

                return new LiteralImpl(object.toString());
            }
        }
    }
}
