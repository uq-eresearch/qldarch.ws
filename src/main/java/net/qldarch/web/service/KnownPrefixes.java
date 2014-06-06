package net.qldarch.web.service;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import org.openrdf.model.Literal;
import org.openrdf.model.Value;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class KnownPrefixes {
    public static Logger logger = LoggerFactory.getLogger(KnownPrefixes.class);

    public static final String DEFAULT_SERVER_URI = "http://localhost:8080/openrdf-sesame";
    public static final String DEFAULT_REPO_NAME = "QldarchMetadataServer";
    public static final String DEFAULT_GRAPH = "http://qldarch.net/ns/rdf/2013-08/internal#";
    public static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    public static final String PREFIX_MAP_QUERY_FORMAT = 
        "PREFIX qaint: <http://qldarch.net/ns/rdf/2013-08/internal#> " +
        "select distinct ?prefix ?uri " + 
        "from <%s> " +
        "where { " +
        "  ?uri qaint:usesPrefix ?prefix . " +
        "}";

    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";

    private static final Pattern PREFIXED_URI =
        Pattern.compile("\\A([a-zA-Z][a-zA-Z0-9+-.]*):(.*)\\z");

    public static URI resolve(String uriString) throws MetadataRepositoryException {
        if (uriString == null) throw new IllegalArgumentException("URI cannot be null");
        Matcher matcher = PREFIXED_URI.matcher(uriString);

        if (!matcher.matches()) {
            try {
                return new URI(uriString);
            } catch (URISyntaxException eu0) {
                logger.debug("URIString fails URI prefix validation", eu0);
                throw new MetadataRepositoryException(
                        "URIString fails URI prefix validation", eu0);
            }
        }

        if (matcher.groupCount() != 2) {
            logger.debug("Incorrect number of groups({}) match {}",
                    matcher.groupCount(), uriString);
            throw new MetadataRepositoryException("Incorrect number of groups match");
        }

        logger.debug("Fetching prefix URI matching {}", matcher.group(1));

        URI prefix = KnownPrefixes.instance().getURI(matcher.group(1));
        if (prefix == null) {
            try {
                return new URI(uriString);
            } catch (URISyntaxException eu1) {
                logger.debug("Attempt to resolve invalid URI {}", uriString, eu1);
                throw new MetadataRepositoryException("Attempt to resolve invalid URI", eu1);
            }
        } else {
            try {
                URI suffix = (prefix.getFragment() == null) ?
                    new URI(matcher.group(2)) :
                    new URI("#" + matcher.group(2));
                return prefix.resolve(suffix);
            } catch (URISyntaxException eu2) {
                logger.debug("Suffix was not a valid relative URI {}", matcher.group(2), eu2);
                throw new MetadataRepositoryException(
                        "Invalid URI in resolution against prefix", eu2);
            }
        }
    }

    public static Function<String,Optional<URI>> resolver() {
        return new Function<String,Optional<URI>>() {
            public Optional<URI> apply(String s) {
                try {
                    logger.trace("Resolving string: {}", s);
                    if (s != null && !s.isEmpty()) {
                        return Optional.of(KnownPrefixes.resolve(s));
                    } else {
                        return Optional.absent();
                    }
                } catch (MetadataRepositoryException em) {
                    logger.debug("Unable to resolve {}, returning null from resolver", s);
                    return Optional.absent();
                }
            }
        };
    }

    public static Set<URI> getNamespaces() throws MetadataRepositoryException {
        return KnownPrefixes.instance().getMap().values();
    }

    /*
     * Factory field/method.
     */
    private static KnownPrefixes singleton;

    public static synchronized KnownPrefixes instance() {
        if (singleton != null) {
            return singleton;
        } else {
            singleton = new KnownPrefixes();
            return singleton;
        }
    }

    /*
     * Fields
     */
    private String serverURI = DEFAULT_SERVER_URI;
    private String repoName = DEFAULT_REPO_NAME;
    private String graph = DEFAULT_GRAPH;
    private BiMap<String, URI> prefixes = null;

    public KnownPrefixes() {}

    public String getPrefix(URI uri) throws MetadataRepositoryException {
        if (uri == null) throw new IllegalArgumentException("Prefix URI cannot be null");
        return this.getMap().inverse().get(uri);
    }
    
    public URI getURI(String prefix) throws MetadataRepositoryException {
        if (prefix == null) throw new IllegalArgumentException("Prefix string cannot be null");
        return this.getMap().get(prefix);
    }

    private synchronized BiMap<String, URI> getMap() throws MetadataRepositoryException {
        if (this.prefixes != null && this.prefixes.size() > 0) {
            return this.prefixes;
        }

        Throwable error = null;

        Repository repo = null;
        RepositoryConnection conn = null;
        try {
            repo = new HTTPRepository(this.getServerURI(), this.getRepoName());
            repo.initialize();

            conn = repo.getConnection();

            this.prefixes = loadMap(conn);
        } catch (Exception ei) {
            logger.error("Unable to initialize known-prefixes from store", ei);
            this.prefixes = loadDefaults();
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

        if (this.prefixes != null && this.prefixes.size() > 0) {
            return this.prefixes;
        } else {
            throw new MetadataRepositoryException("Unable to load known prefixes", error);
        }
    }

    private BiMap<String, URI> loadMap(RepositoryConnection conn)
            throws RepositoryException, MetadataRepositoryException {
        String query = String.format(PREFIX_MAP_QUERY_FORMAT, this.getGraph());
        logger.trace("Loading prefix map with query: {}", query);

        try {
            TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();

            BiMap<String, URI> map = HashBiMap.create();

            while (result.hasNext()) {
                BindingSet bs = result.next();
                Value rawPrefix = bs.getValue("prefix");
                Value rawURI = bs.getValue("uri");

                String prefix = validatePrefix(rawPrefix, rawURI);
                URI uri = validateURI(rawURI, rawPrefix);

                if (prefix == null || uri == null) {
                    logger.debug("Error in prefix query result. Skipping entry ({}, {})",
                            rawPrefix, rawURI);
                    continue;
                }

                if (map.containsKey(prefix)) {
                    logger.debug("Duplicate prefix string in result " + prefix + ", first uri: " 
                    		+ map.get(prefix) + ", second uri: " + uri);
                    continue;
                } 
                if (map.containsValue(uri)) {
                    logger.debug("Duplicate uri in result " + uri + ", first prefix: " + map.inverse().get(uri) 
                    		+ ", second prefix: " + prefix);
                    continue;
                }

                map.put(prefix, uri);
            }

            return ImmutableBiMap.copyOf(map);
        } catch (MalformedQueryException em) {
            throw new MetadataRepositoryException("Failed to load prefix map from store", em);
        } catch (QueryEvaluationException eq) {
            throw new MetadataRepositoryException("Failed to load prefix map from store", eq);
        }
    }

    private String validatePrefix(Value rawPrefix, Value rawURI) {
        if (rawPrefix == null) {
            logger.warn("null prefix from prefix query for uri: {}", rawURI.toString());
            return null;
        } else if (!(rawPrefix instanceof Literal)) {
            logger.warn("prefix({}) is not a literal for uri: {}", rawPrefix, rawURI);
            return null;
        }

        Literal literalPrefix = (Literal)rawPrefix;
        Object datatype = literalPrefix.getDatatype();

        if (datatype != null && !datatype.equals(XSD_STRING)) {
            logger.warn("prefix " + literalPrefix + " has non-string datatype " + datatype + " for uri " + rawURI);
            return null;
        }

        return literalPrefix.stringValue();
    }

    private URI validateURI(Value rawURI, Value rawPrefix) {
        if (rawURI == null) {
            logger.warn("null uri from prefix query for prefix: {}", rawPrefix.toString());
            return null;
        } else if (!(rawURI instanceof org.openrdf.model.URI)) {
            logger.warn("uri({}) is not a uri for prefix: {}", rawURI, rawPrefix);
            return null;
        }

        try {
            return new URI(rawURI.toString());
        } catch (URISyntaxException eu) {
            logger.warn("Invalid uri syntax(" + rawURI + ") for prefix " + rawPrefix, eu);
            return null;
        }
    }

    private BiMap<String, URI> loadDefaults() {
        logger.warn("Fall through to default prefix map used");
        ImmutableBiMap.Builder<String,URI> builder = ImmutableBiMap.builder();
        return builder
            .put("qldarch", URI.create("http://qldarch.net/ns/rdf/2012-06/terms#"))
            .put("qavocab", URI.create("http://qldarch.net/ns/skos/2013-02/vocab#"))
            .put("qaint", URI.create("http://qldarch.net/ns/rdf/2013-08/internal#"))
            .put("qaomeka", URI.create("http://qldarch.net/omeka/items/show/"))
            .put("rdf", URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
            .put("rdfs", URI.create("http://www.w3.org/2000/01/rdf-schema#"))
            .put("owl", URI.create("http://www.w3.org/2002/07/owl#"))
            .put("xsd", URI.create("http://www.w3.org/2001/XMLSchema#"))
            .put("dcterms", URI.create("http://purl.org/dc/terms/"))
            .put("foaf", URI.create("http://xmlns.com/foaf/0.1/"))
            .put("skos", URI.create("http://www.w3.org/2004/02/skos/core#"))
            .put("geo", URI.create("http://www.w3.org/2003/01/geo/wgs84_pos#"))
            .build();
    }

    /**
     * Set the URI used to contact the metadata server.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public void setServerURI(String serverURI) {
        this.prefixes = null;
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
    public void setRepoName(String repoName) {
        this.prefixes = null;
        this.repoName = repoName;
    }

    public String getRepoName() {
        return this.repoName;
    }

    /**
     * Set the graph containing the prefix configuration.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public void setGraph(String graph) {
        this.prefixes = null;
        this.graph = graph;
    }

    public String getGraph() {
        return this.graph;
    }

}
