package net.qldarch.web.service;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class User {
    public static Logger logger = LoggerFactory.getLogger(User.class);

    public static String USER_REFERENCE_GRAPH_FORMAT = "http://qldarch.net/users/%s/references";
    public static String USER_URI_FORMAT = "http://qldarch.net/users/%s";

    private String username;

    private User(String username) {
        this.username = Validators.username(username);
    }

    public static User currentUser() {
        Subject currentUser = SecurityUtils.getSubject();
        String username = (String)currentUser.getPrincipal();

        return new User(username);
    }

    public boolean isAnon() {
        return username == null || username.isEmpty();
    }

    public URI getUserURI() {
        return URI.create(String.format(USER_URI_FORMAT, username));
    }

    public URI getReferenceGraph() {
        return URI.create(String.format(USER_REFERENCE_GRAPH_FORMAT, username));
    }
}
