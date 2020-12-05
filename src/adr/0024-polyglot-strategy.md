# 24. Polyglot codebase

Date: 2020-03-17

## Status

Proposed

We rely on scala in parts of the code base.

## Context & Problem Statement

James is written in Java for a very long time. In recent years, Java modernized a lot after a decade of slow progress.

However, in the meantime, most software relying on the JVM started supporting alternative JVM languages to keep being relevant.

It includes Groovy, Clojure, Scala and more recently Kotlin, to name a few.

Not being open to those alternative languages can be a problem for James adoption.

## Decision drivers

Nowadays, libraries and framework targeting the JVM are expected to support usage of one or several of these alternative languages.

James being not only a mail server but also a development framework needs to reach those expectations.

At the same time, more and more developers and languages adopt Function Programming (FP) idioms to solve their problems.

## Considered options

### Strategies

1. Let the users figure out how to make polyglot setups
2. Document the usage of polyglot mailets for some popular languages
3. Document the usage of polyglot components for some popular languages
4. Actually implement some mailets in some popular languages
5. Actually implement some components in some popular languages

### Languages:

[upperroman]
I. Clojure
II. Groovy
III. Kotlin
IV. Scala

## Decision

We decide for options 4, 5 and IV.

That means we need to write some mailets in Scala and demonstrate how it's done and then used in a running server.

It also means writing and/or refactoring some server components in Scala, starting where it's the most relevant.

### Positive Consequences 

* Modernize parts of James code
* Leverage Scala richer FP ecosystem and language to overcome Java limitations on that topic
* Should attract people that would not like Java

### Negative Consequences 

* Adds even more knowledge requirements to contribute to James
* Scala build time is longer than Java build time

## Pros and Cons of the Options

### Option 1: Let the users figure out how to make polyglot setups

Pros:
* We don't have anything new to do

Cons:
* It's boring, we like new challenges
* Java is declining despite language modernization, it means in the long term, less and less people will contribute to James

### Option 2: Document the usage of polyglot mailets for some popular languages

Pros:
* It's not a lot of work and yet it opens James to alternatives and can attract people outside Java developers community

Cons:
* Documentation without implementation often gets outdated when things move forward
* We don't really gain knowledge on the polyglot matters as a community and won't be able to help users much

### Option 3: Document the usage of polyglot components for some popular languages

Pros:
* It opens James to alternatives and can attract people outside Java developers community

Cons:
* Documentation without implementation often gets outdated when things move forward
* For such complex subject it's probably harder to do than actually implementing a component in another language
* We don't really gain knowledge on the polyglot matters as a community and won't be able to help users much

### Option 4: Actually implement some mailets in some popular languages

Pros:
* It's probably not a lot of work, a mailet is just a simple class, probably easy to do in most JVM language
* It makes us learn how it works and maybe will help us go further than the basic polyglot experience by doing small
enhancements to the codebase
* We can document the process and illustrate with some actual code
* It opens James to alternatives and can attract people outside Java developers community

Cons:
* It can have a negative impact on the build time and dependencies download

### Option 5: Actually implement some components in some popular languages

Pros:
* Leverage a modern language for some complex components
* It makes us learn how it works and maybe will help us go further than the basic polyglot experience by doing small
enhancements to the codebase
* We can document the process and illustrate with some actual code
* It opens James to alternatives and can attract people outside Java developers community

Cons:
* It makes the codebase more complex, requiring knowledge in another language
* It can have a negative impact on the build time and dependencies download

### Option I: Clojure

Pros:
* Functional Language

Cons:
* Weak popularity
* No prior experience among current active commiters
* Not statically typed hence less likely to fit the size of the project

### Option II: Groovy

Pros:
* More advanced than current Java on most topics

Cons:
* No prior experience among current active commiters
* Not very FP
* Replaced in JVM community by Kotlin last years

### Option III. Kotlin

Pros:
* Great Intellij support
* Most of the good parts of Scala
* FP-ish with Arrow
* Coroutine for handling high-performance IOs

Cons:
* No prior experience among current active commiters
* Lack of some FP constructs like proper Pattern Matching, persistent collections 
* Despite progress done by Arrow, Kotlin community aims mostly at writing "better Java"

#### Option IV. Scala

Pros:
* Rich FP community and ecosystem
* Existing knowledge among current active commiters

Cons:
* Needs work to master
* Can be slow to build
* 3.0 will probably require code changes

## References

* [Mailing list thread](https://www.mail-archive.com/server-dev@james.apache.org/msg66100.html)
