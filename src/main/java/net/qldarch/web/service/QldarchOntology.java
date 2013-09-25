package net.qldarch.web.service;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;
import org.openrdf.model.datatypes.XMLDatatypeUtil;
import org.openrdf.model.impl.BooleanLiteralImpl;
import org.openrdf.model.impl.CalendarLiteralImpl;
import org.openrdf.model.impl.DecimalLiteralImpl;
import org.openrdf.model.impl.IntegerLiteralImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

public class QldarchOntology {
    public static Logger logger = LoggerFactory.getLogger(QldarchOntology.class);

    public static final String DEFAULT_ENTITY_GRAPH = "http://qldarch.net/ns/rdf/2012-06/terms#" ;
    public static final String DEFAULT_PROPERTIES_GRAPH = DEFAULT_ENTITY_GRAPH;

    public static final URI RDF_TYPE =
        URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    private static final URI RDFS_RANGE = URI.create("http://www.w3.org/2000/01/rdf-schema#range");
    public static final URI OWL_OBJECT_PROPERTY =
        URI.create("http://www.w3.org/2002/07/owl#ObjectProperty");
    public static final URI XSD_STRING = URI.create("http://www.w3.org/2001/XMLSchema#string");
    private static final URI XSD_DATE = URI.create("http://www.w3.org/2001/XMLSchema#date");
    private static final URI XSD_INTEGER = URI.create("http://www.w3.org/2001/XMLSchema#integer");
    private static final URI XSD_DECIMAL = URI.create("http://www.w3.org/2001/XMLSchema#decimal");
    public static final URI XSD_BOOLEAN = URI.create("http://www.w3.org/2001/XMLSchema#boolean");

    public static final String DEFAULT_ENTITY_QUERY = 
        "PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "select distinct ?s ?p ?o " +
        "from <%s> " +
        "where { " +
        "  ?s rdfs:subClassOf qldarch:Entity . " +
        "  ?s ?p ?o . " +
        "}";

    public static final String DEFAULT_PROPERTIES_QUERY = 
        "PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
        "select distinct ?s ?p ?o " +
        "from <%s> " +
        "where { " +
        "  ?s rdf:type rdf:Property . " +
        "  ?s ?p ?o . " +
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
    private Multimap<URI, URI> entitiesByType = null;
    private Map<URI, Multimap<URI, Object>> properties = null;

    private SesameConnectionPool connectionPool = null;

    public QldarchOntology() {}

    public Multimap<URI,Object> findByURI(URI entityTypeURI) throws MetadataRepositoryException {
        return this.getEntities().get(entityTypeURI);
    }

    public List<URI> findByRdfType(URI type) throws MetadataRepositoryException {
        List<URI> result = Lists.newArrayList();
        for (URI entity : this.getEntitiesByType().get(type)) {
            result.add(entity);
        }
        return result;
    }

    private interface MapLoader extends RepositoryOperation {
        public boolean isLoaded();
        public Map<URI, Multimap<URI, Object>> getLoadedMap();
    }

