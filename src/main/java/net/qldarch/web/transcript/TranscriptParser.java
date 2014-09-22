package net.qldarch.web.transcript;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TranscriptParser {

  private static final Logger logger = LoggerFactory.getLogger(TranscriptParser.class);

  private static final Pattern TRANSCRIPT_PATTERN = Pattern.compile(
      "^\\[?\\s*(\\d\\d?)\\s*\\:\\s*(\\d\\d?)\\s*\\:\\s*(\\d\\d?)\\s*\\]?\\s+(.*)$");

  private final LineNumberReader reader;

  private boolean steadyTimestamps = true;

  public TranscriptParser(Reader reader) {
    this.reader = new LineNumberReader(reader);
  }

  private static ObjectMapper mapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
    return mapper;
  }

  private String cleanTimestamp(String s) {
    return Utterance.sec2str(Utterance.str2sec(s));
  }

  private String getTimestamp(String s) {
    Matcher m = TRANSCRIPT_PATTERN.matcher(StringUtils.strip(s));
    return m.matches()?cleanTimestamp(
        String.format("%s:%s:%s", m.group(1), m.group(2), m.group(3))):null;
  }

  private String getTranscript(String s) {
    Matcher m = TRANSCRIPT_PATTERN.matcher(StringUtils.strip(s));
    return m.matches()?StringUtils.strip(m.group(4)):null;
  }

  private boolean startsWithTimestamp(String s) {
    return getTimestamp(s) != null;
  }

  public ParseResult parse() {
    try {
      Transcript transcript = parseTranscript();
      return new ParseResult(transcript, mapper().writeValueAsString(transcript));
    } catch(Exception e) {
      String msg = String.format(
          "transcript parse faild on line %s: %s", reader.getLineNumber(), e.getMessage());
      return new ParseResult(msg, e);
    }
  }

  private String cleanSpeaker(String s) {
    return StringUtils.removeEnd(StringUtils.strip(s), ":");
  }

  private void addUtterance(Transcript t, Utterance u, String line) {
    u.setTime(getTimestamp(line));
    u.setTranscript(getTranscript(line));
    t.addUtterance(u, steadyTimestamps);
  }

  private Utterance continueLastSpeaker(Transcript transcript) {
    Utterance u = transcript.last();
    return (u!=null)?new Utterance(u.getSpeaker()):null;
  }

  private boolean command(String line) {
    String[] tmp = StringUtils.split(line);
    if((tmp.length) >= 1 && "set".equalsIgnoreCase(tmp[0])) {
      if(tmp.length >= 2 && "steady_timestamps".equalsIgnoreCase(tmp[1])) {
        if(tmp.length >= 3 && "off".equalsIgnoreCase(tmp[2])) {
          steadyTimestamps = false;
        } else {
          steadyTimestamps = true;
        }
        return true;
      }
    }
    return false;
  }

  private Transcript parseTranscript() throws IOException {
    Transcript transcript = new Transcript();
    String line;
    Utterance utterance = null;
    while((line = reader.readLine()) != null) {
      if(StringUtils.isBlank(line) || StringUtils.startsWith(line, "#")) {
        continue;
      }
      line = StringUtils.strip(line);
      if(command(line)) {
        continue;
      }
      //ignore the rest if end of transcript marker is found
      if("END OF TRANSCRIPT".equalsIgnoreCase(StringUtils.strip(line))) {
        break;
      }
      // continue current utterance
      if(utterance != null) {
        if(startsWithTimestamp(line)) {
          addUtterance(transcript, utterance, line);
          utterance = null;
        } else {
          String msg = String.format("parse error on line %s '%s'",
              reader.getLineNumber(), Utterance.shorten(line, 20));
          throw new RuntimeException(msg);
        }
      } else if(!transcript.hasTitle()) {
        transcript.setTitle(line);
      } else if(!transcript.hasDate()) {
        transcript.setDate(line);
      } else {
        // start new utterance
        if(startsWithTimestamp(line)) {
          utterance = continueLastSpeaker(transcript);
          if(utterance == null) {
            throw new RuntimeException(
                "no speaker for current transcript on line "+reader.getLineNumber());
          }
          addUtterance(transcript, utterance, line);
          utterance = null;
        } else {
          // speaker line
          utterance = new Utterance(cleanSpeaker(line));
        }
      }
    }
    if(utterance != null) {
      logger.debug("unexpected utterance after loop");
    }
    return transcript;
  }
}
