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
|-- hbase           -- Mailbox implementation over HBase
|-- jcr             -- Mailbox implementation over Java Content Repository (JCR)
|-- jpa             -- Database Mailbox implementation using Java Persistence API
|-- lucene          -- Email indexing module with Apache Lucene
|-- maildir         -- Email storage using Maildir format http://en.wikipedia.org/wiki/Maildir
|-- memory          -- In memory Mailbox implementation - good for testing
|-- spring          -- Spring module - starts a specific mailbox implementation
|-- store           -- Common base/utility classes used in all mailbox implementations
|-- tool            -- Database migration/mailbox export tool
|-- zoo-seq-provider -- Distributed unique ID generator using Zookeeper and Curator (Clustering James Mailbox)
~~~

Mailbox JPA
===========

Persist email messages inside any database that is supported by your Java Persistence Api provider. Currently James uses
OpenJPA (http://openjpa.apache.org/), but it's easy to implement your own.

Mailbox 'In memory' message store
=================================

In module **memory**, does not persist emails. It just keeps them in memory. Fast, and good for testing.
**Note:** Not to be used in production.

Mailbox JCR
===========

Uses Java Content Repository as a persistence layer. Uses Jackrabbit as a provider (http://jackrabbit.apache.org/),
but you could swap in any provider. Comes with all the nice features that Jackrabbit has.


Mailbox Maildir
===============

Implements the Maildir standard for email storage (http://en.wikipedia.org/wiki/Maildir). Works only on GNU/Linux and other
*Nix systems.


Mailbox HBase
=============

Uses Apache HBase (http://hbase.apache.org/) for storing email messages. Provides a scalable email storage. To have a fully
distributed email server you will also need, among others:

* distributed UID generation, look at Zookeeper Sequence Provider (**zoo-seq-provider**) for distributed locking and Mailbox manipulation
* distributed SMTP/IMAP access
* other

Zookeeper Sequence Provider
==========================

Uses Zookeeper and Curator Framework for generating distributed unique ID's, needed for mailbox management from multiple
instances of James (IMAP) servers.


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
