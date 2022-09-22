# 58. Upgrade to netty 4

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

James 3.7.0 uses Netty 3 for its TCP stacks (IMAP, SMTP, LMTP, MANAGESIEVE, POP3).

Latests Netty 3 minor releases dates from 20XX. This brings concerns regarding security
(unmaintained dependency).

Netty 4 delivers many enhancements compared to Netty 3. 

 - We can mention here the buffer APIs
 - Netty 4 empowers the use of native SSL, native event loops
 

## Decision

Upgrade James to Netty 4.

## Consequences

Significant rewrite of the protocol stack. Though the count of lines of code involved is low, the code is
tricky and would need several iteration to be stabilized. For this reason, James 3.7.0 was released prior
the Netty 4 adoption.
 
Associated risk:
 - Buffer leaks. Manual buffer management can be error prone. Netty project includes relevant 
 tooling.
 - We encountered issues with write ordering (when execute on, or outside of, the event loop)

Benefits: Netty 4 natively supports graceful shutdown, which enables easily implementing such a feature in 
James.

Netty 4 upgrades was also the opportunity to significantly improve test coverage for IMAP protocol stacks
and IMAP 4 extensions, which allowed fixing numerous bugs. To be mentioned:
 - COMPRESSION
 - STARTTLS and SSL
 - IMAP IDLE
 - QRESYNC and CONDSTORE
 
To be noted that Netty version used by James needs to be aligned with those of our dependencies:
 - S3 driver
 - Cassandra 4 driver
 - Lettuce driver (Redis for rate limiting)
 - Reactor HTTP
 
Netty upgrades thus needs to be carefully done. So far we successfully aligned versions, so the overall
compatibility looks good.

## Follow up work

We did not yet succeed to enable native epoll, native SSL.

## References

- [JIRA JAMES-3715](https://issues.apache.org/jira/browse/JAMES-3715)
- [Netty](https://netty.io)
- [Netty 4 migration](https://netty.io/wiki/new-and-noteworthy-in-4.0.html)
