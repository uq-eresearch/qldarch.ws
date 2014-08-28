package net.qldarch.web.shiro;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class ShiroDb {

  private static final String DS_PATH = "java:/comp/env/jdbc/UserDB";

  public static interface Work<T> {
    public T run(Connection con) throws Exception;
  }

  private DataSource datasource() {
    try {
      InitialContext cxt = new InitialContext();
      DataSource ds = (DataSource) cxt.lookup(DS_PATH);
      if ( ds == null ) {
         throw new RuntimeException(String.format("no datasource at %s", DS_PATH));
      } else {
        return ds;
      }
    } catch(NamingException e) {
      throw new RuntimeException("failed to retrieve datasource", e);
    }
  }

  private <T> T execute(Work<T> work) {
    Connection con = null;
    try {
      DataSource ds = datasource();
      con = ds.getConnection();
      con.setAutoCommit(false);
      return work.run(con);
    } catch(Exception e) {
      try {con.rollback();} catch(Exception sqle) {}
      throw new RuntimeException("got exception while executing transaction, rollback", e);
    } finally {
      if(con != null) {
        try {con.commit();} catch(SQLException e) {}
        try {con.close();} catch(SQLException e) {}
      }
    }
  }

  public ShiroUser get(final String username) {
    return execute(new Work<ShiroUser>() {
      @Override
      public ShiroUser run(Connection con) throws Exception {
        try(PreparedStatement pstmt = con.prepareStatement(
            "SELECT users.email, user_roles.role_name"
                + " FROM users, user_roles"
                + " WHERE users.username = ? AND user_roles.username = users.username")) {
          pstmt.setString(1, username);
          try(ResultSet rs = pstmt.executeQuery()) {
            return rs.next()?new ShiroUser(
                username, rs.getString("email"), rs.getString("role_name")):null;
          }
        }
      }});
  }

}
