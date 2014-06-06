package net.qldarch.web.service;

import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is dodgy, and needs to be enhanced, but will do for now.
 */
public class SesameConnectionPool {
    public static Logger logger = LoggerFactory.getLogger(SesameConnectionPool.class);

    public static final String DEFAULT_SERVER_URI = "http://localhost:8080/openrdf-sesame";
    public static final String DEFAULT_REPO_NAME = "QldarchMetadataServer";

    private String serverURI = DEFAULT_SERVER_URI;
    private String repoName = DEFAULT_REPO_NAME;
    private Repository repo;

    private static SesameConnectionPool singleton;

    public static synchronized SesameConnectionPool instance() {
        if (singleton != null) {
            return singleton;
        } else {
            singleton = new SesameConnectionPool();
            return singleton;
        }
    }

    public SesameConnectionPool() {
        try {
            repo = new HTTPRepository(this.getServerURI(), this.getRepoName());
            repo.initialize();
        } catch (RepositoryException er) {
            logger.error("Unable to initialize repository: {}, {}",
                    this.getServerURI(), this.getRepoName());
            er.printStackTrace();
            throw new IllegalStateException("Unable to initalize repository", er);
        }
    }

    public void performOperation(RepositoryOperation operation)
            throws MetadataRepositoryException {
        RepositoryConnection conn = null;
        try {
            conn = repo.getConnection();

            operation.perform(conn);
        } catch (MetadataRepositoryException em) {
            throw em;
        } catch (RepositoryException er) {
            logger.warn("Error performing operation", er);
            throw new MetadataRepositoryException("Error performing operation", er);
        } finally {
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close();
                }
            } catch (RepositoryException erc) {
                logger.warn("Error closing repository connection", erc);
            }
        }
    }

    public <T> T performQuery(RepositoryQuery<T> operation)
            throws MetadataRepositoryException {
        RepositoryConnection conn = null;
        try {
            conn = repo.getConnection();

            return operation.query(conn);
        } catch (MetadataRepositoryException em) {
            throw em;
        } catch (RepositoryException er) {
            logger.warn("Error performing query", er);
            throw new MetadataRepositoryException("Error performing query", er);
        } finally {
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close();
                }
            } catch (RepositoryException erc) {
                logger.warn("Error closing repository connection", erc);
            }
        }
    }

    /**
     * Set the URI used to contact the metadata server.
     *
     * Note: This will force a reload of the prefix map on the next 
     *   lookup of a prefix or uri.
     */
    public synchronized void setServerURI(String serverURI) {
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
    public synchronized void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getRepoName() {
        return this.repoName;
    }

}
