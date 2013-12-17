package net.qldarch.web.util;

import org.slf4j.Logger;
import org.apache.commons.io.IOUtils;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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

    public static Response badRequest(String msg) {
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity(msg)
                .build();
    }

    public static Response internalError(String msg) {
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity(msg)
                .build();
    }

    public static Response forbidden(String msg) {
        return Response
                .status(Response.Status.FORBIDDEN)
                .type(MediaType.TEXT_PLAIN)
                .entity(msg)
                .build();
    }
    public static Response noContent() {
        return Response.noContent().build();
    }

}
