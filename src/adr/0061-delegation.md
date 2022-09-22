# 61. Delegation

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

Delegation is a common feature for email servers:

```
As user A I want to access mailbox of user B.
```

James currently supports a similar feature called impersonation:

```
As an administrator I want to acces mailbox of user B.
```

Impersonation can for instance be used to perform migrations with tools like IMAP-Sync.

## Decision

Implement delegation in Apache James (opt in).

Reuse APIs used for impersonation to also back delegation up. Technically if user B delegates his
account to user A then user A can impersonate user B.

Stored delegated access in a Cassandra database and expose it through webadmin.

Support delegation while logging in IMAP/SMTP. Both LOGIN/PLAIN authentication and OIDC
authentication are supported.

## Consequences

Logging traces belongs to the target user and not the user that really authenticated
though an intermediate log upon logging should allow a correlation.

Associated risk:
 - Special care needs to be paid to delegation as it can fall into the `improper authorization` 
 attack class and might result in data leaks / modification / deletion. However, as delegation 
 is performed upon logging the attack surface is limited, and typically simpler than traditional
 right management systems.

## Follow-up work

JMAP integration. We can expose delegated accounts through the JMAP session object and support
using non-default JMAP accounts.

This might come at the price of one Cassandra read per JMAP request when interacting with delegated 
accounts.

## References

- [JIRA JAMES-3756](https://issues.apache.org/jira/browse/JAMES-3756)
- [IMAP Sync](https://imapsync.lamiral.info/)
