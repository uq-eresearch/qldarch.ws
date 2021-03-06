package net.qldarch.web.service;

import java.net.URI;

public interface KnownURIs {
    public static final URI XSD_STRING = URI.create("http://www.w3.org/2001/XMLSchema#string");
    public static final URI XSD_DATE = URI.create("http://www.w3.org/2001/XMLSchema#date");
    public static final URI XSD_INTEGER = URI.create("http://www.w3.org/2001/XMLSchema#integer");
    public static final URI XSD_DECIMAL = URI.create("http://www.w3.org/2001/XMLSchema#decimal");
    public static final URI XSD_BOOLEAN = URI.create("http://www.w3.org/2001/XMLSchema#boolean");

    public static final URI QAC_HAS_ANNOTATION_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasAnnotationGraph");
    public static final URI QAC_HAS_ENTITY_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasEntityGraph");
    public static final URI QAC_HAS_EXPRESSION_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasExpressionGraph");
    public static final URI QAC_HAS_FILE_GRAPH = 
        URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasFileGraph");
    public static final URI QAC_HAS_COMPOUND_OBJECT_GRAPH = 
            URI.create("http://qldarch.net/ns/rdf/2013-09/catalog#hasCompoundObjectGraph");
    public static final URI QAC_CATALOG_GRAPH = 
        URI.create("http://qldarch.net/rdf/2013-09/catalog");

    public static final URI RDF_TYPE
        = URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

    public static final URI QA_REQUIRED
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#requiredToCreate");
    public static final URI QA_EVIDENCE
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#evidence");
    public static final URI QA_EVIDENCE_TYPE
        = URI.create("http://qldarch.net/ns/rdf/2012-06/terms#Evidence");
    public static final URI QA_ASSERTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertedBy");
    public static final URI QA_SUBJECT =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#subject");
    public static final URI QA_ASSERTION_DATE =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#assertionDate");
    public static final URI QA_DOCUMENTED_BY =
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#documentedBy");
    public static final URI QA_BASIC_MIME_TYPE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#basicMimeType");
    public static final URI QA_HAS_FILE_SIZE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#hasFileSize");
    public static final URI QA_SOURCE_FILENAME = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#sourceFilename");
    public static final URI QA_TRANSCRIPT_FILE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#transcriptFile");
    public static final URI QA_THUMBNAIL_FILE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#thumbnailFile");
    public static final URI QA_WEB_FILE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#webFile");
    public static final URI QA_SYSTEM_LOCATION = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#systemLocation");
    public static final URI QA_DIGITAL_FILE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#DigitalFile");
    public static final URI QA_UPLOADED_BY = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#uploadedBy");
    public static final URI QA_DATE_UPLOADED = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#dateUploaded");
    public static final URI QA_MANAGED_FILE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#managedFile");

    public static final URI HAS_TRANSCRIPT = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#hasTranscript");
    public static final URI TRANSCRIPT_LOCATION = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#transcriptLocation");

    public static final URI OBJECT = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#object");
    public static final URI RELATED_TO_RELATION = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#RelatedToRelation");
    public static final URI SUBJECT = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#subject");
    public static final URI PREDICATE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#predicate");
    public static final URI RELATED_TO = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#relatedTo");
    public static final URI ASSOCIATED_FIRM = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#associatedFirm");
    public static final URI ASSOCIATED_ARCHITECT = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#associatedArchitect");
    public static final URI WORKED_ON = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#workedOn");
    public static final URI WORKED_ON_RELATION = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#WorkedOnRelation");
    public static final URI COMPLETION_DATE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#completionDate");
    public static final URI START_DATE = 
        URI.create("http://qldarch.net/ns/rdf/2012-06/terms#startDate");
}
