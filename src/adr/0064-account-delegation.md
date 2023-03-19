# 64. Account Delegation

Date: 2022-11-15

## Status

Accepted (lazy consensus).

Implemented.

## Context

The account delegation is a feature that gives another permission to access your account.
The user that had been granted access begin accessing that account then read, send, respond to, and delete email messages... on your behalf.
This feature is useful for VIP (who have secretaries), admins, etc...
James currently supports account delegation in IMAP / SMTP through SASL OIDC as well as SASL AUTH PLAIN (https://github.com/apache/james-project/blob/master/src/adr/0061-delegation.md)

Current now James support `urn:apache:james:params:jmap:mail:shares extension`, but it only affects the mailbox scope.

## Decision

Based on DelegationStore API, provide a James specific JMAP extension for managing delegation.

- Provide JMAP methods:
  - Delegate/get: list email addresses can the user access
  - Delegate/set (create/delete): please note that only the owner of the account should be able to interact with the delegation settings
    + Delegate my account to other people
    + Revoke delegation of my account on another person's account (revoke a right given to me)
    + Revoke delegation of another people's account on my account (revoke a right given to others)
  - DelegatedAccount/get: the method for getting delegated accounts
  - DelegatedAccount/set-delete: the method for delete delegated account

JMAP endpoints should support being called with accountIds of delegated accounts and needs to proceed authorization logic according to delegations.

## Consequences
The account delegation will help owner and delegated account:

- Multi-users can use a shared account
- Can give different limited access to each user
- Bring some commonly expected collaborative features
- We would need a way to "list accounts delegated to me" in the delegation store. We could use Cassandra LOGGED batch to keep this eventually consistent
- The mailboxSession needs to cary over information regarding logged-in user to allow restricting access to the delegation JMAP methods to only the account owner.

## References

- [JIRA](https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3756)
- [JMAP Sharing](https://datatracker.ietf.org/doc/draft-ietf-jmap-sharing/)
- [James Delegation](https://github.com/apache/james-project/blob/master/src/adr/0061-delegation.md)