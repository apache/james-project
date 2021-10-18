# 42. James CLI based on webadmin API

Date: 2020-10-05

## Status

Accepted (lazy consensus).

Partially implemented.

## Context

James servers offer a command-line interface in order to interact with the server. However, it relies on the JMX protocol, which is known to be insecure. The JMX server embedded in Apache James, also used by the command line client is exposed to a java de-serialization issue according to [NVD-CVE-2017-12628 Detail](https://nvd.nist.gov/vuln/detail/CVE-2017-12628), and thus can be used to execute arbitrary commands. 

Besides, the current CLI interface is also not optimal for users. It places actions in front of entities with contiguous syntax, making it harder for the user to remember the command (for example, which entity the GET action command can interact with). If we design to place the entity first and the outgoing actions can interact with that entity afterward, the user will easily imagine what he/she can do with each entity. This creates an intuitive interface that is easier to remember.

Webadmin APIs use HTTP protocol, which is more secure than JMX protocol to interact with James servers.

Webadmin command-line interface is an upcoming replacement for the outdated, security-vulnerable JMX command-line interface. 

## Decision

We decided to write a new CLI client, running on top of the JVM, communicating with James via the webadmin protocol, using http.

* What libraries will we use? 

  * http client: ***Feign library***. We used it as an http client in other parts of James so we continue to use it.

  * CLI: ***Picocli library***. Picocli is a one-file command line parsing framework written in Java that allows us to create command line applications with almost no code. It allows mixing Options with positional Parameters (Eg: no need to the follow order Options then Parameters), [automatic type conversion](https://picocli.info/#_strongly_typed_everything) of command line arguments to the type of the annotated field, provide Automatic Help and better Subcommand Support, easily handle Exceptions.

* How will we limit breaking changes this new CLI will cause?

  * Work on a wrapper to adapt the old CLI API.

* Where will we locate this cli code?

  * server/protocols/webadmin-cli

* Write a man page.

  * Picocli generates beautiful documentation for our CLI (HTML, PDF and Unix man pages).

* We decided to adopt a more modern, modular CLI syntax:

```   
$ ./james-cli [OPTION] ENTITY ACTION {ARGUMENT}
```
where

    OPTION: optional parameter when running the command line,
  
    ENTITY: represents the entity to perform action on,
  
    ACTION: name of the action to perform,
  
    ARGUMENT: arguments needed for the action.

#### Examples

Add a domain to the domain list.
```
$ ./james-cli --url http://127.0.0.1:9999 domain create domainNameToBeCreated
```

In above command-line 

    OPTION: --url http://127.0.0.1:9999
  
    ENTITY: domain
  
    ACTION: create
  
    ARGUMENT: domainNameToBeCreated


## Consequences

It aims at providing a more modern and more secure CLI, also bringing compatibility ability with old CLI.

## References
* [NVD-CVE-2017-12628 Detail](https://nvd.nist.gov/vuln/detail/CVE-2017-12628)
* [Picocli 2.0: Do More With Less](https://dzone.com/articles/whats-new-in-picocli-20)
* [Picocli Homepage](https://picocli.info/)
* [Native Image Maven Plugin](https://www.graalvm.org/reference-manual/native-image/NativeImageMavenPlugin/)

* [JIRA](https://issues.apache.org/jira/browse/JAMES-3400)
* [PR discussing this ADR](https://github.com/apache/james-project/pull/251)