package net.qldarch.web.service;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

import java.net.URI;
import java.util.List;
import java.util.Map;

public interface RepositoryResourceQuery extends RepositoryQuery<Map<String, String>> {
    public Map<String, String> query(RepositoryConnection conn)
            throws RepositoryException, MetadataRepositoryException;
}
