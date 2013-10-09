package net.qldarch.web.service;

import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

import java.net.URI;

import static net.qldarch.web.service.KnownURIs.*;

public class RdfDataStoreDao {
    private SesameConnectionPool connectionPool;
    private QldarchOntology ontology;

    public void performInsert(final RdfDescription rdf, final User user, final URI graph,
            final URI hasGraphPredicate) throws MetadataRepositoryException {
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
