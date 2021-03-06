package net.qldarch.web.resource;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static net.qldarch.web.service.KnownURIs.QAC_HAS_FILE_GRAPH;
import static net.qldarch.web.service.KnownURIs.QA_BASIC_MIME_TYPE;
import static net.qldarch.web.service.KnownURIs.QA_DATE_UPLOADED;
import static net.qldarch.web.service.KnownURIs.QA_DIGITAL_FILE;
import static net.qldarch.web.service.KnownURIs.QA_HAS_FILE_SIZE;
import static net.qldarch.web.service.KnownURIs.QA_MANAGED_FILE;
import static net.qldarch.web.service.KnownURIs.QA_SOURCE_FILENAME;
import static net.qldarch.web.service.KnownURIs.QA_SYSTEM_LOCATION;
import static net.qldarch.web.service.KnownURIs.QA_THUMBNAIL_FILE;
import static net.qldarch.web.service.KnownURIs.QA_TRANSCRIPT_FILE;
import static net.qldarch.web.service.KnownURIs.QA_UPLOADED_BY;
import static net.qldarch.web.service.KnownURIs.QA_WEB_FILE;
import static net.qldarch.web.service.KnownURIs.RDF_TYPE;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import net.qldarch.web.model.RdfDescription;
import net.qldarch.web.model.User;
import net.qldarch.web.service.KnownPrefixes;
import net.qldarch.web.service.KnownURIs;
import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.service.RepositoryOperation;
import net.qldarch.web.transcript.ParseResult;
import net.qldarch.web.transcript.Transcripts;
import net.qldarch.web.util.SparqlToJsonString;

import org.apache.commons.io.FilenameUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.tika.Tika;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

@Path("/file")
public class FileSummaryResource {
    public static Logger logger = LoggerFactory.getLogger(FileSummaryResource.class);
    public static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    private File archiveDir;
    private RdfDataStoreDao rdfDao;

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

        logger.debug("Querying PREFIX: " + prefix + ", ID: " + idParam + ", IDLIST: " + idlist);

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
    @Path("user")
    @Produces("application/json")
    public Response performGet(@DefaultValue("") @QueryParam("ID") String fileId) {
        /*
        User user = User.currentUser();
        if (user.isAnon()) {
            return Response
                .status(Status.FORBIDDEN)
                .entity("{}")
                .build();
        }
*/
        User user = new User("admin");

        logger.debug("Querying user files : ID: {}", fileId);

        String query = queryFilesByUser(
                user.getUserURI(), KnownPrefixes.resolver().apply(fileId), false);

        logger.debug("ExpressionResource performing SPARQL query: {}", query);

        return Response.ok().entity(new SparqlToJsonString().performQuery(query)).build();
    }

    private String getIdFromJson(String json) {
      Map<String,String> map;
      ObjectMapper mapper = new ObjectMapper();
      try {
        map = mapper.readValue(json, 
            new TypeReference<HashMap<String,String>>(){});
        return map.get("id");
      } catch (Exception e) {}
      return null;
    }

    private URI toUri(String s) {
      try {
        return URI.create(s);
      } catch(Exception e ) {
        return null;
      }
    }

    private static Value value(String value) {
      return new ValueFactoryImpl().createLiteral(value);
    }

    private static URIImpl uri(URI uri) {
      return new URIImpl(uri.toString());
    }

    private String parseTranscript(File tmpFile, File transcriptFile) throws Exception {
      try(PrintStream ps = new PrintStream(new FileOutputStream(transcriptFile, false))) {
        ParseResult pr = Transcripts.parse(tmpFile);
        if(pr.ok()) {
          ps.print(pr.json());
        } else {
          throw new Exception(pr.msg(), pr.cause());
        }
        return transcriptFile.toString().substring(this.archiveDir.toString().length() + 1);
      }
    }

