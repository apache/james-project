Apache James Server
===================

This document is an effort to document the architecture and components that make Apache James. As a new committer to
the project I found out fast that the lack of documentation made development slow. On my quest to solving issues I
decided to document what I understand about Apache James.

Feel free to add/change things if you think it's required.

Server structure: components and services
=========================================

The directory structure is presented bellow. All directories are maven projects and correspond roughly to a component/service.
I'll explain what I mean by 'component' and 'service' bellow.

~~~
|-- app
|-- container
    `-- cli
    `-- core
    `-- lifecycle-api
    `-- filesystem-api
    `-- mailbox-adapter
    `-- spring
    `-- util
|-- data
    `-- data-api
    `-- data-file
    `-- data-jdbc
    `-- data-jpa
    `-- data-ldap
    `-- data-library
|-- dns-service
    `-- dnsservice-api
    `-- dnsservice-dnsjava
    `-- dnsservice-library
|-- karaf
    `-- distribution
     `-- features
     `-- integration
|--mailet
    `-- mailetcontainer-api
    `-- mailetcontainer-camel
    `-- mailets
|-- protocols
    `-- protocols-imap4
    `-- protocols-library
    `-- protocols-lmtp
    `-- protocols-pop3
    `-- protocols-smtp
    `-- fetchmail
|-- queue
    `-- queue-activemq
    `-- queue-api
    `-- queue-file
    `-- queue-jms

~~~

Apache James is made up of *components* and *services*. Most of the components implement an API and there are several
implementations that you can choose from. The only difference between components and services is the fact that a
service is a component designed to be consumed. It provides a useful feature.

Take **dns-service** for example:**dnsservice-api** and **dnsservice-library** are components while **dnsservice-dnsjava**
is a service that implements an API that James uses for resolving email domain names to IP addresses.
What you need to remember is that you can instantiate a service and it will provide useful value for you.
The only way to know which one is a service and which one is a component is by reading the documentation or code.

James has a nice architecture based on reusable components/services. Services are important. Components are just means to
get to create services without duplicating a lot of code.

Services in Apache James Server
===============================

Being an email server, James is composed of services related to this task:

* dns-service - Service for resolving (email) domain names
* fetch-mail scheduler - Service for scheduling email fetching
* queue - Service used for spooling email
* data (user and domain) repository - Service for storing users, passwords and email domains managed by James
* mail-store - Service for persisting email messages in mailboxes
* mailet-container - Service for processing emails using Mailets
* protocol services: SMTP, POP3, IMAP4, LMTP - Services that implement specific protocols
* JMX monitoring - Service that provides application monitoring via JMX
* configuration - more of a concern than a service

Services are created via Spring. You can find the main configuration **spring-server.xml** file in the **spring** component.
Each service defines his own Spring context which is imported in the main application context.

Application bootstrap
=====================

There are multiple ways to start/deploy James Server:

* as a standalone application/service, using scripts provided by the **app**
* as a web application (WAR) inside an application server (Apache Tomcat, Jetty, GlassFish)
* inside an OSGi container - Apache Karaf (in progress)

DNS Service
===========

Provides DNS resolution for (email) domain names. James defines an API and has one implementation that relies on
dnsjava (http://www.dnsjava.org/) for this task. You can find the code under **dns-service** directory.

This service does not depend on other services.

FetchMail Service
=================


Queue Service
=============


User and domain (data) repository Service
=========================================


Mail store Service
==================


Mailet container Service
========================

Protocol services
=================


JMX monitoring Service
======================




