package net.qldarch.web.transcript;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class Transcripts {

  private static Reader reader(File f) throws Exception {
    if("doc".equalsIgnoreCase(FilenameUtils.getExtension(f.getName()))) {
      ParseContext c = new ParseContext();
      Detector detector = new DefaultDetector();
      Parser p = new AutoDetectParser(detector);
      c.set(Parser.class, p);
      StringWriter writer = new StringWriter();
      Metadata metadata = new Metadata();
      try(InputStream in = new FileInputStream(f)) {
        ContentHandler handler = new BodyContentHandler(writer);
        p.parse(in, handler, metadata, c); 
        return new StringReader(writer.toString());
      }
    } else {
      return new FileReader(f);
    }
  }

  public static ParseResult parse(File f) {
    try(Reader reader = reader(f)) {
      return new TranscriptParser(reader).parse();
    } catch(Exception e) {
      return new ParseResult("failed to parse "+f.getAbsolutePath(), e);
    }
  }

}
