CREATE TABLE users (
    username varchar(50) PRIMARY KEY,
    email varchar(100) NOT NULL,
    verified boolean NOT NULL DEFAULT FALSE,
    password varchar(50) NOT NULL,
    password_salt varchar(100) NOT NULL
) ENGINE=INNODB DEFAULT CHARSET=latin1;

CREATE TABLE roles (
    role_name varchar(50) PRIMARY KEY,
    description varchar(500)
) ENGINE=INNODB DEFAULT CHARSET=latin1;

CREATE TABLE permissions (
    permission varchar(50) PRIMARY KEY,
    description varchar(50)
) ENGINE=INNODB DEFAULT CHARSET=latin1;

CREATE TABLE user_roles (
    username varchar(50) NOT NULL,
    role_name varchar(50) NOT NULL,
    PRIMARY KEY (username,role_name),
    FOREIGN KEY (username)
        REFERENCES users(username)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    FOREIGN KEY (role_name)
        REFERENCES roles(role_name) 
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=latin1;

CREATE TABLE roles_permissions (
    permission varchar(50) NOT NULL,
    role_name varchar(50) NOT NULL,
    PRIMARY KEY (permission,role_name),
    FOREIGN KEY (permission)
        REFERENCES permissions(permission)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    FOREIGN KEY (role_name)
        REFERENCES roles(role_name) 
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=latin1;
