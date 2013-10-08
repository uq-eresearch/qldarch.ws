package net.qldarch.web.service;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static javax.ws.rs.core.Response.Status;
import static net.qldarch.web.service.KnownURIs.*;

@Path("/file")
public class FileSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(FileSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    private ServletContext context;
    private File archiveDir;

    public static String formatQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder(
                "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " +
                "select distinct ?s ?p ?o where {" + 
                "  {" + 
                "    graph <http://qldarch.net/rdf/2013-09/catalog> {" + 
                "      ?u <http://qldarch.net/ns/rdf/2013-09/catalog#hasFileGraph> ?g." + 
                "    } ." + 
                "  } UNION {" + 
                "    BIND ( <http://qldarch.net/ns/omeka-export/2013-02-06> AS ?g ) ." + 
                "  } ." + 
                "  graph ?g {" +
                "    ?s a :DigitalFile ." +
                "    ?s ?p ?o ." +
                "  } . " +
                "} BINDINGS ?s { (<");

        String query = Joiner.on(">) (<").appendTo(builder, transform(ids, toStringFunction())).append(">) }").toString();
        logger.debug("FileSummaryResource performing SPARQL query: {}", query);
        
        return query;
    }

    @Context
    @SuppressWarnings("unchecked")
    public void setServletContext(ServletContext context) {
        logger.trace("Setting servlet context");
        logger.trace("  init-names: {}",
                newArrayList(Iterators.forEnumeration(context.getInitParameterNames())));
        this.context = context;
        String archiveDirStr = context.getInitParameter("net.qldarch.context.archivedir");
        if (archiveDirStr == null) {
            logger.warn("InitParam net.qldarch.context.archivedir not set");
            throw new IllegalArgumentException("net.qldarch.context.archivedir not set");
        }
        this.archiveDir = new File(context.getInitParameter("net.qldarch.context.archivedir"));
        logger.info("Using archive directory: {}", archiveDir);
        if (!this.archiveDir.exists()) {
            logger.warn("Archive Dir {} does not exist", this.archiveDir);
            throw new IllegalArgumentException("net.qldarch.context.archivedir does not exist");
        } else if (!this.archiveDir.isDirectory()) {
            logger.warn("Archive Dir {} is not a directory", this.archiveDir);
            throw new IllegalArgumentException("net.qldarch.context.archivedir is not a directory");
        } else if (!this.archiveDir.canWrite()) {
            logger.warn("Archive Dir {} is not writable", this.archiveDir);
            throw new IllegalArgumentException("net.qldarch.context.archivedir is not writable");
        } else if (!this.archiveDir.canRead()) {
            logger.warn("Archive Dir {} is not readable", this.archiveDir);
            throw new IllegalArgumentException("net.qldarch.context.archivedir is not readable");
        }
    }

    @GET
    @Path("summary")
    @Produces("application/json")
    public String performGet(
            @QueryParam("PREFIX") String prefix,
            @QueryParam("ID") Set<String> idParam,
            @DefaultValue("") @QueryParam("IDLIST") String idlist) {

        logger.debug("Querying PREFIX: {}, ID: {}, IDLIST: {}", prefix, idParam, idlist);

        Set<String> ids = newHashSet(idParam);
        Iterables.addAll(ids, Splitter.on(',').trimResults().omitEmptyStrings().split(idlist));
        logger.debug("Raw ids: {}", ids);
        return new SparqlToJsonString().performQuery(formatQuery(resolvePrefix(prefix, ids)));
    }

    private static Collection<String> resolvePrefix(String prefixString, Collection<String> ids) {
        Optional<String> prefix = fromNullable(prefixString);

        return prefix.isPresent() ? transform(ids, resolver(prefix.get())) : ids;
    }

    private static Function<String,String> resolver(final String prefix) {
        return new Function<String,String>() {
            public String apply(String s) {
                if (s.indexOf(':') == -1) {
                    return prefix + s;
                } else {
                    return s;
                }
            }
        };
    }

    public static String queryFilesByUser(URI userURI, Optional<URI> fileURI, boolean summary) {
        if (userURI == null) throw new IllegalArgumentException("userURI null");
        if (fileURI == null) throw new IllegalArgumentException("optional(fileURI) null");

        String baseQuery = 
            "PREFIX :<http://qldarch.net/ns/rdf/2012-06/terms#> " + 
            "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> " + 
            "select distinct ?s ?p ?o where  {" + 
            "  graph <http://qldarch.net/rdf/2013-09/catalog> {" + 
            "    <%s> <http://qldarch.net/ns/rdf/2013-09/catalog#hasFileGraph> ?g." + 
            "  } ." + 
            "  graph ?g {" + 
            "    ?s a :DigitalFile ." +
            "    ?s ?p ?o ." + 
            "  } ." + 
            "%s" +
            "%s" +
            "}";

        String summaryRestriction =
            "  graph <http://qldarch.net/ns/rdf/2012-06/terms#> {" + 
            "    ?p a :SummaryProperty ." + 
            "  } ";

        String fileRestriction =
            "  {" +
            "    BIND ( <%s> AS ?s ) ." +
            "  } ";

        String query = String.format(baseQuery,
                userURI,
                (fileURI.isPresent() ? String.format(fileRestriction, fileURI.get()) : ""),
                (summary ? summaryRestriction : ""));

        return query;
    }

    @GET
    @Path("user/{id}")
    @Produces("application/json")
    public Response performGet(@DefaultValue("") @PathParam("id") String fileId) {
        User user = User.currentUser();
        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .entity("{}")
                .build();
        }

        logger.debug("Querying user files : ID: {}", fileId);

        String query = queryFilesByUser(
                user.getUserURI(), KnownPrefixes.resolver().apply(fileId), false);

        logger.debug("ExpressionResource performing SPARQL query: {}", query);

        return Response.ok().entity(new SparqlToJsonString().performQuery(query)).build();
    }

    @POST
    @Path("user")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response performPost(
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileDis) throws IOException {

        /*
        User user = User.currentUser();
        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .entity("{}")
                .build();
        }

        URI userFileGraph = user.getFileGraph();
*/
        // Kludge so I can use curl to test.
        User user = new User("admin");

        File userFileDir = new File(this.archiveDir, user.getUsername());
        File userTmpDir = new File(userFileDir, "tmp");
        if (!userTmpDir.exists()) {
            userTmpDir.mkdirs();
        }

        String filename = fileDis.getFileName();
        String basename = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);
        String stripBase = basename.replaceAll("[^a-zA-Z0-9_]", "");
        String stripExt = extension.replaceAll("[^a-zA-Z0-9_]", "");
        File tmpFile = new File(userTmpDir, String.format("%s-%d-%s.%s",
                    user.getUsername(), System.currentTimeMillis(), stripBase, stripExt));
        File destFile = new File(userFileDir, String.format("%s-%d-%s.%s",
                    user.getUsername(), System.currentTimeMillis(), stripBase, stripExt));

        Files.copy(file, tmpFile.toPath());

        logger.info("File creation date: {}", fileDis.getCreationDate());
        logger.info("File filename: {}", fileDis.getFileName());
        logger.info("File modification date: {}", fileDis.getModificationDate());
        logger.info("File read date: {}", fileDis.getReadDate());
        logger.info("File size: {}", fileDis.getSize());
        logger.info("File type: {}", fileDis.getType());
        logger.info("File parameters {}", fileDis.getParameters());
        logger.info("Tmpfile size: {}", Files.size(tmpFile.toPath()));
        logger.info("destFile: {}", destFile);

        String mimetype = new Tika().detect(tmpFile);

        logger.info("Tika mimetype: {}", mimetype);

        Files.move(tmpFile.toPath(), destFile.toPath());

        /*
        RdfDescription fileDesc = new RdfDescription();
        fileDesc.addProperty(RDF_TYPE, QA_DIGITAL_FILE);
        fileDesc.addProperty(QA_UPLOADED_BY, user.getUserURI());
        fileDesc.addProperty(QA_DATE_UPLOADED, new Date());
        fileDesc.addProperty(QA_SOURCE_FILENAME, filename);
        fileDesc.addProperty(QA_HAS_FILE_SIZE, Files.size(tmpFile.toPath());
        fileDesc.addProperty(QA_BASIC_MIME_TYPE, mimetype);
        fileDesc.addProperty(QA_SYSTEM_LOCATION, destFile.toString());
        */

        return Response.ok().entity("{}").build();
    }
}
