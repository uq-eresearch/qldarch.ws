package net.qldarch.test.web.service;

import org.junit.rules.ExternalResource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class EmbeddedTestServer extends ExternalResource {
    Server server;
    int port = 8080;
    String warFile;
    String contextPath;

    // FIXME: Should take dist/ from system property.
    public EmbeddedTestServer(String warFile, String contextPath) {
        this.warFile = warFile;
        this.contextPath = contextPath;
    }

    @Override
    protected void before() throws Throwable {
        server = new Server(port);
        server.setHandler(new WebAppContext("dist/" + warFile, contextPath));
        server.start();
    }

    @Override
    protected void after() {
        try {
            server.stop();
        } catch (Throwable t) {}
    }

    public String uri() {
        return "http://localhost:" + port;
    }
}
