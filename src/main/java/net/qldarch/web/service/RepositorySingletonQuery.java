package net.qldarch.web.service;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

import java.net.URI;
import java.util.List;

public interface RepositorySingletonQuery extends RepositoryQuery<List<URI>> {
    public List<URI> query(RepositoryConnection conn)
            throws RepositoryException, MetadataRepositoryException;
}
