package net.qldarch.web.service;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

public interface RepositoryOperation {
    public void perform(RepositoryConnection conn)
        throws RepositoryException, MetadataRepositoryException;
}
