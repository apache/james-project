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

 - **Customization use cases**: operators is willing to provide advanced, integrated features
for emails. They are willing to do advanced configuration and write extensions in order to customize 
the behaviour of Apache James.

 - **Distributed Mail server**: scaling mail storage infrastructure is notoriously hard as most 
mail server leverages only the file system. James project delivers a distributed mail server 
leveraging modern noSQL infrastructure to ease scaling.

### Current situation

We currently deliver as part of the Apache James server:

 - **Spring server** (`server/app`): barely maintained, Spring server exposes configuration allowing 
class level overides, resulting in untested componant combinaison leading to unexpected runtime errors. 
Its usage requires configuration to adapt it to the user use cases, wich can be 
not obvious for people with limited knowledge/resources, and can discourage self hosting usages.

Guice servers includes:

 - **Cassandra - guice** (`server/container/guice/cassandra-guice`) and its LDAP counter-part
(`server/container/guice/cassandra-guice-ldap`): about to be deprecated, this guice server relies on
a Cassandra & ElasticSearch database without achieving distribution (as it misses a messaging component).

 - **Distributed James** (`server/container/guice/cassandra-rabbitmq-guice`) and its LDAP counter part
(`server/container/guice/cassandra-rabbitmq-ldap-guice`) enabled to achieve the distributed use case. This
server furthermore is extensible, and ships many extra features by default. It is however hard to operate.

 - **Guice memory** (`server/container/guice/memory-guice`) ships a server based on memory. Easy to boot, 
without external dependencies, we mostly rely on it for testing purposes.

 - **JPA Guice** (`server/container/guice/jpa-guice`) ships an extensible server based on JPA storage. With
a default configuration using Derby embedded database, this server have no external dependencies by default.
However the still large amount of configuration can make new comers confused. It currently satisfies operator 
looking for self hosting solutions as well as those looking for an extensible server.

 - **JPA SMTP** (`server/container/guice/jpa-smtp`) demonstrate a Mail Transfer Agent, dedicated to mail
processing, that do not ship the James Mailbox, as well as Mail Delivery protocols like IMAP, JMAP or POP3.

We can notice that this offer is specified in technical terms, and do not directly map to identified user needs.

Also the large amount of servers makes the choice harder to make for newcomers.

## Decision

We should deliver a subset of mail server directly adressing users needs, and brand them according to their use cases
and not due to their technical details.

In order to get a clearer offer we should first reduce the server cardinality. This can be achieved via:

 - **Spring server** deprecation and removal
 - Merging Guice servers with their LDAP variations (see [ADR 36 about guice module choosing](0036-against-use-of-conditional-statements-in-guice-modules.md))
 - **Cassandra-guice** deprecation and removal

We should then rename our servers in order to match the use cases:

 - **Basic server** addresses the self-hosting use case. It will be based on current *jpa-guice* server but with the clear intent to 
lower adoption barriers. It exposes limited configurations in order not to confuse the user. Configuration and documentation is centered 
around self hosting use cases.

 - **Advanced server** addresses the customization use cases. It will be based on the current *jpa-guice* server. It differs from the basic server
as more features will be packaged as part of it, configuration shows via examples advanced usage possibilities, and the documentation is centered 
around feature extensibility.

Note that transitionning from basic server to the advanced one will be easy as they rely on the same technologies.

 - **Distributed server** will remain unchanged.

 - **Testing server** will rely on current `memory guice` server.

 - **Mail processing server** will rely on current `JPA SMTP guice` server.

Also, we should separate our server application from the technical guice bindings, and group them together for better discoverability.

We should adopt this folder structure:

```
server/apps
\_____ basic-server
\_____ advanced-server
\_____ distributed-server
\_____ testing-server
\_____ mail-processing-server
```

## Consequences

By better focussing our offer on community needs we expect a larger adoption of James project servers.

Such a split will furthermore ease documentation efforts.

The split between basic and advanced server will furthermore ease bug reports as the customization capabilities of the basic server are limited.
