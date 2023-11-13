create user rlsuser WITH PASSWORD 'secret1';
create database rlsdb;
grant all privileges on database rlsdb to rlsuser;
\c rlsdb;
create schema if not exists rlsschema authorization rlsuser;