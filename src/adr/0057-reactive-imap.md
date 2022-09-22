# 57. Reactive IMAP

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

Classic java programming involves doing some blocking calls. When performing some input-output, the
thread performing the tasks at hand will hang until a response is received. This means that a server 
performing IOs, which is the case of James, would end up with a high number of threads: for an IMAP
server, one IMAP request in flight equals one thread.

The widely documented issue with such an approach is that, in Java threads correspond to system threads,
and are expensive. Typically, each thread takes up to ~1MB of memory. To cap the count of threads at 
hand, the Thread Pool model is used to cap the count of threads. Process at most N IMAP requests with
N threads, and queue requests that cannot yet be handled.

The blocking thread pool model was the concurrency paradigm used by James IMAP implementation in 3.7.x.

Reactive programming instead does not perform blocking operation, thus one thread can handle several
IO tasks. This can be seen as callbacks executed once the request is received. Reactive programming model,
amongst others, leads to efficient resource usage. It helps keeping the count of applicative threads 
low, helps reduce context switches.

James have been, among the past few years, transitioning to a reactive programming model using the
Reactor library. Migrated components involves:
 - Cassandra/Distributed storage
 - JMAP protocol stacks
 - Some (webadmin) tasks also relies on Reactor

It is to be noted that our IMAP network stack uses Netty 4.x library that allows handling requests
asynchronously.

## Decision

Migrate IMAP to a reactive model.

Implement a way to limit IMAP concurrency to protect James server from bursts.

## Consequences

Significant rewrite of the IMAP stack.

Going reactive on the IMAP stacks yields the following improvements:
 - Reduced thread count. IMAP request handling can now be dealt with from the Netty IO threads.
 - Improve latency/performance. Removing the need of an extra scheduling and of synchronisation
 leads to improved tail latencies and better throughput.
 
Diagnostic tools will be harder to use on top of the IMAP stack:
 - Stack traces will be more complex to read
 - Glowroot APM would not work anymore as asynchronous logic are notoriously badly handled
 - Flame graphs will be messier.
 
Associated risk:
 - Badly managed blocking calls would now directly impact the Netty event loop thus strongly degrading the performance
 of the server. Tools like blockhound can help to mitigate this risk.

## Alternatives

From Java 19 onward, similar concerns are addressed by project Loom. This project delivers "Virtual Threads" scheduled
by the Java Virtual Machine on top of carrier system threads. This approach allow preserving the imperative programing
style. Tooling also works better.

Yet, this promissing feature:

 - Would only be available for use in Java 21 (LTS), not to be delivered prior to September 2023. Upgrading the James
 ecosystem to Java 21 Java Development Kit would be a controversial decision in itself and might raise significant debates
 within the community.
 - Operational feedback would likely be required prior adoption.
 - Do not have yet performance comparison with asynchronous libraries

## Follow up work

Some other components have not yet been migrated to a reactive style. This includes:

 - Mail processing within the mailet container
 - Remote Delivery (outgoing SMTP)
 - SMTP server (incoming SMTP / LMTP)
 - WebAdmin
 
With lower throughput, the benefits of migrating such components to a reactive style are lower thus might not match the
associated rewrite costs and operational risk. As such, the effort had not yet been undertaken, but might be in the future.

It is to be mentioned that APPEND command is buffered to a file, which, when happening is done on a dedicated thread. The
associated code is complex as Netty 4 ByteToMessageDecoder is meant to be synchronous. Upcoming Netty 5 would enable us
to simplify associated code.

## References

- [JIRA JAMES-3737](https://issues.apache.org/jira/browse/JAMES-3737)
- [JIRA JAMES-3816](https://issues.apache.org/jira/browse/JAMES-3816)
- [Project reactor](https://projectreactor.io/)
- [Project loom](https://wiki.openjdk.org/display/loom/Main)
- [Netty](https://netty.io/)
- [Blockhound](https://github.com/reactor/BlockHound)
- [Discussion on the mailing list](https://www.mail-archive.com/server-dev@james.apache.org/msg72113.html)