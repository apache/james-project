# 65. Changing username

Date: 2023-03-17

## Status

Accepted (lazy consensus).

Implemented.

## Context
Changing username is a desired feature for an email server. It is a common practice when a user marries. For example:

Alice MARTINEZ have an email address `alice.martinez@domain.tld` and gets married to Bob ERNANDEZ. She thus wants to
change her email address to `alice.ernandez@domain.tld` while preserving her email data.

Nowadays, James uses username as an identifier for user data. Therefore, this feature is hard to implement because of changing
directly identifier to another value is difficult and could break data consistency if we do not do it carefully.

## Decision

Need to copy the data
Modular design

We decided to do changing username with two steps: first, create new username, then migrate the old user's data to the new account -
the way we expect that is less dangerous to current data consistency. The data in the old account need to be truncated after migrating to the new account.

For the data migration step, we decided to form it under a webadmin task with a modular design. A big data migration task contains
many small tasks that implement `UsernameChangeTaskStep` interface. This modular design for migration steps could help developers easier
to manage/test each step, and help other tailor James servers can implement their own steps as well.

Today, implemented migration steps are:

- `ForwardUsernameChangeTaskStep`: creates forward from the old user to the new user and migrates existing forwards
- `FilterUsernameChangeTaskStep`: migrates users filtering rules
- `DelegationUsernameChangeTaskStep`: migrates delegations where the impacted user is either delegatee or delegator
- `MailboxUsernameChangeTaskStep`: migrates mailboxes belonging to the old user to the account of the new user. It also
  migrates users' mailbox subscriptions.
- `ACLUsernameChangeTaskStep`: migrates ACLs on mailboxes the migrated user has access to and updates subscriptions accordingly.

We introduce `fromStep` query parameter that allows skipping previous steps, allowing the operator to resume the username change from a failed step.
This option could ease operators in case the data migration fails in the middle.

## Consequences
- Add one more useful feature to James rich feature set: Users can change their name while preserving their email data.
- Users would be aware that they have a new account through a new username/password. No implicit migration without user attention is archived today.
- JMAP Identity data is not migrated because they are tightly coupled to the old username, users should create identities again themselves.
- Some data considered not critical like Push subscription, Vacation response,... are not migrated today. Contributions for them are welcome.

## Alternatives
We could have another choice for implementing this topic: create an account identifier not linked directly to the username (e.g a hash value points to a username),
so changing the pointer value would be easier than changing the pointer itself. However, that approach has two great concerns:
- Huge refactoring/breaking change is required: nowadays username as an identifier is used everywhere in tables schema and code.
- Performance concern: 1 more request is required every time we want to resolve the username.