    @POST
    @Path("user")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response performPost(
            @FormDataParam("myObj") String json,
            @FormDataParam("file") InputStream file,
            @FormDataParam("file") FormDataContentDisposition fileDis)
                throws IOException, MetadataRepositoryException {
        if (!SecurityUtils.getSubject().isPermitted("file:create")) {
        	return Response.status(Status.FORBIDDEN)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Permission Denied.")
                    .build();
        }
        final URI ownerUri = toUri(getIdFromJson(json));
        // Kludge so I can use curl to test.
        User user = new User("amuys");

        URI userFileGraph = user.getFileGraph();

        File userFileDir = new File(this.archiveDir, user.getUsername());
        File userTmpDir = new File(userFileDir, "tmp");
        if (!userTmpDir.exists()) {
            userTmpDir.mkdirs();
        }
        final long timestamp = System.currentTimeMillis();
        String filename = fileDis.getFileName();
        String basename = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);
        String stripBase = basename.replaceAll("[^a-zA-Z0-9_]", "");
        String stripExt = extension.replaceAll("[^a-zA-Z0-9_]", "");
        File tmpFile = new File(userTmpDir, String.format("%s-%d-%s.%s",
                    user.getUsername(), timestamp, stripBase, stripExt));
        File destFile = new File(userFileDir, String.format("%s-%d-%s.%s",
                    user.getUsername(), timestamp, stripBase, stripExt));
        File fullFile = new File(userFileDir, String.format("full/%s-%d-%s.%s",
                user.getUsername(), timestamp, stripBase, stripExt));
        File metaFile = new File(userFileDir, String.format("%s-%d-%s.%s.json",
                    user.getUsername(), timestamp, stripBase, stripExt));

        // Yes, this should be a URL, but Java doesn't permit conversion from relative URI to URL!
        String locationURI = destFile.toString().substring(this.archiveDir.toString().length() + 1);
        
        Files.copy(file, tmpFile.toPath());

        if (stripExt.toLowerCase().equals("jpg") || stripExt.toLowerCase().equals("jpeg")) {
        	autoRotateImage(tmpFile.getAbsolutePath(), stripExt);
        }
        
        boolean isTranscript = (stripExt.toLowerCase().equals("doc") || stripExt.toLowerCase().equals("txt"));
        String transcriptLocationURI = "";
        final File transcriptFile = new File(userFileDir, String.format("transcript/%s-%d-%s.%s.json",
            user.getUsername(), timestamp, stripBase, stripExt));
        String message = null;
        if (isTranscript) {
          try {
            transcriptLocationURI = parseTranscript(tmpFile, transcriptFile);
          } catch(Exception e) {
            logger.warn(String.format("parsing transcript '%s' (%s) failed",
                tmpFile.getAbsolutePath(), destFile.getAbsolutePath()), e);
            message = e.getMessage();
            isTranscript = false;
          }
        }
        boolean isImage = (stripExt.toLowerCase().equals("jpg") || 
        		stripExt.toLowerCase().equals("jpeg") || stripExt.toLowerCase().equals("png") || 
        		stripExt.toLowerCase().equals("gif") || stripExt.toLowerCase().equals("bmp"));
        String webFileLocationURI = "";
        String thumbmnailLocationURI = "";
        if (isImage) {
            try {
                Files.copy(tmpFile.toPath(), fullFile.toPath());
                
            	File webFile = new File(userFileDir, String.format("web/%s-%d-%s.thumbnail.%s",
                        user.getUsername(), timestamp, stripBase, stripExt));
            	
    			Thumbnails.of(tmpFile).crop(Positions.CENTER).size(800, 800).toFile(webFile);
                
    			webFileLocationURI = webFile.toString().substring(this.archiveDir.toString().length() + 1);

            	File thumbnailFile = new File(userFileDir, String.format("thumbs/%s-%d-%s.thumbnail.%s",
                        user.getUsername(), timestamp, stripBase, stripExt));
            	
    			Thumbnails.of(tmpFile).crop(Positions.CENTER).size(200, 200).toFile(thumbnailFile);
                
    			thumbmnailLocationURI = thumbnailFile.toString().substring(this.archiveDir.toString().length() + 1);
    		} catch (IOException e) {
    			isImage = false;
    			e.printStackTrace();
    		}
        }
        
