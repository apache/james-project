# 53. Email rate limiting

Date: 2021-01-10

## Status

Accepted (lazy consensus).

Implemented.

## Context

Rate limiting is one of the common features expected from an email system. Examples: SaaS is
one https://www.fastmail.help/hc/en-us/articles/1500000277382-Account-limits#sending/receiving
, https://support.google.com/mail/answer/22839

They limit how many emails users can send/receive from/to each email account over a given period of time.  
We believe the rate-limiting will help James to have more benefits:

- Control of the resources
- Easy to configure dynamically the user policy.
- Complements the storage quota
- Can be a security requirement for SaaS deployments.
- Minimise impacts of Open-relay types of compression.
- Limiting the amount of emails sent to third parties can also prevent them from considering you as an open relay and can
  be beneficial as well.

## Decision

Set up a new maven project dedicated to rating limiting. This allows the rate limiting mailets to be embedded in a James
server as a soft dependency using the external-jar loading mechanism. Please note that this will take the form of an
extension jar, that could be dropped in one's James installation, and thus is optional, and not a runtime dependency.

Rate limiting will be enabled per sender, per receiver and globally. For each we will provide options for email size and
email count.

- This can be done via mailets:
    - PerSenderRateLimit is per sender
    - PerRecipientRateLimit is per recipient
    - GlobalRateLimit is for everyone. Depending on the position in the pipeline this could allow rate limiting all emails in
      transit, relayed emails or locally delivered emails.    
      The rate limit will be configured
      in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/mailetcontainer.xml).

- Those mailets will be based on a generic RateLimiter interface. We will propose two implementations for it:
    - In memory implementation: suitable for single instance deployments.
    - [Redis](https://redis.io) based implementation: suitable for distributed deployments.

We will base on [RateLimitJ](https://github.com/mokies/ratelimitj) to provide the implementation.
It is a Java library for rate limiting, assembled using extensible storage (Redis) and application framework adaptors. 
Its library's interfaces support thread-safe sync, async, and reactive usage patterns which is suitable for our reactive pipeline.

The implementation chosen will be configurable as part of mailet configuration. One would be able to configure the
implementation he wishes to use.

- We will document such a setup, and provide a mailetcontainer sample file.

## Consequences

- When having a change in the rate limit configuration, we need to restart James.
- Only protocols allowing to submit emails make sense here so SMTP and JMAP.
- It is more than acceptable to lose all redis data, which is equivalent to resetting the rate limiting.

## Alternatives

Alternatives implementation of the rate limiter can be proposed, and used within the aforementioned mailet.

For instance one could rely on Cassandra counters and Cassandra time series (thus not needing additional dependencies) however we fear the potential performance impact doing so.  Streaming based options, that aggregate in memory counters, might be a viable option too.

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3693)
- [Redis driver](https://github.com/lettuce-io/lettuce-core#reactive-api)
- [RateLimitJ](https://github.com/mokies/ratelimitj)