package net.qldarch.web.util;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.STGroupFile;

public class SparqlTemplate {

  public static Logger logger = LoggerFactory.getLogger(SparqlTemplate.class);

  private static final STGroupFile ANNOTATION_QUERIES =
      new STGroupFile("queries/Annotations.sparql.stg");

  private static final STGroupFile ENTITY_QUERIES =
      new STGroupFile("queries/Entities.sparql.stg");

  private static final SparqlTemplate INSTANCE = new SparqlTemplate();

  private SparqlTemplate() {}

  public static synchronized SparqlTemplate instance() {
    return INSTANCE;
  }

  public synchronized String prepareAnnotationByUtteranceQuery(URI annotation,
      BigDecimal time, BigDecimal duration) {
    BigDecimal end = time.add(duration);
    String query = ANNOTATION_QUERIES.getInstanceOf("byUtterance")
        .add("resource", annotation)
        .add("lower", time)
        .add("upper", end)
        .render();
    logger.debug("AnnotationResource GET pabyq performing SPARQL query:\n{}", query);
    return query;
  }

  public synchronized String prepareAnnotationByRelationshipQuery(URI subject,
      URI predicate, URI object) {
    String query = ANNOTATION_QUERIES.getInstanceOf("byRelationship")
        .add("subject", subject)
        .add("predicate", predicate)
        .add("object", object)
        .render();
    logger.debug("Annotation by Relationship SPARQL: {}", query);
    return query;
  }

  public synchronized String findEvidenceByIds(Collection<URI> ids) {
    if (ids.size() < 1) {
      throw new IllegalArgumentException("Empty id collection passed to findEvidenceByIds()");
    }
    String query = ANNOTATION_QUERIES.getInstanceOf("byEvidenceIds")
        .add("ids", ids)
        .render();
    logger.debug("AnnotationResource GET febi performing SPARQL query:\n{}", query);
    return query;
  }

  public synchronized String findEvidenceByRelationships(Collection<URI> ids) {
    if (ids.size() < 1) {
      throw new IllegalArgumentException(
          "Empty id collection passed to findEvidenceByRelationships()");
    }
    String query = ANNOTATION_QUERIES.getInstanceOf("byRelationships")
        .add("ids", ids)
        .render();
    logger.debug("AnnotationResource GET febr performing SPARQL query:\n{}", query);
    return query;
  }

  public synchronized String confirmEvidenceIds(Collection<URI> idURIs) {
    return ANNOTATION_QUERIES.getInstanceOf("confirmEvidenceIds")
        .add("ids", idURIs)
        .render();
  }

  public synchronized String unevidencedRelationships() {
    return ANNOTATION_QUERIES.getInstanceOf("unevidencedRelationships").render();
  }

  public synchronized String prepareEntitiesByTypesQuery(Collection<URI> types, long since,
      boolean includeSubClass, boolean includeSuperClass, boolean summary) {
    String query = ENTITY_QUERIES.getInstanceOf("byType")
        .add("types", types)
        .add("incSubClass", includeSubClass)
        .add("incSuperClass", includeSuperClass)
        .add("summary", summary)
        .render();
    logger.debug("EntitySummaryResource performing SPARQL query: {}", query);
    return query;
  }

  public synchronized String prepareMergeUpdate(String intoResource, String fromResource) {
    String query = ENTITY_QUERIES.getInstanceOf("merge")
        .add("intoResource", intoResource)
        .add("fromResource", fromResource)
        .render();
    logger.debug("EntitySummaryResource performing SPARQL update: {}", query);
    return query;
  }

  public synchronized String prepareSearchByUserQuery(URI userExpressionID) {
    String query = ENTITY_QUERIES.getInstanceOf("searchByUserId")
        .add("id", userExpressionID)
        .render();
    logger.debug("EntitySummaryResource performing SPARQL query: {}", query);
    return query;
  }

  public synchronized String prepareSearchQuery(String searchString, Collection<URI> types, boolean restrictType) {
    String query = ENTITY_QUERIES.getInstanceOf("searchByLabelIds")
        .add("searchString", searchString)
        .add("types", types)
        .add("restrictType", restrictType)
        .render();
    logger.debug("EntitySummaryResource performing SPARQL query: {}", query);
    return query;
  }

  public synchronized String findByIds(Collection<URI> ids, boolean summary) {
    if (ids.size() < 1) {
      throw new IllegalArgumentException("Empty id collection passed to findEvidenceByIds()");
    }
    logger.debug("EntityResource performing SPARQL query: {}, {}", summary, ids);
    logger.debug("{}", (ids == null));
    String query = ENTITY_QUERIES.getInstanceOf("byIds")
        .add("ids", ids)
        .add("summary", summary)
        .render();
    logger.debug("EntityResource performing SPARQL query: {}", query);
    return query;
  }

  public synchronized String confirmEntityIds(Collection<URI> idURIs) {
    return ENTITY_QUERIES.getInstanceOf("confirmEntityIds")
        .add("ids", idURIs)
        .render();
  }

  public synchronized String extractGraphContext(Collection<URI> idURIs) {
    return ENTITY_QUERIES.getInstanceOf("extractGraphContext")
        .add("ids", idURIs)
        .render();
  }

}
