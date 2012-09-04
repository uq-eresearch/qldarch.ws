package net.qldarch.test.web.service;

import static org.junit.Assert.*;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.junit.Rule;
import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import net.qldarch.test.web.service.EmbeddedTestServer;

public class HelloWorldResourceTest {

    @Rule
    public EmbeddedTestServer server = new EmbeddedTestServer();

    @Test
    public void shouldReturnHelloWorld() {
        Client client = Client.create(new DefaultClientConfig());
        URI uri = UriBuilder.fromPath("rest/hello").build();
        WebResource wr = client.resource(server.uri()).path(uri.getPath());

        ClientResponse resp = wr.accept(MediaType.TEXT_PLAIN).get(ClientResource.class);

        assertNotNull("Response must not be null", resp);
        assertEquals("Status", 200, resp.getStatus());
        assertTrue("Response contains entity", resp.hasEntity());
        assertEquals("Response type text/plain", MediaType.TEXT_PLAIN, resp.hasEntity());

        String result = resp.getEntity(String.class);

        assertEquals("Result", "Hello World!", result);
    }
}
