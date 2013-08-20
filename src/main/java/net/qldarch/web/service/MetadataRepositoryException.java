package net.qldarch.web.service;

/**
 * Thrown to indicate an error has occurred accessing or interpreting
 * the metadata repository.
 */
public class MetadataRepositoryException extends Exception {
    public MetadataRepositoryException(String message) {
        super(message);
    }

    public MetadataRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
