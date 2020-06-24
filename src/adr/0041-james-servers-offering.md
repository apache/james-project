# 41. James server offering

Date: 2020-06-22

## Status

Accepted (lazy consensus)

## Context

The Apache James project is willing to provide ready-to-use Mail Servers for our users.

There exists several use cases for such servers:

 - **Self hosting**: a hobbyist, or a small organization is willing to host a mail service. They 
have limited infrastructure, time and knowledge and are mostly looking for an easy to use 
solution.

 - **Customization use cases**: operators are willing to provide advanced, integrated features
for emails. They are willing to do advanced configuration and write extensions in order to customize 
the behaviour of Apache James.

 - **Distributed Mail server**: scaling mail storage infrastructure is notoriously hard as most 
mail server leverages only the file system. James project delivers a distributed mail server 
leveraging modern noSQL infrastructure to ease scaling.

### Current situation

We currently deliver as part of the Apache James server:

 - **Spring server** (`server/app`): barely maintained, Spring server exposes configuration allowing 
class level overrides, resulting in untested component combination leading to unexpected runtime errors. 
Its usage requires configuration to adapt it to the user use cases, which can be 
not obvious for people with limited knowledge/resources, and can discourage self hosting usages.

Guice servers includes:

 - **Cassandra - guice** (`server/container/guice/cassandra-guice`) and its LDAP counter-part
(`server/container/guice/cassandra-guice-ldap`): this guice server relies on a Cassandra & ElasticSearch database
for storing data, but cannot be used in a clustered setup, as there is no messaging happening between nodes, which 
prevents some James features to work correctly. This server furthermore is extensible, and ships many extra features 
by default.

 - **Distributed James** (`server/container/guice/cassandra-rabbitmq-guice`) and its LDAP counter part
(`server/container/guice/cassandra-rabbitmq-ldap-guice`) enabled to achieve the distributed use case. This
server furthermore is extensible, and ships many extra features by default. It requires good knowledge of 
underlying services like Cassandra, S3-like storage, RabbitMQ and ElasticSearch.

 - **Guice memory** (`server/container/guice/memory-guice`) ships a server based on memory storage. Easy to boot, 
without external dependencies, we mostly rely on it for testing purposes.

 - **JPA Guice** (`server/container/guice/jpa-guice`) ships an extensible server based on JPA to store data in 
a RDBMS. This server has no external dependencies by default. However the still large amount of configuration 
can make new comers confused. It currently satisfies operator looking for self hosting solutions as well as 
those looking for an extensible server.

 - **JPA SMTP** (`server/container/guice/jpa-smtp`) demonstrates a Mail Transfer Agent, dedicated to mail
processing, that does not ship the James Mailbox, as well as Mail Delivery protocols like IMAP, JMAP or POP3.

We can notice that this offer is specified in technical terms, and does not directly map to identified user needs.

Also the large amount of servers makes the choice harder to make for newcomers.

## Decision

We should deliver a subset of mail server directly addressing users needs, and brand them according to their use cases
and not due to their technical details.

In order to get a clearer offer we should first reduce the server cardinality. This can be achieved via:

 - **Spring server** deprecation and removal
 - Merging Guice servers with their LDAP variations (see [ADR 36 about guice module choosing](0036-against-use-of-conditional-statements-in-guice-modules.md))

We should then rename our servers in order to match the use cases:

 - **Basic server** addresses the self-hosting use case. It will be based on current *jpa-guice* server but with the clear intent to 
lower adoption barriers. It exposes limited configurations in order not to confuse the user. Configuration and documentation is centered 
around self hosting use cases. Configuration and documentation addresses the self-hosting usage without exposing underlying James concepts.

The **Basic Server** is a subset of the advanced server, and differs from it from documentation and configuration. Users of the basic server feeling
constraint by its minimalistic configuration capabilities and features would then be able to easily upgrade to the advanced server.

 - **Advanced server** addresses the customization use cases. It will be based on the current *jpa-guice* server. It differs from the basic server
as more features will be packaged as part of it, configuration shows via examples advanced usage possibilities, and the documentation is centered 
around feature extensibility.

 - **Cassandra Guice** (TODO rebrand me) still have community traction, 

Note that transitionning from basic server to the advanced one will be easy as they rely on the same technologies.

 - **Distributed server** will remain unchanged.

 - **Testing server** will rely on current `memory guice` server.

 - **Mail processing server** will rely on current `JPA SMTP guice` server.

Also, we should separate our server application from the technical guice bindings, and group them together for better discoverability.

We should adopt this folder structure:

```
server/distributions
\_____ basic
\_____ advanced
\_____ distributed
\_____ testing
\_____ mail-processing
\_____ cassandra-guice (TODO rebrand me please)
```

## Consequences

By better focussing our offer on community needs we expect a larger adoption of James project servers.

Such a split will furthermore ease documentation efforts.

The split between basic and advanced server will furthermore ease bug reports as the customization capabilities of the basic server are limited.

In order to conduct this split:

 - We will need to work on JAMES-2335 for at least mailetContainer.xml, imapServer.xml and smtpServer.xml
 - Work on simplified configuration formats, oriented toward the self hosting use case, in order to generate these configuration POJOs. Configuration
examples of self-hosting oriented, zero James-specific-knowledge configuration files for the basic server can be found below.

Additionally, working on the memory footprint of the basic server would enable to run it on limited environments, which could help adoption.

## Example

Here is an example of replacement of "mail processing" configuration file. It does cover self-hosting expected features while not 
exposing James underlying concepts of `Mailet` and `Matcher`.

```
# (optional)
# smtp.gateway.host=smtpout.my.cloud.supplier
# smtp.gateway.password=xyz

relay.allow.network=172.0.0.0/18

spf.verify=true

dkim.verify=true
dkim.sign=true
dkim.key=/path/to/something

spamassassin.host=172.2.5.6
```

Here is an example of `ssl.properties` from which we could derive working 

```
starttls.enable=true
ssl.compulsary=false

ssl.keystore=/path/to/keystore
ssl.keystore.password=xyz
```

Disclaimer: these are meant as examples to highlight the use of the basic server. The
exact basic server configuration format would need to be discussed elsewhere.
