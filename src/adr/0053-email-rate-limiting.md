# 53. Email rate limiting

Date: 2021-01-10

## Status

Accepted (lazy consensus).

Not yet implemented.

## Context

Rate limiting is one of the common features expected from an email system. Examples: SaaS is
one https://www.fastmail.help/hc/en-us/articles/1500000277382-Account-limits#sending/receiving
, https://support.google.com/mail/answer/22839

They limit how many emails users can send/receive from/to each email account over a given period of time.  
We believe the rate-limiting will help James has more benefits:

- Control of the resource
- Easy to dynamic config the user policy.
- Complements the storage quota
- Can be a security requirement for SaaS deployments.
- Minimise impacts of Open-relay types of compression.
- Limiting the amount of email sent to third parties can also prevent them from considering you as an open relay and can
  be beneficial as well.

## Decision

Set up a new maven project dedicated to rating limiting. This allows the rate limiting mailets to be embedded in a James
server as a soft dependency using the external-jar loading mechanism. Please note that this will take the form of an
extension jars, that could be dropped in one's James installation, and thus is optional, and not of a runtime
dependency.

Rate limiting will be enabled per sender, per receiver and globally. For each we will provide options for email size and
email count.

- This can be done via mailets:
    - PerSenderRateLimit is per sender
    - PerRecipientRateLimit is per recipient
    - GlobalRateLimit for everyone Depending on the position in the pipeline this could allow rating limit all emails in
      transit, relayed emails or locally delivered emails.    
      The rate limit will be config
      in [mailetcontainer.xml](/server/apps/distributed-app/sample-configuration/mailetcontainer.xml).

- Those mailets will be based on a generic RateLimiter interface. We will propose two implementations for it:
    - In memory (guava based) suitable for single instance deployments
    - [Redis](https://redis.io) based, suitable for distributed deployments.

The implementation chosen will be configurable as part of mailet configuration. One would be able to configure the
implementation he wishes to use.

- We will document such a setup, and provide a mailetcontainer sample file.

## Consequences

- When having change the rate limit config, We need to restart James.
- Only protocols allowing to submit emails make sense here so SMTP and JMAP.
- It is more than acceptable to lose all redis data, which is equivalent to resetting the rate limiting.

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3693)
- [Redis driver](https://github.com/lettuce-io/lettuce-core#reactive-api)
- [Guava rate limiter](https://guava.dev/releases/19.0/api/docs/index.html?com/google/common/util/concurrent/RateLimiter.html)