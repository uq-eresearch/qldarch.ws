create database UserDB ;
create user 'auth'@'localhost' identified by 'tmppassword' ;
grant all privileges on UserDB.* to 'auth'@'localhost' ;
