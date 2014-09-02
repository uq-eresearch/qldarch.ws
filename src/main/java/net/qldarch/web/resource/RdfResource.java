package net.qldarch.web.resource;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.qldarch.web.service.MetadataRepositoryException;
import net.qldarch.web.service.RdfDataStoreDao;
import net.qldarch.web.service.RepositoryQuery;

import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.shiro.SecurityUtils;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ContextStatementImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@Path("/rdf")
public class RdfResource {

  @JsonAutoDetect(fieldVisibility = Visibility.ANY,
      getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
  @JsonIgnoreProperties({"encode"})
  @SuppressWarnings("unused")
  private static class CompactStatement implements Statement {

    private boolean encode;
    private String subject;
    private String predicate;
    private String object;
    private String context;

    private String stringValue(Value v) {
      if(v == null) {
        return null;
      } else {
        if(v instanceof URI) {
          return String.format("<%s>", v.stringValue());
        } else {
          return v.stringValue();
        }
      }
    }

    private String encode(String s) {
      if(encode) {
        try {
          return new URLCodec().encode(s);
        } catch(EncoderException e) {
          throw new RuntimeException(e);
        }
      } else {
        return s;
      }
    }

    public CompactStatement(Statement s, boolean encode) {
      this.encode = encode;
      this.subject = encode(stringValue(s.getSubject()));
      this.predicate = encode(stringValue(s.getPredicate()));
      this.object = encode(stringValue(s.getObject()));
      this.context = encode(stringValue(s.getContext()));
    }

    @Override
    public Resource getContext() {
      throw new RuntimeException("not supported");
    }

    @Override
    public Value getObject() {
      throw new RuntimeException("not supported");
    }

    @Override
    public URI getPredicate() {
      throw new RuntimeException("not supported");
    }

    @Override
    public Resource getSubject() {
      throw new RuntimeException("not supported");
    }
  }

  private static String chopAngle(String s) {
    return (isNotBlank(s) && s.startsWith("<") && s.endsWith(">"))?s.substring(1, s.length()-1):s;
  }

  private static URI uri(String u) {
    return isBlank(u)?null:new URIImpl(chopAngle(u));
  }

  private static Resource resource(String r) {
    return uri(r);
  }

  private static boolean isUri(String u) {
    try {
      if(u.startsWith("<") && u.endsWith(">")) {
        uri(u.substring(1, u.length()-1));
      }
    } catch(Exception e) {}
    return false;
  }

  private static Value value(String v) {
    if(isBlank(v)) {
      return null;
    } else {
      try {
        return isUri(v)?uri(v):new LiteralImpl(v);
      } catch(Exception e) {
        return new LiteralImpl(v);
      }
    }
  }

  private RepositoryResult<Statement> getStatements(RepositoryConnection con,
      String s, String p, String o) throws RepositoryException {
    Resource qs = resource(s);
    URI qp = uri(p);
    Value qo = value(o);
    return con.getStatements(qs, qp, qo, true);
  }

  private Statement statement(Statement statement, boolean compact, boolean encode) {
    return compact?new CompactStatement(statement, encode):statement;
  }

  private List<Statement> asList(RepositoryResult<Statement> statements,
      boolean compact, boolean encode) throws RepositoryException {
    if(compact) {
      List<Statement> result = Lists.newArrayList();
      while(statements.hasNext()) {
        result.add(statement(statements.next(), true, encode));
      }
      return result;
    } else {
      return statements.asList();
    }
  }

  private static ObjectMapper mapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
    return mapper;
  }

  @GET
  @Produces("application/json")
  public String execute(@QueryParam("s") final String s, @QueryParam("p") final String p,
      @QueryParam("o") final String o,
      @DefaultValue("true") @QueryParam("compact") final boolean compact,
      @DefaultValue("false") @QueryParam("encode") final boolean encode) {
    try {
      List<Statement> statements = new RdfDataStoreDao().execute(
          new RepositoryQuery<List<Statement>>() {
        @Override
        public List<Statement> query(RepositoryConnection con)
            throws RepositoryException, MetadataRepositoryException {
          return asList(getStatements(con, s, p, o), compact, encode);
        }
      });
      return mapper().writeValueAsString(statements);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

  @POST
  @Produces("application/json")
  public Response update(@QueryParam("s") final String s, @QueryParam("p") final String p,
      @QueryParam("o") final String o, @QueryParam("ns") final String ns,
      @QueryParam("np") final String np, @QueryParam("no") final String no,
      @DefaultValue("true") @QueryParam("compact") final boolean compact,
      @DefaultValue("false") @QueryParam("encode") final boolean encode) {
    if(!SecurityUtils.getSubject().hasRole("root")) {
      return Response.status(Status.FORBIDDEN).type(MediaType.APPLICATION_JSON).entity(
          ImmutableMap.of("msg","permission denied")).build();
    }
    try {
      Statement statement = new RdfDataStoreDao().execute(new RepositoryQuery<Statement>() {
        @Override
        public Statement query(RepositoryConnection con)
            throws RepositoryException, MetadataRepositoryException {
          List<Statement> statements = getStatements(con, s, p, o).asList();
          if(statements.isEmpty()) {
            throw new RuntimeException("so statement selected");
          } else if(statements.size() > 1) {
            throw new RuntimeException("more then 1 statement selected");
          } else {
            Statement statement = statements.get(0);
            Resource subject = resource(ns);
            URI predicate = uri(np);
            Value object = value(no);
            if(!((subject == null) && (predicate == null) && (object == null))) {
              Statement updated = new ContextStatementImpl(
                  (subject!=null?subject:statement.getSubject()),
                  (predicate!=null?predicate:statement.getPredicate()),
                  (object!=null?object:statement.getObject()),
                  statement.getContext());
              con.remove(statement);
              con.add(updated);
              return updated;
            } else {
              return statement;
            }
          }
        }
      });
      return Response.status(Status.OK).type(MediaType.APPLICATION_JSON).entity(
          mapper().writeValueAsString(statement(statement, compact, encode))).build();
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }

}
