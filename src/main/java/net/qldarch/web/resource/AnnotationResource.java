package net.qldarch.web.resource;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Sets.newHashSet;
import static net.qldarch.web.service.KnownURIs.ASSOCIATED_ARCHITECT;
import static net.qldarch.web.service.KnownURIs.ASSOCIATED_FIRM;
import static net.qldarch.web.service.KnownURIs.OBJECT;
import static net.qldarch.web.service.KnownURIs.PREDICATE;
import static net.qldarch.web.service.KnownURIs.QAC_HAS_ANNOTATION_GRAPH;
import static net.qldarch.web.service.KnownURIs.QA_ASSERTED_BY;
import static net.qldarch.web.service.KnownURIs.QA_ASSERTION_DATE;
import static net.qldarch.web.service.KnownURIs.QA_EVIDENCE;
import static net.qldarch.web.service.KnownURIs.RDF_TYPE;
import static net.qldarch.web.service.KnownURIs.RELATED_TO;
import static net.qldarch.web.service.KnownURIs.RELATED_TO_RELATION;
import static net.qldarch.web.service.KnownURIs.SUBJECT;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.service.RepositoryQuery;
import net.qldarch.web.util.Functions;
import net.qldarch.web.util.SparqlTemplate;
import net.qldarch.web.util.SparqlToJsonString;
import net.qldarch.web.util.SparqlToJsonString.PGraph;

