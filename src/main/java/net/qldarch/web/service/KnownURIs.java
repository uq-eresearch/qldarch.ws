package net.qldarch.web.service;

import java.net.URI;

public interface KnownURIs {
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    public static final URI QA_REQUIRED
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#requiredToCreate");
    public static final URI QA_EVIDENCE
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#evidence");
    public static final URI QA_EVIDENCE_TYPE
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#Evidence");
    public static final URI RDF_TYPE
        = URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    public static final URI QA_ASSERTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertedBy");
    public static final URI QA_SUBJECT =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#subject");
    public static final URI QA_ASSERTION_DATE =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertionDate");
    public static final URI QA_DOCUMENTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#documentedBy");
    public static final URI QAC_HAS_ANNOTATION_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasAnnotationGraph");
    public static final URI QAC_HAS_ENTITY_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasEntityGraph");
    public static final URI QAC_CATALOG_GRAPH = 
        URI.create("http://qldarch.net/rdf/2013-09/catalog");
}