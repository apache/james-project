# 64. Account Delegation

Date: 2022-11-15

## Status

Accepted (lazy consensus).

Not yet implemented.

## Context

The account delegation is a feature that gives another permission to access your account and vice versa.
The user has been delegated can begin accessing that account from your own account,
then read, send, respond to, and delete email messages... on their behalf.
This feature will be helpful for teamwork, family, collaboration, or company department... 

The account delegation will help James to have more benefits:

- Shared the mailbox resources
- Multi-users can use a shared account
- Can give different limited access to each user

The popular email provider also provide same feature, eg: [Google](https://support.google.com/mail/answer/138350?hl=en)

## Decision

Based on DelegationStore API, provide the jmap interface (jmap rfc-8621) for the user.

- Provide JMAP methods:
  - Delegate/get: list accountIds can the user access
  - Delegate/set (create/delete): 
    + Delegate my account to other people (please note that only the owner of the account should be able to interact with the delegation settings)
    + Revoke delegation of my account on another person's account (revoke a right given to me)
    + Revoke delegation of another people's account on my account (revoke a right given to others)

- Delegation on JMAP endpoints (API, eventsource, websocket, download, uploads, etc.. all of them). Use of accountIds of delegated accounts

## Consequences

## Alternatives

- If you only want to share a mailbox, you can set rights in the `Mailbox/set` method

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-xxx)
- [JMAP Sharing](https://datatracker.ietf.org/doc/draft-ietf-jmap-sharing/)