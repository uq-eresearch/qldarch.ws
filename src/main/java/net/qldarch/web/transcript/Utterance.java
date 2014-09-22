package net.qldarch.web.transcript;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class Utterance {

  private String speaker;
  private String time;
  private String transcript;

  public Utterance(String speaker) {
    this.speaker = speaker;
  }

  public String getSpeaker() {
    return speaker;
  }

  public String getTime() {
    return time;
  }

  void setTime(String time) {
    this.time = time;
  }

  public String getTranscript() {
    return transcript;
  }

  void setTranscript(String transcript) {
    this.transcript = transcript;
  }

  long getSeconds() {
    return str2sec(getTime());
  }

  String getTranscriptShort() {
    return shorten(getTranscript(), 20);
  }

  static long str2sec(String s) {
    String[] tmp = StringUtils.split(s, ':');
    ArrayUtils.reverse(tmp);
    long[] mul = {1, 60, 3600};
    if(tmp.length > mul.length) {
      throw new RuntimeException("can't convert timestamp "+s);
    }
    long result = 0;
    for(int i=0;i<tmp.length;i++) {
      long l = Long.parseLong(tmp[i]);
      result += l * mul[i];
    }
    return result;
  }

  private static String pad0(long l) {
    String s = Long.toString(l);
    if(s.length() == 1) {
      return "0"+s;
    } else {
      return s;
    }
  }

  static String sec2str(long sec) {
    long hour = sec / 3600;
    long minute = (sec - hour*3600 ) / 60;
    long second = sec - hour * 3600 - minute * 60;
    return String.format("%s:%s:%s", pad0(hour), pad0(minute), pad0(second));
  }

  static String shorten(String s, int max) {
    return (StringUtils.isBlank(s) || (s.length() <=  max))?
        s:StringUtils.substring(s, 0, max) + "...";
  }

}
