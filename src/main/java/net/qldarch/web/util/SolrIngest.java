package net.qldarch.web.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.transcript.ParseResult;
import net.qldarch.web.transcript.Transcripts;
import net.qldarch.web.transcript.Utterance;

import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CharStreams;

public class SolrIngest {

    private RdfDataStoreDao rdfDao;
    public static Logger logger = LoggerFactory.getLogger(SolrIngest.class);
    
	public static void ingestArticle(URI resource) {		
		RdfDataStoreDao rdfDao = new RdfDataStoreDao();
		try {
			String query =  "SELECT ?s ?title ?periodical ?source ?orig " + 
					"WHERE {" +
				    "  BIND (<" + resource.toString() + "> AS ?s) ." +
					"  ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://qldarch.net/ns/rdf/2012-06/terms#Article>." +
					"  ?s <http://purl.org/dc/terms/title> ?title." +
					"  OPTIONAL { ?s <http://qldarch.net/ns/rdf/2012-06/terms#periodicalTitle> ?periodical. }" +
					"  ?s <http://qldarch.net/ns/rdf/2012-06/terms#hasFile> ?o." +
					"  ?o <http://qldarch.net/ns/rdf/2012-06/terms#sourceFilename> ?orig." +
					"  ?o <http://qldarch.net/ns/rdf/2012-06/terms#systemLocation> ?source." +
					"}";
			logger.debug("SolrIngest DELETE evidence performing SPARQL query:\n{}", query);
	
			Map<String, String> article = rdfDao.queryForRdfResource(query, 
					Arrays.asList("title", "periodical", "source", "orig"));
			
            String bodyText = CharStreams.toString(new FileReader(
            		"/var/www/html/files/" + article.get("source").replace("\"", "")));
            
            File temp = new File(
            		"/var/www/html/files/article/" + article.get("source").replace("\"", ""));

        	Document document = DocumentHelper.createDocument();
            Element root = document.addElement("add")
                .addAttribute("commitWithin", "30000")
                .addAttribute("overwrite", "true");
            
            Element doc = root.addElement("doc");
            doc.addElement("field")
                .addAttribute("name", "id")
                .addText(resource.toString().replace("\"", ""));
            doc.addElement("field")
                .addAttribute("name", "article")
                .addText(bodyText);
            if (article.containsKey("title")) {
                doc.addElement("field")
                    .addAttribute("name", "title")
                    .addText(article.get("title").replace("\"", ""));
            }
            if (article.containsKey("periodical")) {
                doc.addElement("field")
                    .addAttribute("name", "periodical")
                    .addText(article.get("periodical").replace("\"", ""));
            }
            if (article.containsKey("source")) {
                doc.addElement("field")
                    .addAttribute("name", "system_location")
                    .addText(article.get("source").replace("\"", ""));
            }
            if (article.containsKey("orig")) {
                doc.addElement("field")
                    .addAttribute("name", "original_filename")
                    .addText(article.get("orig").replace("\"", ""));
            }
                
            XMLWriter writer = new XMLWriter(FileUtils.openOutputStream(temp),
                    OutputFormat.createPrettyPrint());
            writer.write(document);
            writer.close();
            
            HttpURLConnection conn = (HttpURLConnection)(new URL("http://localhost:8080/solr/update")).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setFollowRedirects(false);
            conn.setRequestProperty("Content-type", "text/xml");
            conn.setFixedLengthStreamingMode(temp.length());
            
            try {
                OutputStream out = conn.getOutputStream();
                try {
                    FileUtils.copyFile(temp, out);
                    InputStream in;
                    if (conn.getResponseCode() < 400) {
                        in = conn.getInputStream();
                    } else {
                        String errmsg = String.format("Solr returned %d for %s, %s",
                                conn.getResponseCode(), temp.toString(),
                                conn.getResponseMessage());

                        in = conn.getErrorStream();
                    }

                    try {
                        FileUtils.copyInputStreamToFile(in, temp);
                    } finally {
                        if (in != null) in.close();
                    }
                } finally {
                    if (out != null) out.close();
                }
            } finally {
                conn.disconnect();
                temp.delete();
            }
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	public static void ingestTranscript(URI resource) {
		RdfDataStoreDao rdfDao = new RdfDataStoreDao();
		try {
			String query =  "SELECT ?s ?source " + 
					"WHERE {" +
				    "  BIND (<" + resource.toString() + "> AS ?s) ." +
					"  ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://qldarch.net/ns/rdf/2012-06/terms#Interview>." +
					"  ?s <http://qldarch.net/ns/rdf/2012-06/terms#hasTranscript> ?o." +
					"  ?o <http://qldarch.net/ns/rdf/2012-06/terms#systemLocation> ?source." +
					"}";
			logger.debug("SolrIngest DELETE evidence performing SPARQL query:\n{}", query);
	
			Map<String, String> article = rdfDao.queryForRdfResource(query, 
					Arrays.asList("source"));
        	ParseResult pr = Transcripts.parse(
        	    new File("/var/www/html/files/" + article.get("source").replace("\"", "")));
        	if(!pr.ok()) {
        	  throw new Exception(pr.msg(), pr.cause());
        	}
        	Document document = DocumentHelper.createDocument();
            Element root = document.addElement("add")
                .addAttribute("commitWithin", "30000")
                .addAttribute("overwrite", "true");
            for (Utterance utterance : pr.transcript().getExchanges()) {
                Element doc = root.addElement("doc");
                doc.addElement("field")
                    .addAttribute("name", "id")
                    .addText(resource.toString() + "#" + utterance.getTime());
                doc.addElement("field")
                    .addAttribute("name", "interview")
                    .addText(resource.toString());
                doc.addElement("field")
                    .addAttribute("name", "transcript")
                    .addText(utterance.getTranscript());
            }

            File temp = new File(
            		"/var/www/html/files/article/" + article.get("source").replace("\"", ""));
                
            XMLWriter writer = new XMLWriter(FileUtils.openOutputStream(temp),
                    OutputFormat.createPrettyPrint());
            writer.write(document);
            writer.close();
            
            HttpURLConnection conn = (HttpURLConnection)(new URL("http://localhost:8080/solr/update")).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setFollowRedirects(false);
            conn.setRequestProperty("Content-type", "text/xml");
            conn.setFixedLengthStreamingMode(temp.length());
            
            try {
                OutputStream out = conn.getOutputStream();
                try {
                    FileUtils.copyFile(temp, out);
                    InputStream in;
                    if (conn.getResponseCode() < 400) {
                        in = conn.getInputStream();
                    } else {
                        String errmsg = String.format("Solr returned %d for %s, %s",
                                conn.getResponseCode(), temp.toString(),
                                conn.getResponseMessage());

                        in = conn.getErrorStream();
                    }

                    try {
                        FileUtils.copyInputStreamToFile(in, temp);
                    } finally {
                        if (in != null) in.close();
                    }
                } finally {
                    if (out != null) out.close();
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	public static void delete(URI resource) {	
		System.out.println("Delete Resource " + resource.toString());
		RdfDataStoreDao rdfDao = new RdfDataStoreDao();
		try {
			String query =  "SELECT ?source " + 
							"WHERE {" +
						    "  BIND (<" + resource.toString() + "> AS ?s) ." +
						    "  ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://qldarch.net/ns/rdf/2012-06/terms#Article>." +
							"  ?s <http://qldarch.net/ns/rdf/2012-06/terms#hasFile> ?o." +
							"  ?o <http://qldarch.net/ns/rdf/2012-06/terms#systemLocation> ?source." +
							"}";
			logger.debug("SolrIngest DELETE evidence performing SPARQL query:\n{}", query);
			
			Map<String, String> article = rdfDao.queryForRdfResource(query, Arrays.asList("source"));
						
			if (article.containsKey("source")) {
				// Is Article
				String source = article.get("source").replace("\"", "");
				source = source.substring(12,25);
				URL url = new URL("http://localhost:8080/solr/update?stream.body=%3Cdelete%3E%3Cquery%3E("
						+ "system_location:" + source + ")%3C/query%3E%3C/delete%3E&commit=true");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		        conn.setRequestMethod("GET");
		        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		        rd.close();
			} else {
				// Is Interview
				String source = resource.toString();
				source = source.substring(source.lastIndexOf("#") + 1);
				URL url = new URL("http://localhost:8080/solr/update?stream.body=%3Cdelete%3E%3Cquery%3E("
						+ "interview:" + source + ")%3C/query%3E%3C/delete%3E&commit=true");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		        conn.setRequestMethod("GET");
		        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		        rd.close();
			}
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
}
