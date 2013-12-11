package net.qldarch.web.resource;

import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.util.SparqlToJsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;

@Path("/ontology")
public class OntologyResource {
    public static Logger logger = LoggerFactory.getLogger(OntologyResource.class);

    private static final STGroupFile ONTOLOGY_QUERIES = new STGroupFile("queries/Ontology.sparql.stg");

    @GET
    @Path("/relationships")
    @Produces("application/json")
    public String findRelationships() {
        String query = ONTOLOGY_QUERIES.getInstanceOf("listRelationships").render();

        return new SparqlToJsonString().performQuery(query);
    }

    @GET
    @Path("/properties")
    @Produces("application/json")
    public String findProperties() {
        String query = ONTOLOGY_QUERIES.getInstanceOf("listProperties").render();

        return new SparqlToJsonString().performQuery(query);
    }

    @GET
    @Path("/entities/{type}")
    @Produces("application/json")
    public String findEntities(@PathParam("type") String type,
            @DefaultValue("false") @QueryParam("INCSUBCLASS") boolean includeSubClass,
            @DefaultValue("false") @QueryParam("INCSUPERCLASS") boolean includeSuperClass)
            throws MetadataRepositoryException {

        if (type == null) {
            logger.info("No type param provided.");
            throw new IllegalArgumentException("No type param provided");
        }
        URI typeURI = KnownPrefixes.resolve(type);

        String query = ONTOLOGY_QUERIES.getInstanceOf("entitiesByType")
                .add("type", typeURI)
                .add("incSubClass", includeSubClass)
                .add("incSuperClass", includeSuperClass)
                .render();

        logger.debug("Obtaining types from ontology with query: {}", query);

        return new SparqlToJsonString().performQuery(query);
    }
}
