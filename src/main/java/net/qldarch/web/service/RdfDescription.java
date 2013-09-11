package net.qldarch.web.service;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.base.Predicates.notNull;

public class RdfDescription {
    public static Logger logger = LoggerFactory.getLogger(RdfDescription.class);

    public static final URI RDF_TYPE = URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    private URI uri;
    private QldarchOntology ontology;

    private Multimap<URI, Object> properties;

    public RdfDescription() {
        this.properties = HashMultimap.create();
        this.ontology = null;
    }

    @JsonIgnore
    public List<URI> getType() {
        Collection<Object> typeObj = properties.get(RDF_TYPE);
        return ImmutableList.copyOf(filter(transform(typeObj, toURI), notNull()));
    }

    @JsonIgnore
    public void setURI(URI uri) {
        this.uri = uri;
    }

    @JsonIgnore
    public URI getURI() {
        return this.uri;
    }

    @JsonProperty(value="uri")
    public void setUri(String uri) {
        try {
            this.uri = new URI(uri);
        } catch (URISyntaxException eu) {
            throw new IllegalArgumentException("Invalid uri", eu);
        }
    }

    @JsonAnySetter
    public void addProperty(String name, Object value) throws MetadataRepositoryException {
        URI nameURI = KnownPrefixes.resolve(name);
        properties.put(nameURI, value);
    }

    private static Function<Object,URI> toURI = new Function<Object,URI>() {
        public URI apply(Object o) {
            if (!(o instanceof String)) {
                return null;
            } else {
                try {
                    return new URI((String)o);
                } catch (URISyntaxException eu) {
                    logger.debug("Error in uri: {}", o, eu);
                    return null;
                }
            }
        }
    };

    public Iterable<Statement> asStatements(URI subject)
            throws MetadataRepositoryException {
        return filter(transform(properties.entries(), toStatements(subject)), notNull());
    }

    private Function<Map.Entry<URI,Object>,Statement> toStatements(URI subject) 
            throws MetadataRepositoryException {
        final URIImpl subjectURI = new URIImpl(subject.toString());

        return new Function<Map.Entry<URI,Object>,Statement>() {
            public Statement apply(Map.Entry<URI,Object> entry) {
                try {
                    URIImpl predicateURI = new URIImpl(entry.getKey().toString());
                    Object object = entry.getValue();
                    String objectString = object.toString();
                    Value objectValue = getOntology().convertObject(entry.getKey(), entry.getValue());

                    return new StatementImpl(subjectURI, predicateURI, objectValue);
                } catch (MetadataRepositoryException em) {
                    return null;
                }
            }
        };
    }

    public synchronized QldarchOntology getOntology() {
        if (this.ontology == null) {
            this.ontology = QldarchOntology.instance();
        }
        return this.ontology;
    }

    public synchronized void setOntology(QldarchOntology ontology) {
        this.ontology = ontology;
    }
}
