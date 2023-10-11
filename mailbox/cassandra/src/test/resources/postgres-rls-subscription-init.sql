CREATE USER rlsuser WITH PASSWORD 'secret';
CREATE DATABASE rlsdb;
grant all privileges on database rlsdb to rlsuser;