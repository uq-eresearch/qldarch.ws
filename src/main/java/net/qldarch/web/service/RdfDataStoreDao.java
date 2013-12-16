package net.qldarch.web.service;

import net.qldarch.web.model.QldarchOntology;
import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import org.openrdf.model.Resource;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.*;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static net.qldarch.web.service.KnownURIs.*;

public class RdfDataStoreDao {
    public static Logger logger = LoggerFactory.getLogger(SesameConnectionPool.class);

    private SesameConnectionPool connectionPool;
    private QldarchOntology ontology;

    public void insertRdfDescription(final RdfDescription rdf, final User user, final URI hasGraphPredicate, final URI graph) throws MetadataRepositoryException {
        this.getConnectionPool().performOperation(new RepositoryOperation() {
            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                URIImpl userURI = new URIImpl(user.getUserURI().toString());
                URIImpl hasGraphURI = new URIImpl(hasGraphPredicate.toString());
                URIImpl contextURI = new URIImpl(graph.toString());
                URIImpl catalogURI = new URIImpl(QAC_CATALOG_GRAPH.toString());

                conn.add(userURI, hasGraphURI, contextURI, catalogURI);
                conn.add(rdf.asStatements(), contextURI);
            }
        });
    }

    public void deleteRdfResource(final URI resource) throws MetadataRepositoryException {
        this.getConnectionPool().performOperation(new RepositoryOperation() {
            public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                URIImpl resourceURI = new URIImpl(resource.toString());

                conn.remove(resourceURI, null, null);
                conn.remove((Resource)null, null, resourceURI);
            }
        });
    }

    public List<URI> queryForRdfResources(final String query) throws MetadataRepositoryException {
        return this.getConnectionPool().performQuery(new RepositorySingletonQuery() {
            @Override
            public List<URI> query(RepositoryConnection conn) throws RepositoryException, MetadataRepositoryException {
                try {
                    TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate();
                    List<URI> resources = newArrayList();

                    while (result.hasNext()) {
                        BindingSet bs = result.next();
                        Value r = bs.getValue("r");

                        try {
                            resources.add(new URI(r.toString()));
                        } catch (URISyntaxException e) {
                            logger.warn("Invalid URI({}) returned from RdfResources query: {}", r, query);
                        }
                    }

                    return resources;

                } catch (QueryEvaluationException e) {
                    logger.warn("Query failed: {}", query, e);
                    throw new MetadataRepositoryException("Query failed", e);
                } catch (MalformedQueryException e) {
                    logger.warn("Query malformed: {}", query, e);
                    throw new MetadataRepositoryException("Query malformed", e);
                }
            }
        });
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

    public void setOntology(QldarchOntology ontology) {
        this.ontology = ontology;
    }

    public synchronized QldarchOntology getOntology() {
        if (this.ontology == null) {
            this.ontology = QldarchOntology.instance();
        }
        return this.ontology;
    }
}
