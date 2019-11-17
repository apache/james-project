Apache James Mailbox project
============================

The James Mailbox project aims to provide an email (message) store. The main user of the Mailbox project is James Server
project. The implementations can be used standalone and do not depend on James Server.

The project defines the Mailbox API and has several implementations that you can use. More details bellow.

Overview
========

Apache James Mailbox has the following project (Maven) structure:

~~~
|-- api             -- Mailbox API
|-- jpa             -- Database Mailbox implementation using Java Persistence API
|-- lucene          -- Email indexing module with Apache Lucene
|-- maildir         -- Email storage using Maildir format http://en.wikipedia.org/wiki/Maildir
|-- memory          -- In memory Mailbox implementation - good for testing
|-- spring          -- Spring module - starts a specific mailbox implementation
|-- store           -- Common base/utility classes used in all mailbox implementations
|-- tool            -- Database migration/mailbox export tool
~~~

Mailbox JPA
===========

Persist email messages inside any database that is supported by your Java Persistence Api provider. Currently James uses
OpenJPA (http://openjpa.apache.org/), but it's easy to implement your own.

Mailbox 'In memory' message store
=================================

In module **memory**, does not persist emails. It just keeps them in memory. Fast, and good for testing.
**Note:** Not to be used in production.


Mailbox Maildir
===============

Implements the Maildir standard for email storage (http://en.wikipedia.org/wiki/Maildir). Works only on GNU/Linux and other
*Nix systems.


Building
========

The primary build tool for Apache James Mailbox is maven 3.

On a new checkout start by running
~~~
    $ mvn clean package
~~~

This will compiled all modules

For just building without running junit tests:
~~~
    $ mvn clean package -DskiTests=true
~~~