    private synchronized Map<URI, Multimap<URI,Object>> getEntities()
            throws MetadataRepositoryException {
        return getMap(new MapLoader() {
            public boolean isLoaded() {
                return (QldarchOntology.this.entities != null &&
                    QldarchOntology.this.entities.size() > 0);
            }

            public Map<URI, Multimap<URI, Object>> getLoadedMap() {
                return QldarchOntology.this.entities;
            }

            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                QldarchOntology.this.entities = loadStatements(conn,
                    QldarchOntology.this.getEntityQuery(),
                    QldarchOntology.this.getEntityGraph());
            }
        });
    }

    private synchronized Map<URI, Multimap<URI,Object>> getProperties()
            throws MetadataRepositoryException {
        return getMap(new MapLoader() {
            public boolean isLoaded() {
                return (QldarchOntology.this.properties != null &&
                    QldarchOntology.this.properties.size() > 0);
            }

            public Map<URI, Multimap<URI, Object>> getLoadedMap() {
                return QldarchOntology.this.properties;
            }

            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                QldarchOntology.this.properties = loadStatements(conn,
                    QldarchOntology.this.getPropertiesQuery(),
                    QldarchOntology.this.getPropertiesGraph());
            }
        });
    }

    /**
     * @return A multimap of entities indexed by rdf:type.
     */
    private synchronized Multimap<URI, URI> getEntitiesByType()
            throws MetadataRepositoryException {
        if (this.entitiesByType == null) {
            this.entitiesByType = indexByType(this.getEntities());
        }
        return this.entitiesByType;
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

    private Map<URI,Multimap<URI,Object>>
            loadStatements(RepositoryConnection conn, String baseQuery, String graph)
            throws RepositoryException, MetadataRepositoryException {
        logger.trace("baseQuery: {}, graph: {}", baseQuery, graph);
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
                URI predicate = validatePredicate(rawPred);
                Object object = validateObject(rawObject);

                if (subject == null || predicate == null || object == null) {
                    logger.debug("Error in ontology query result. Skipping entry ({}, {}, {})",
                            rawSubject, rawPred, rawObject);
                    continue;
                }

                if (!map.containsKey(subject)) {
                    // Extra line required to compensate for java's poor type inferencing.
                    Multimap<URI, Object> mm = HashMultimap.create();
                    map.put(subject, mm);
                } 

                map.get(subject).put(predicate, object);
            }

            return map;
        } catch (MalformedQueryException em) {
            logger.warn("Failed to load ontology from store", em);
            throw new MetadataRepositoryException("Failed to load ontology from store", em);
        } catch (QueryEvaluationException eq) {
            logger.warn("Failed to load ontology from store", eq);
            throw new MetadataRepositoryException("Failed to load ontology from store", eq);
        }
    }

    private URI validateSubject(Value rawSubject) {
        if (rawSubject == null) {
            logger.warn("null subject from ontology query: {}", rawSubject);
            return null;
        } else if (!(rawSubject instanceof org.openrdf.model.URI)) {
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
            logger.warn("Blank-node object in ontology {}", rawObject);
            return null;
        } else if (rawObject instanceof Literal) {
            Literal literal = (Literal)rawObject;
            org.openrdf.model.URI datatype = literal.getDatatype();
            if (datatype == null) {
                return literal.toString();
            } else if (XMLDatatypeUtil.isCalendarDatatype(datatype)) {
                return literal.calendarValue();
            } else if (XMLDatatypeUtil.isIntegerDatatype(datatype)) {
                return literal.intValue();
            } else if (XMLDatatypeUtil.isDecimalDatatype(datatype)) {
                return literal.decimalValue();
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
     * Note: This will force a reload of the entities map
     */
    public synchronized void setEntityGraph(String graph) {
        this.entities = null;
        this.entityGraph = graph;
    }

    public String getEntityGraph() {
        return this.entityGraph;
    }

    /**
     * Set the query for entities from the ontology.
     *
     * Note: This will force a reload of the entities map
     */
    public synchronized void setEntityQuery(String query) {
        this.entities = null;
        this.entityQuery = query;
    }

    public String getEntityQuery() {
        return this.entityQuery;
    }

    /**
     * Set the graph containing the ontology properties.
     *
     * Note: This will force a reload of the properties map
     */
    public synchronized void setPropertiesGraph(String graph) {
        this.properties = null;
        this.propertiesGraph = graph;
    }

    public String getPropertiesGraph() {
        return this.propertiesGraph;
    }

    /**
     * Set the query for entities from the ontology.
     *
     * Note: This will force a reload of the properties map
     */
    public synchronized void setPropertiesQuery(String query) {
        this.properties = null;
        this.propertiesQuery = query;
    }

    public String getPropertiesQuery() {
        return this.propertiesQuery;
    }

    public void setConnectionPool(SesameConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public synchronized SesameConnectionPool getConnectionPool() {
        if (this.connectionPool == null) {
            this.connectionPool = SesameConnectionPool.instance();
        }
        return this.connectionPool;
    }

    // FIXME: This MUST be broken up into a utility class of its own so it can be tested properly.
    public Value convertObject(URI property, Object object) throws MetadataRepositoryException {
        logger.trace("Converting {} :: {} of class {}", property, object, object.getClass());
        Map<URI, Multimap<URI, Object>> properties = this.getProperties();

        if (!properties.containsKey(property)) {
            return new LiteralImpl(object.toString());
        } else {
            Multimap<URI, Object> propertyDesc = properties.get(property);

            if (propertyDesc.get(RDF_TYPE).contains(OWL_OBJECT_PROPERTY)) {
                try {
                    if (object instanceof RdfDescription) {
                        RdfDescription rdf = (RdfDescription)object;
                        URI uri = rdf.getURI();
                        if (uri == null) {
                            logger.error("Storage of blank nodes not currently supported: {} => {}",
                                    property, object);
                            throw new MetadataRepositoryException(
                                    "Storage of blank nodes not currently supported");
                        } 

                        return new URIImpl(uri.toString());
                    } else {
                        return new URIImpl(object.toString());
                    }
                } catch (IllegalArgumentException ei) {
                    logger.debug("ObjectProperty value({}) could not be converted to URI",
                            object, ei);
                    return new LiteralImpl(object.toString());
                }
            } else {
                Collection<Object> ranges = properties.get(property).get(RDFS_RANGE);
                for (Object rng: ranges) {
                    if (XSD_STRING.equals(rng)) {
                        return new LiteralImpl(object.toString());
                    } else if (XSD_DATE.equals(rng)) {
                        if (object instanceof XMLGregorianCalendar) {
                            return new CalendarLiteralImpl((XMLGregorianCalendar)object);
                        } else if (object instanceof Date) {
                            GregorianCalendar cal = new GregorianCalendar();
                            cal.setTimeInMillis(((Date)object).getTime());
                            try {
                                return new CalendarLiteralImpl(
                                    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
                            } catch (DatatypeConfigurationException ed0) {
                                logger.debug("Range of date, but object did not convert {}",
                                        object, ed0);
                                continue;
                            }
                        } else if (object instanceof GregorianCalendar) {
                            try {
                                return new CalendarLiteralImpl(
                                        DatatypeFactory.newInstance().newXMLGregorianCalendar(
                                            (GregorianCalendar)object));
                            } catch (DatatypeConfigurationException ed1) {
                                logger.debug("Range of date, but object did not convert {}",
                                        object, ed1);
                                continue;
                            }
                        } else {
                            try {
                                return new CalendarLiteralImpl(
                                        XMLDatatypeUtil.parseCalendar(object.toString()));
                            } catch (IllegalArgumentException ei) {
                                logger.debug("Range of date, but object did not convert {}",
                                        object, ei);
                                continue;
                            }
                        }
                    } else if (XSD_BOOLEAN.equals(rng)) {
                        if (object instanceof Boolean) {
                            return new BooleanLiteralImpl(((Boolean)object).booleanValue());
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
                                logger.debug("Range of int, but object did not convert {}",
                                        object, en);
                                continue;
                            }
                        }
                    } else if (XSD_DECIMAL.equals(rng)) {
                        try {
                            return new DecimalLiteralImpl(
                                    new BigDecimal(object.toString()));
                        } catch (NumberFormatException en) {
                            logger.debug("Range of decimal, but object did not convert {}",
                                    object, en);
                            continue;
                        }
                    }
                }

                logger.trace("Property {} has class {}, Object {} has class {}",
                        property, property.getClass(), object, object.getClass());
                logger.trace("Property {} has description: {}", property, propertyDesc);
                logger.info(
                        "Object({}) for property({}) did not match any range declaration in {}",
                        object, property, ranges);

                return new LiteralImpl(object.toString());
            }
        }
    }

    private Multimap<URI, URI> indexByType(Map<URI, Multimap<URI, Object>> entities)
            throws MetadataRepositoryException {
        Multimap<URI, URI> byType = HashMultimap.create();

        for (URI entity : entities.keySet()) {
            Iterable<URI> types = Iterables.filter(entities.get(entity).get(RDF_TYPE), URI.class);
            for (URI type : types) {
                byType.put(type, entity);
            }
        }

        return byType;
    }
}
