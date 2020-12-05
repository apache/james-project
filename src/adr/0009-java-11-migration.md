# 9. Migration to Java Runtime Environment 11

Date: 2019-10-24

## Status

Accepted & implemented

## Context

Java 11 is the only "Long Term Support" java release right now so more and more people will use it exclusively.

James is known to build with Java Compiler 11 for some weeks.

## Decision

We adopt Java Runtime Environment 11 for James as a runtime to benefits from a supported runtime and new features
of the languages and the platform.

## Consequences

* It requires the upgrade of Spring to 4.3.x.
* All docker images should be updated to adoptopenjdk 11.
* The documentation should be updated accordingly.

## References

* [Mailing list thread](https://www.mail-archive.com/server-dev@james.apache.org/msg62611.html)
* [Mailing list thread](https://www.mail-archive.com/server-dev@james.apache.org/msg61335.html)
* [PR discussing this ADR](https://github.com/apache/james-project/pull/171)