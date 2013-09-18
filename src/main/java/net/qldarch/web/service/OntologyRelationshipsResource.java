package net.qldarch.web.service;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/ontology")
public class OntologyRelationshipsResource {
    public static Logger logger = LoggerFactory.getLogger(OntologyRelationshipsResource.class);

    @GET
    @Path("/Relationships")
    @Produces("application/json")
    public String performGet() {
        return new SparqlToJsonString().performQuery(
                "PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
                " select ?s ?p ?o" +
                " from <http://qldarch.net/ns/rdf/2012-06/terms#>" +
                " where {" +
                " ?s a qldarch:Relationship . ?s ?p ?o ." +
                " }");
    }

}
