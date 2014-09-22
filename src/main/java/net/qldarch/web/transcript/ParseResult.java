package net.qldarch.web.transcript;

public class ParseResult {

  private boolean ok;

  private String msg;

  private String json;

  private Exception cause;

  private Transcript transcript;

  public ParseResult(String msg, Exception cause) {
    this.ok = false;
    this.msg = msg;
    this.cause = cause;
  }

  public ParseResult(Transcript transcript, String json) {
    this.ok = true;
    this.json = json;
    this.transcript = transcript;
  }

  public boolean ok() {
    return ok;
  }

  public String msg() {
    return msg;
  }

  public String json() {
    return json;
  }

  public Exception cause() {
    return cause;
  }

  public Transcript transcript() {
    return transcript;
  }

}
