package net.qldarch.web.resource;

import java.net.URI;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.util.SparqlTemplate;
import net.qldarch.web.util.SparqlToJsonString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

@Path("/ontology")
public class OntologyResource {
    public static Logger logger = LoggerFactory.getLogger(OntologyResource.class);

    private static final SparqlTemplate ONTOLOGY_QUERIES =
        new SparqlTemplate("queries/Ontology.sparql.stg");

    @GET
    @Path("/relationships")
    @Produces("application/json")
    public String findRelationships() {
        String query = ONTOLOGY_QUERIES.render("listRelationships");
        return new SparqlToJsonString().performQuery(query);
    }

    @GET
    @Path("/properties")
    @Produces("application/json")
    public String findProperties() {
        String query = ONTOLOGY_QUERIES.render("listProperties");
        return new SparqlToJsonString().performQuery(query);
    }

    @GET
    @Path("/entities/{type}")
    @Produces("application/json")
    public String findEntities(@PathParam("type") String type,
            @DefaultValue("false") @QueryParam("INCSUBCLASS") final boolean includeSubClass,
            @DefaultValue("false") @QueryParam("INCSUPERCLASS") final boolean includeSuperClass)
            throws MetadataRepositoryException {

        if (type == null) {
            logger.info("No type param provided.");
            throw new IllegalArgumentException("No type param provided");
        }
        final URI typeURI = KnownPrefixes.resolve(type);
        String query = ONTOLOGY_QUERIES.render("entitiesByType", new SparqlTemplate.Binder() {
          @Override
          public void bind(ST template) {
            template.add("type", typeURI).add(
                "incSubClass", includeSubClass).add("incSuperClass", includeSuperClass);
          }
        });
        logger.debug("Obtaining types from ontology with query: {}", query);
        return new SparqlToJsonString().performQuery(query);
    }
}
