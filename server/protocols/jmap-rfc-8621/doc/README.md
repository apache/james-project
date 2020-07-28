Annotated JMAP documentation
============================

This directory contains annotated JMAP documentation as found on https://jmap.io/.

Officially finalized specifications so far regarding JMAP are:

* [The core protocol](https://jmap.io/spec-core.html) [[RFC 8620](https://tools.ietf.org/html/rfc8620)]
* [JMAP Mail](https://jmap.io/spec-mail.html) [[RFC 8621](https://tools.ietf.org/html/rfc8621)]

Annotations aim at tracking implementation progress in James project.

Annotations are usually represented in the documentation by an `aside` tag. That tag can have two classes:

* `notice` More like an informative annotation, usually used to mark that a feature has been implemented
* `warning` To get the developer's attention, usually to say that maybe a point or detail of an implemented feature has 
not been implemented, or partially