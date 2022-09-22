# 63. Prevent temporary file leaks

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

In order to reduce memory allocation, Apache James buffers big emails into temporary files, in both SMTP
and during the email processing.

This means that all James entities relying on `Mail` object needs to have resource management in place
to prevent temporary file leaks. If not this can result in both unreasonable disk occupation and file
descriptor leaks.

Core components were found to badly manage those resources. Some mailets (bounce), mail queue APIs, mail 
repository APIs were found to be causing temporary file leaks.

James allows users to define custom mailets, that them too can badly manage emails. If insiders tend to
commit such errors, then we should expect our users to commit them too.

This points toward the need of a systematic approach to detect and mitigate temporary file leaks.

Similar leak detection is performed by other libraries. One can mention here Netty buffer libraries,
which relies on phantom references to detect leaks. Phantom references allows the Java garbage collector
to report recently GCed object. Upon phantom reference allocation, a check can then be done to check recently 
GCed object, and release related resources if need be.

## Decision

Implement a leak detector "a la Netty" using phantom references.

Allow several modes:
 - Off: does nothing which is James 3.6.x behaviour.
 - Simple: logs leaks and deletes associated files
 - Advanced: Same as simple but with the allocation stack trace which comes at a performance cost.
 Useful for diagnostic.
 - Testing: Assertion errors upon leaks. Can be used to streangthen test suite and detect leaks as part 
 of a build.

## Consequences

Makes James core components "leak proofed".

Allows user to safely write extensions involving emails (eg duplication / sending).

Performance impact of turning on "simple" leak detection (default behaviour) is expected not 
to be significant.

## Alternatives

Similar functionalities can be implemented in a simpler way by relying on Java finalize method, called 
upon garbage collection.

Yet, such a move should not be done as many operational problems come through the use of 'finalize':
 - Longer GC pauses as finalise runs
 - GC is significantly slower when finalize method exists
 - During finalizing stage objects are still live which ease GC promotion to old generation and can result
 in so called 'kill the world GCs'.

## Follow up work

Mail management in James test suite is poor which leads to many false positive and prevents
us from leveraging leaks detector benefits as part of our test suite. Significant work would
be needed to do so.

## References

- [JIRA JAMES-3724](https://issues.apache.org/jira/browse/JAMES-3724)
- [Leak detection in Netty](https://netty.io/wiki/reference-counted-objects.html)
- [Phantom references](https://www.baeldung.com/java-phantom-reference)
