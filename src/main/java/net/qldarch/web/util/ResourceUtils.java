package net.qldarch.web.util;

import org.slf4j.Logger;
import org.apache.commons.io.IOUtils;

public class ResourceUtils {
    private Logger logger;

    public ResourceUtils(Logger logger) {
        this.logger = logger;
    }

    public String loadQueryFormat(String queryResource) {
        try {
            return IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream(queryResource));
        } catch (Exception e) {
            logger.error("Failed to load {} from classpath", queryResource, e);
            throw new IllegalStateException("Failed to load " + queryResource + " from classpath", e);
        }
    }
}
