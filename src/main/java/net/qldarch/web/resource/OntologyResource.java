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

@Path("/ontology")
public class OntologyResource {
    public static Logger logger = LoggerFactory.getLogger(OntologyResource.class);

    @GET
    @Path("/relationships")
    @Produces("application/json")
    public String findRelationships() {
        return new SparqlToJsonString().performQuery(
                "PREFIX qldarch: <http://qldarch.net/ns/rdf/2012-06/terms#> " +
                " select ?s ?p ?o" +
                " from <http://qldarch.net/ns/rdf/2012-06/terms#>" +
                " where {" +
                " ?s a qldarch:Relationship . ?s ?p ?o ." +
                " }");
    }

    @GET
    @Path("/properties")
    @Produces("application/json")
    public String findProperties() {
        return new SparqlToJsonString().performQuery(
                "select ?s ?p ?o" +
                " from <http://qldarch.net/ns/rdf/2012-06/terms#>" +
                " where {" +
                " { ?s rdf:type owl:DatatypeProperty. ?s ?p ?o. }" +
                " union { ?s rdf:type owl:ObjectProperty. ?s ?p ?o. }" +
                " }");
    }

    @GET
    @Path("/entities/{type}")
    @Produces("application/json")
    public String findEntities(@PathParam("type") String type,
            @DefaultValue("false") @QueryParam("INCSUBCLASS") boolean includeSubClass,
            @DefaultValue("false") @QueryParam("INCSUPERCLASS") boolean includeSuperClass)
            throws MetadataRepositoryException {
        String queryFormat =
            " PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
            " PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " +
            " select distinct ?s ?p ?o where  {" +
            "   BIND ( <%s> AS ?type ) ." +
            "   { " +
            "%s" +
            "%s" +
            "       BIND ( ?type AS ?transType ) ." +
            "   } ." +
            "   BIND ( ?transType AS ?s ) ." +
            "   graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" +
            "     ?s ?p ?o ." +
            "   } ." +
            " }";

        String subClassClause = 
            "     graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" +
            "       ?transType rdfs:subClassOf ?type ." +
            "     } ." +
            "   } UNION {";

        String superClassClause =
            "     graph <http://qldarch.net/ns/rdf/2012-06/terms#>  {" +
            "       ?type rdfs:subClassOf ?transType ." +
            "     } ." +
            "   } UNION {";

        if (type == null) {
            logger.info("No type param provided.");
            throw new IllegalArgumentException("No type param provided");
        }

        URI typeURI = KnownPrefixes.resolve(type);

        String query = String.format(queryFormat, typeURI, 
                includeSubClass ? subClassClause : "",
                includeSuperClass ? superClassClause : "");

        logger.trace("Obtaining types from ontology with query: {}", query);

        return new SparqlToJsonString().performQuery(query);
    }
}