import org.apache.shiro.SecurityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Path("/annotation")
public class AnnotationResource {
    public static Logger logger = LoggerFactory.getLogger(AnnotationResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    public static String SHARED_ANNOTATION_GRAPH = "http://qldarch.net/rdf/2013-09/annotations";

    private static final SparqlTemplate ANNOTATION_QUERIES =
        new SparqlTemplate("queries/Annotations.sparql.stg");

    private RdfDataStoreDao rdfDao;

    @GET
    @Produces("application/json")
    public Response performGet(
            @DefaultValue("") @QueryParam("RESOURCE") String resourceStr,
            @DefaultValue("") @QueryParam("TIME") String timeStr,
            @DefaultValue("0.0") @QueryParam("DURATION") String durationStr) {

        logger.debug("Querying annotations for resource: " + resourceStr 
        		+ ", time: " + timeStr + ", duration: " + durationStr);

        if (resourceStr.isEmpty()) {
            logger.info("Bad request received. No resource provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam RESOURCE missing")
                .build();
        }
        if (timeStr.isEmpty()) {
            logger.info("Bad request received. No time provided.");
            return Response
                .status(Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("QueryParam TIME missing")
                .build();
        }

        try {
            final URI resource = resolveURI(resourceStr, "resource");
            final BigDecimal time = parseDecimal(timeStr, "time");
            final BigDecimal duration = parseDecimal(durationStr, "duration");
            logger.debug("Raw annotations query: " + resource + ", " + time + ", " + duration);
            String query = ANNOTATION_QUERIES.render("byUtterance", new SparqlTemplate.Binder() {
              @Override
              public void bind(ST template) {
                template.add("resource", resource).add("lower", time).add("upper", duration);
              }
            });
            String result = new SparqlToJsonString().performQuery(query);
            return Response.ok()
                .entity(result)
                .build();
        } catch (ResourceFailedException er) {
            return er.getResponse();
        }
    }

    private static List<Value> lof(String uri) {
      return lof(uri(uri));
    }

    private static List<Value> lof(Value v) {
      return ImmutableList.of(v);
    }

    private static URIImpl uri(String u) {
      return new URIImpl(u); 
    }

    private static URIImpl uri(URI uri) {
      return uri(uri.toString());
    }

    private static Value value(String literal) {
      return new ValueFactoryImpl().createLiteral(literal);
    }

    private void addStructures(final PGraph pgraph, final String s) {
      try {
        List<String> structures = getRdfDao().execute(new RepositoryQuery<List<String>>() {
          @Override
          public List<String> query(RepositoryConnection con)
              throws RepositoryException, MetadataRepositoryException {
            List<String> l = Lists.newArrayList();
            {
              RepositoryResult<Statement> result =  con.getStatements(
                  null, uri(ASSOCIATED_ARCHITECT), uri(s), true);
              for(Statement statement : result.asList()) {
                l.add(statement.getSubject().stringValue());
              }
            }
            {
              RepositoryResult<Statement> result =  con.getStatements(
                  null, uri(ASSOCIATED_FIRM), uri(s), true);
              for(Statement statement : result.asList()) {
                l.add(statement.getSubject().stringValue());
              }
            }
            return l;
          }});
        for(String structure : structures) {
          Map<String, List<Value>> pMap = Maps.newHashMap();
          pMap.put(OBJECT.toString(), lof(structure));
          pMap.put(RDF_TYPE.toString(), lof(RELATED_TO_RELATION.toString()));
          pMap.put(SUBJECT.toString(), lof(s));
          pMap.put(PREDICATE.toString(), lof(RELATED_TO.toString()));
          pMap.put("implicit", lof(value("true")));
          pgraph.put(structure, pMap);
        }
      } catch(Exception e) {}
    }

    private void addAssociated(final PGraph pgraph, final String o) {
      try {
        List<String> rs = getRdfDao().execute(new RepositoryQuery<List<String>>() {
          @Override
          public List<String> query(RepositoryConnection con)
              throws RepositoryException, MetadataRepositoryException {
            List<String> l = Lists.newArrayList();
            {
              RepositoryResult<Statement> result =  con.getStatements(
                  uri(o), uri(ASSOCIATED_FIRM), null, true);
              for(Statement statement : result.asList()) {
                l.add(statement.getObject().stringValue());
              }
            }
            {
              RepositoryResult<Statement> result =  con.getStatements(
                  uri(o), uri(ASSOCIATED_ARCHITECT), null, true);
              for(Statement statement : result.asList()) {
                l.add(statement.getObject().stringValue());
              }
            }
            return l;
          }});
        for(String relation : rs) {
          Map<String, List<Value>> pMap = Maps.newHashMap();
          pMap.put(OBJECT.toString(), lof(o));
          pMap.put(RDF_TYPE.toString(), lof(RELATED_TO_RELATION.toString()));
          pMap.put(SUBJECT.toString(), lof(relation));
          pMap.put(PREDICATE.toString(), lof(RELATED_TO.toString()));
          pMap.put("implicit", lof(value("true")));
          pgraph.put(relation, pMap);
        }
      } catch(Exception e) {}
    }

    private void addImplicitRelationships(final PGraph pgraph, final String s, final String o) {
      addAssociated(pgraph, o);
      addStructures(pgraph, s);
    }

    @GET
    @Path("relationship")
    @Produces("application/json")
    public Response annotationsByRelationship(
        final @DefaultValue("") @QueryParam("subject") String subjectStr,
        final @DefaultValue("") @QueryParam("predicate") String predicateStr,
        final @DefaultValue("") @QueryParam("object") String objectStr) {
      logger.debug("Querying annotations by relationship subject: " + subjectStr + 
          ", predicate: " + predicateStr + ", object: " + objectStr);
      try {
        final URI subject = resolveURI(subjectStr, "subject");
        final URI predicate = resolveURI(predicateStr, "predicate");
        final URI object = resolveURI(objectStr, "object");
        logger.debug("Raw annotations query: " + subject + ", " + predicate + ", " + object);
        String query = ANNOTATION_QUERIES.render("byRelationship", new SparqlTemplate.Binder() {
          @Override
          public void bind(ST template) {
            template.add("subject", subject).add("predicate", predicate).add("object", object);
          }
        });
        SparqlToJsonString qe = new SparqlToJsonString();
        PGraph pgraph = qe.execute(query);
        addImplicitRelationships(pgraph, subjectStr, objectStr);
        String result = qe.serialise(pgraph);
        return Response.ok().entity(result).build();
      } catch(Exception e) {
        String msg = String.format("failed to find relationship of %s, %s, %s",subjectStr,
            predicateStr, objectStr);
        logger.debug(msg, e);
        throw new RuntimeException(msg, e);
      }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("evidence/{id : (.+)?}")
    public Response getEvidenceById(
            @DefaultValue("") @PathParam("id") String id,
            @DefaultValue("") @QueryParam("IDLIST") String idlist,
            @DefaultValue("") @QueryParam("RELIDS") String relids) {
        logger.debug("Querying evidence by id: {}, idlist: {}", id, idlist);

        Set<String> idStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        if (!id.isEmpty()) idStrs.add(id);

        Set<String> relIdStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(relids));

        if (!idStrs.isEmpty() && !relIdStrs.isEmpty()) {
            logger.info("Bad request: specified both Evidence ids and Relationship ids.");
            return Response
                    .status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Bad request: specified both Evidence ids and Relationship ids")
                    .build();
        }

        final Collection<URI> idURIs = transform(idStrs, Functions.toResolvedURI());
        final Collection<URI> relURIs = transform(relIdStrs, Functions.toResolvedURI());

        String result = relURIs.isEmpty() ?
            new SparqlToJsonString().performQuery(findEvidenceByIds(idURIs)) :
            new SparqlToJsonString().performQuery(findEvidenceByRelationships(relURIs));
        return Response.ok()
                .entity(result)
                .build();
    }

    private String findEvidenceByIds(final Collection<URI> ids) {
      if (ids.size() < 1) {
        throw new IllegalArgumentException("Empty id collection passed to findEvidenceByIds()");
      }
      return ANNOTATION_QUERIES.render("byEvidenceIds", new SparqlTemplate.Binder() {
        @Override
        public void bind(ST template) {
          template.add("ids", ids);
        }
      });
    }

    private String findEvidenceByRelationships(final Collection<URI> ids) {
      if (ids.size() < 1) {
        throw new IllegalArgumentException(
            "Empty id collection passed to findEvidenceByRelationships()");
      }
      return ANNOTATION_QUERIES.render("byRelationships", new SparqlTemplate.Binder() {
        @Override
        public void bind(ST template) {
          template.add("ids", ids);
        }
      });
    }

    private URI resolveURI(String uriStr, String description) throws ResourceFailedException {
        try {
            if (uriStr == null || uriStr.isEmpty()) return null;
            return KnownPrefixes.resolve(uriStr);
        } catch (MetadataRepositoryException em) {
            String msg = String.format("Unable to resolve %s URI: %s", description, uriStr);
            logger.info(msg, em);
            throw new ResourceFailedException(msg, em,
                    Response.status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(msg)
                    .build());
        }
    }

    private BigDecimal parseDecimal(String decimal, String description) throws ResourceFailedException {
        try {
            if (decimal == null || decimal.isEmpty()) return null;
            return new BigDecimal(decimal);
        } catch (NumberFormatException en) {
            String msg = String.format("Unable to parse %s as xsd:decimal: %s", description, decimal);
            logger.info(msg, en);
            throw new ResourceFailedException(msg, en,
                    Response.status(Status.BAD_REQUEST)
                            .type(MediaType.TEXT_PLAIN)
                            .entity(msg)
                            .build());
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addAnnotation(String json) throws IOException {
        if (!SecurityUtils.getSubject().isPermitted("annotation:create")) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        
        RdfDescription rdf = new ObjectMapper().readValue(json, RdfDescription.class);

        User user = User.currentUser();

        URI userAnnotationGraph = user.getAnnotationGraph();

        try {
            List<RdfDescription> evidences = rdf.getSubGraphs(QA_EVIDENCE);
            for (RdfDescription ev : evidences) {
                List<URI> evTypes = ev.getType();
                if (evTypes.size() == 0) {
                    logger.info("Bad request received. No rdf:type provided for evidence: {}", ev);
                    return Response
                        .status(Status.BAD_REQUEST)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("No rdf:type provided for evidence")
                        .build();
                }
                URI evType = evTypes.get(0);
                URI evId = user.newId(userAnnotationGraph, evType);
                
                ev.setURI(evId);
                ev.replaceProperty(QA_ASSERTED_BY, user.getUserURI());
                ev.replaceProperty(QA_ASSERTION_DATE, new Date());

                this.getRdfDao().insertRdfDescription(ev, user, QAC_HAS_ANNOTATION_GRAPH,
                        userAnnotationGraph);
            }

            List<URI> relTypes = rdf.getType();
            if (relTypes.size() == 0) {
                logger.info("Bad request received. No rdf:type provided: {}", rdf);
                return Response
                    .status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("No rdf:type provided")
                    .build();
            }
            URI relType = relTypes.get(0);
            URI relId = user.newId(userAnnotationGraph, relType);

            rdf.setURI(relId);

            // Generate and Perform insertRdfDescription query
            this.getRdfDao().insertRdfDescription(rdf, user, QAC_HAS_ANNOTATION_GRAPH,
                    userAnnotationGraph);
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertRdfDescription graph:" + userAnnotationGraph + ", rdf:" + rdf + ")", em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        }

        String annotation = new ObjectMapper().writeValueAsString(rdf);
        logger.trace("Returning successful annotation: {}", annotation);
        // Return
        return Response.created(rdf.getURI())
            .entity(annotation)
            .build();
    }

    @DELETE
    @Path("evidence")
    public Response deleteEvidence(@DefaultValue("") @QueryParam("ID") String id,
                                   @DefaultValue("") @QueryParam("IDLIST") String idlist) {
        User user = User.currentUser();
        if (!SecurityUtils.getSubject().isPermitted("annotation:delete")
        		&& !user.isOwner(id)) {
        	return Response
                    .status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }

        Set<String> idStrs = newHashSet(
                Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        if (!id.isEmpty()) idStrs.add(id);

        final Collection<URI> idURIs = transform(idStrs, Functions.toResolvedURI());

        List<URI> evidenceURIs = null;
        try {
            String query = ANNOTATION_QUERIES.render(
                "confirmEvidenceIds", new SparqlTemplate.Binder() {
              @Override
              public void bind(ST template) {
                template.add("ids", idURIs);
              }
            });

            logger.debug("AnnotationResource DELETE evidence performing SPARQL id-query:\n{}", query);

            evidenceURIs = this.getRdfDao().queryForRdfResources(query);
        } catch (MetadataRepositoryException e) {
            logger.warn("Error confirming evidence ids: {})", idURIs);
            return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error confirming evidence ids")
                    .build();
        }

        if (evidenceURIs.isEmpty()) {
            logger.info("Bad request received. No evidence ids provided.");
            return Response
                    .status(Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("QueryParam ID/IDLIST missing or invalid")
                    .build();
        }

        for (URI evidence : evidenceURIs) {
            try {
                this.getRdfDao().deleteRdfResource(evidence);
            } catch (MetadataRepositoryException e) {
                logger.warn("Error performing delete evidence:{})", evidence);
                return Response
                        .status(Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("Error performing delete")
                        .build();
            }
        }

        List<URI> orphaned = null;
        try {
            String query = ANNOTATION_QUERIES.render("unevidencedRelationships");

            logger.debug("AnnotationResource DELETE evidence performing SPARQL query:\n{}", query);

            orphaned = this.getRdfDao().queryForRdfResources(query);
            for (URI orphan : orphaned) {
                this.getRdfDao().deleteRdfResource(orphan);
            }
        } catch (MetadataRepositoryException e) {
            logger.warn("Error removing orphaned relationships:{})", orphaned);
        }

        return Response
            .status(Status.ACCEPTED)
            .type(MediaType.TEXT_PLAIN)
            .entity(String.format("Evidence %s deleted", id))
            .build();
    }

    public void setRdfDao(RdfDataStoreDao rdfDao) {
        this.rdfDao = rdfDao;
    }

    public RdfDataStoreDao getRdfDao() {
        if (this.rdfDao == null) {
            this.rdfDao = new RdfDataStoreDao();
        }
        return this.rdfDao;
    }
}
