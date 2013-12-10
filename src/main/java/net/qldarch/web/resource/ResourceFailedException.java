package net.qldarch.web.resource;

import javax.ws.rs.core.Response;

public class ResourceFailedException extends Exception {
    private final Response response;

    public ResourceFailedException(String message, Throwable cause, Response response) {
        super(message, cause);
        this.response = response;
    }

    public ResourceFailedException(String message, Response response) {
        super(message);
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }
}
