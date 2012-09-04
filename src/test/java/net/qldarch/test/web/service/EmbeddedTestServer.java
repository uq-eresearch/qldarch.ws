package net.qldarch.test.web.service;

import org.junit.rules.ExternalResource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class EmbeddedTestServer extends ExternalResource {
    Server server;
    int port = 8080;

    @Override
    protected void before() throws Throwable {
        server = new Server(port);
        server.setHandler(new WebAppContext("dist/HelloWorld-0.0.1-min.war", "/"));
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
