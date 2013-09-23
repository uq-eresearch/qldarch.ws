package net.qldarch.web.service;

import com.google.common.base.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class Functions {
    public static Logger logger = LoggerFactory.getLogger(Functions.class);

    public static final class ToResolvedURI implements Function<String,URI> {
        public URI apply(String str) {
            try {
                return KnownPrefixes.resolve(str);
            } catch (MetadataRepositoryException em) {
                logger.trace("Error resolving URI %s", str, em);
                throw new IllegalArgumentException("Error resolving URI", em);
            }
        }
    }

    public static ToResolvedURI toResolvedURI() {
        return new ToResolvedURI();
    }
}
