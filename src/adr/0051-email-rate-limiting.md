# Email rate limiting

Date: 2021-01-10

## Status

// todo

## Context

Rate limiting is one of the most popular features of any service. They limit how many emails you can send/receive from/to
each email account over a given period of time.  
We believe the rate-limiting will help James has more control of the resource and easy to dynamic config the user policy.

## Decision

Provide 2 criteria to limit to each duration:

- Number of emails
- Total size of emails

This can be done via mailets (`PerSenderRateLimit` and `PerRecipientRateLimit`). Depending on the position in the pipeline this could allow rating limit all emails in
transit, relayed emails or locally delivered emails.    
The rate limit will be config in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/mailetcontainer.xml).

Provide the interface `RateLimiter`, that will evaluate the current rate limit and return result acceptable or exceeded.
The default implement of RateLimiter will be using [Redis](https://redis.io) to store the current rate of each email (Redis is an in-memory data structure store, used
as a distributed).

We will document such a setup, and provide a mailetcontainer sample file.

## Consequences

- When having change the rate limit config, We need to restart James.
- The rate limit will not care with which protocol like IMAP, POP3, SMTP, JMAP.
- It is more than acceptable to lose all redis data, which is equivalent to resting the rate limiting.

## References
- [JIRA](https://issues.apache.org/jira/browse/JAMES-XXX)
- [Redis driver](https://github.com/lettuce-io/lettuce-core#reactive-api) 