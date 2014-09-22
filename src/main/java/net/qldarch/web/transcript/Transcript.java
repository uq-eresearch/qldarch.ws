package net.qldarch.web.transcript;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

public class Transcript {

  private String title;
  private String date;
  private List<Utterance> exchanges = Lists.newArrayList();

  public String getTitle() {
    return title;
  }

  void setTitle(String title) {
    this.title = title;
  }

  public String getDate() {
    return date;
  }

  void setDate(String date) {
    this.date = date;
  }

  void addUtterance(Utterance u, boolean steadyTimestamp) {
    if(StringUtils.isBlank(u.getSpeaker())) {
      throw new RuntimeException("speaker field not set on utterance");
    }
    if(StringUtils.isBlank(u.getTime())) {
      throw new RuntimeException("time field not set on utterance");
    }
    if(StringUtils.isBlank(u.getTranscript())) {
      throw new RuntimeException("transcript field not set on utterance");
    }
    // just make sure that converting into seconds works (without throwing an exception)
    u.getSeconds();
    if(steadyTimestamp) {
      Utterance p = last();
      if((p != null) && (u.getSeconds() < p.getSeconds())) {
        throw new RuntimeException(String.format(
            "utterance is earlier than previous utterance '%s %s'",
            u.getTime(), u.getTranscriptShort()));
      }
    }
    exchanges.add(u);
  }

  public boolean hasTitle() {
    return title != null;
  }

  public boolean hasDate() {
    return date != null;
  }

  public List<Utterance> getExchanges() {
    return Collections.unmodifiableList(exchanges);
  }

  public Utterance last() {
    return getExchanges().isEmpty()?null:getExchanges().get(getExchanges().size()-1);
  }

}
