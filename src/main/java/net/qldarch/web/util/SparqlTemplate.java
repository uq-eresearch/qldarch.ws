package net.qldarch.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroupFile;

public class SparqlTemplate {

  private static final Logger logger = LoggerFactory.getLogger(SparqlTemplate.class);

  public static interface Binder {
    public void bind(ST template);
  }

  private STGroupFile group;

  public SparqlTemplate(String filename) {
    group = new STGroupFile(filename);
    if(group == null) {
      throw new RuntimeException(String.format("STGroupFile %s not found", filename));
    }
  }

  public String render(String name) {
    return render(name, null);
  }

  public synchronized String render(String name, Binder binder) {
    ST template = group.getInstanceOf(name);
    if(template == null) {
      throw new RuntimeException(String.format(
          "String template %s in STGroupFile %s not found", name, group));
    }
    if(binder != null) {
      binder.bind(template);
    }
    String s = template.render();
    logger.debug("generated query '{}' from group '{}' and name '{}' ", s, group, name);
    return s;
  }

}
