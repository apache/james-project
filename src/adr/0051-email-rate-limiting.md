# Email rate limiting

Date: 2021-01-10

## Status

// todo

## Context

Rate limiting is one of the common features. Examples: SaaS is one https://www.fastmail.help/hc/en-us/articles/1500000277382-Account-limits#sending/receiving, https://support.google.com/mail/answer/22839
They limit how many emails you can send/receive from/to each email account over a given period of time.  
We believe the rate-limiting will help James has more control of the resource and easy to dynamic config the user policy. It also complements the storage quota.

## Decision

- Set up a new maven project dedicated to rating limiting
- Provide 2 criteria to limit to each duration: number of emails and total size of emails
- This can be done via mailets:
  - PerSenderRateLimit is per sender
  - PerRecipientRateLimit is per recipient
  - GlobalRateLimit for everyone
Depending on the position in the pipeline this could allow rating limit all emails in transit, relayed emails or locally delivered emails.    
The rate limit will be config in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/mailetcontainer.xml).

- Provide the interface `RateLimiter`, that will evaluate the current rate limit and return result acceptable or exceeded.
- Create the implement InmemoryRateLimiter, which use guava-rate-limiter 
- Create the implement RedisRateLimiter, which will use [Redis](https://redis.io) to store the current rate of each email. Use https://lettuce.io for the non-blocking Redis driver.
- We will document such a setup, and provide a mailetcontainer sample file.

## Consequences

- When having change the rate limit config, We need to restart James.
- The rate limit will not care with which protocol like IMAP, POP3, SMTP, JMAP.
- It is more than acceptable to lose all redis data, which is equivalent to resting the rate limiting.

## References
- [JIRA](https://issues.apache.org/jira/browse/JAMES-3693)
- [Redis driver](https://github.com/lettuce-io/lettuce-core#reactive-api) 
- [Guava rate limiter](https://guava.dev/releases/19.0/api/docs/index.html?com/google/common/util/concurrent/RateLimiter.html)