        logger.info("File creation date: {}", fileDis.getCreationDate());
        logger.info("File filename: {}", fileDis.getFileName());
        logger.info("File modification date: {}", fileDis.getModificationDate());
        logger.info("File read date: {}", fileDis.getReadDate());
        logger.info("File size: {}", fileDis.getSize());
        logger.info("File type: {}", fileDis.getType());
        logger.info("File parameters {}", fileDis.getParameters());
        logger.info("Tmpfile size: {}", Files.size(tmpFile.toPath()));
        logger.info("destFile: {}", destFile);
        logger.info("metaFile: {}", metaFile);
        logger.info("locationURI: {}", locationURI);
        if (isTranscript) {
            logger.info("transcriptLocation: {}", transcriptLocationURI);
        }
        if (isImage) {
        	logger.info("webFileLocation: {}", webFileLocationURI);
        	logger.info("thumbnailLocation: {}", thumbmnailLocationURI);
        }

        String mimetype = new Tika().detect(tmpFile);

        logger.info("Tika mimetype: {}", mimetype);

        Files.move(tmpFile.toPath(), destFile.toPath());

        RdfDescription fileDesc = new RdfDescription();
        fileDesc.addProperty(RDF_TYPE, QA_DIGITAL_FILE);
        fileDesc.addProperty(QA_UPLOADED_BY, user.getUserURI());
        fileDesc.addProperty(QA_DATE_UPLOADED, new Date());
        fileDesc.addProperty(QA_SOURCE_FILENAME, filename);
        fileDesc.addProperty(QA_HAS_FILE_SIZE, Files.size(destFile.toPath()));
        fileDesc.addProperty(QA_BASIC_MIME_TYPE, mimetype);
        fileDesc.addProperty(QA_MANAGED_FILE, true);
        fileDesc.addProperty(QA_SYSTEM_LOCATION, locationURI.toString());
        if (isTranscript) {
        	fileDesc.addProperty(QA_TRANSCRIPT_FILE, transcriptLocationURI.toString());
        }
        if (isImage) {
        	fileDesc.addProperty(QA_WEB_FILE, webFileLocationURI.toString());
        	fileDesc.addProperty(QA_THUMBNAIL_FILE, thumbmnailLocationURI.toString());
        }
        	
        String entity = null;
        try {
            final URI id = user.newId(userFileGraph, QA_DIGITAL_FILE);
            fileDesc.setURI(id);
            this.getRdfDao().insertRdfDescription(fileDesc, user, QAC_HAS_FILE_GRAPH, userFileGraph);
            // if the uploaded file is a transcript connect it to the interview
            if(isTranscript && (ownerUri != null)) {
              getRdfDao().getConnectionPool().performOperation(new RepositoryOperation() {
                @Override
                public void perform(RepositoryConnection conn)
                    throws RepositoryException, MetadataRepositoryException {
                  Resource context = getContext(ownerUri, conn);
                  // delete any old references to transcripts
                  conn.remove(uri(ownerUri), uri(KnownURIs.HAS_TRANSCRIPT), null, context);
                  conn.remove(uri(ownerUri), uri(KnownURIs.TRANSCRIPT_LOCATION), null, context);
                  conn.add(uri(ownerUri), uri(KnownURIs.HAS_TRANSCRIPT), uri(id), context);
                  conn.add(uri(ownerUri), uri(KnownURIs.TRANSCRIPT_LOCATION),
                      value(webAccessiblePath(transcriptFile)), context);
                }});
            }
        } catch (MetadataRepositoryException em) {
            logger.warn("Error performing insertRdfDescription graph:" + userFileGraph + ", rdf:" + fileDesc + ")", em);
            return Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .entity("Error performing insertRdfDescription")
                .build();
        } finally {
            entity = new ObjectMapper().writeValueAsString(fileDesc);
            Files.write(metaFile.toPath(), entity.getBytes(Charset.forName("UTF-8")));
        }

