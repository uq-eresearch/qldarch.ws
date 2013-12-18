insert into users (username, email, verified, password) values ('admin', 'a.muys@uq.edu.au', 1, '$shiro1$SHA-256$500000$P8/GMXbaOJoRmWLG/je6qg==$kFlttSAtxcBeTYSct+bia426jb7+X5z30f483Vt6/Kg=') ;
insert into roles (role_name, description) values ('root', 'The superuser administration role') ; 
insert into permissions (permission, description) values ('*', 'Global wildcard permission' ) ;
insert into user_roles (username, role_name) values ('admin', 'root') ;
insert into roles_permissions (permission, role_name) values ('*', 'root') ;
