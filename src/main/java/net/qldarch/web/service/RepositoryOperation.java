package net.qldarch.web.service;

public interface RepositoryOperation {
    public void perform(RepositoryConnection conn)
        throws RepositoryException, MetadataRepositoryException;
}
