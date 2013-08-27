package net.qldarch.web.service;

public class Validators {
    public static final Pattern USERNAME_REGEX = Pattern.compile("[a-zA-Z0-9_]{3,}")
    public static String username(String username) {
        if (USERNAME_REGEX.matcher(username).matches()) {
            return username;
        } else {
            logger.warn("Invalid username provided: {}", username);
            throw new IllegalArgumentException("Invalid username");
        }
    }
}
