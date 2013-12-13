package net.qldarch.web.service;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

public interface RepositoryQuery<T> {
    public T query(RepositoryConnection conn) throws RepositoryException, MetadataRepositoryException;
}