        logger.trace("Returning successful entity: {}", entity);
        if(message != null) {
          fileDesc.addProperty("msg", message);
        }
        return Response.ok().entity(fileDesc).build();
    }

    private Resource getContext(URI subject, RepositoryConnection con) throws RepositoryException {
      RepositoryResult<Statement> result = con.getStatements(uri(subject), null, null, true);
      // assumes that the first statement has the context we are looking for.
      // no idea if this is valid really.
      if(result.hasNext()) {
        return result.next().getContext();
      } else {
        return null;
      }
    }

    // assumes that the archiveDir (ends in files currently) is web accessible.
    // hopefully that does not change!
    private String webAccessiblePath(File location) {
      String p = location.getAbsolutePath().substring(
          (int)this.archiveDir.getAbsolutePath().length());
      return "/" + FilenameUtils.getName(this.archiveDir.getAbsolutePath()) + p;
    }

    public void autoRotateImage(String path, String extension) {
    	try {
	        File imageFile = new File(path);
	        BufferedImage originalImage = ImageIO.read(imageFile);
	
	        com.drew.metadata.Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
	        ExifIFD0Directory exifIFD0Directory = metadata.getDirectory(ExifIFD0Directory.class);
	        JpegDirectory jpegDirectory = (JpegDirectory) metadata.getDirectory(JpegDirectory.class);
	
	        int orientation = 1;
	        try {
	            orientation = exifIFD0Directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
	        } catch (Exception ex) {
	            ex.printStackTrace();
	        }
	
	        int width = jpegDirectory.getImageWidth();
	        int height = jpegDirectory.getImageHeight();
	        int tmp = 0;
	        
	        AffineTransform affineTransform = new AffineTransform();
	        
	        switch (orientation) {
	        case 1:
	            return;
	        case 2: // Flip X
	            affineTransform.scale(-1.0, 1.0);
	            affineTransform.translate(-width, 0);
	            break;
	        case 3: // PI rotation
	            affineTransform.translate(width, height);
	            affineTransform.rotate(Math.PI);
	            break;
	        case 4: // Flip Y
	            affineTransform.scale(1.0, -1.0);
	            affineTransform.translate(0, -height);
	            break;
	        case 5: // - PI/2 and Flip X
	            affineTransform.rotate(-Math.PI / 2);
	            affineTransform.scale(-1.0, 1.0);
	        	tmp = width;
	        	width = height;
	        	height = tmp;
	            break;
	        case 6: // -PI/2 and -width
	            affineTransform.translate(height, 0);
	            affineTransform.rotate(Math.PI / 2);
	        	tmp = width;
	        	width = height;
	        	height = tmp;
	            break;
	        case 7: // PI/2 and Flip
	            affineTransform.scale(-1.0, 1.0);
	            affineTransform.translate(-height, 0);
	            affineTransform.translate(0, width);
	            affineTransform.rotate(3 * Math.PI / 2);
	        	tmp = width;
	        	width = height;
	        	height = tmp;
	            break;
	        case 8: // PI / 2
	            affineTransform.translate(0, width);
	            affineTransform.rotate(3 * Math.PI / 2);
	        	tmp = width;
	        	width = height;
	        	height = tmp;
	            break;
	        default:
	            return;
	        }       
	
	        AffineTransformOp affineTransformOp = new AffineTransformOp(affineTransform, AffineTransformOp.TYPE_BILINEAR);  
	        BufferedImage destinationImage = new BufferedImage(width, height, originalImage.getType());
	        destinationImage = affineTransformOp.filter(originalImage, destinationImage);
	        ImageIO.write(destinationImage, extension, new File(path));
    	} catch (IOException e) {
            logger.error("IOException: {}", e);
            e.printStackTrace();
    	} catch (ImageProcessingException e) {
            logger.error("ImageProcessingException: {}", e);
            e.printStackTrace();
    	} catch (MetadataException e) {
            logger.error("MetadataException: {}", e);
            e.printStackTrace();
    	}
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
