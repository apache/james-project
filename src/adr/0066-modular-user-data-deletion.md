# 66. Deleting user data

Date: 2023-05-11

## Status

Accepted (lazy consensus).

Implemented.

## Context

Regulation like European GDPR involves being able to delete all user data upon requests. Currently there exist some APIs
for deleting some data relative for the users but the overall process is complex, requires a good knowlege of James data structures.

The data is scattered across the database and some sensible items might not be deletable.

## Decision

Define a single endpoint to delete all data relative to a user.

James being modular, we decided to form it under a webadmin task with a modular design.

Each feature storing user data would then implement `UserDeletionTaskStep`.

This modular design for migration steps could help developers easier to manage/test each step, and help other tailor 
James servers can implement their own steps as well.

Today, implemented deletion steps are:

- `RecipientRewriteTableUserDeletionTaskStep`: deletes all rewriting rules related to this user.
- `FilterUserDeletionTaskStep`: deletes all filters belonging to the user.
- `DelegationUserDeletionTaskStep`: deletes all delegations from / to the user.
- `MailboxUserDeletionTaskStep`: deletes mailboxes of this user, all ACLs of this user, as well as his subscriptions.
- `WebPushUserDeletionTaskStep`: deletes push data registered for this user.
- `IdentityUserDeletionTaskStep`: deletes identities registered for this user.
- `VacationUserDeletionTaskStep`: deletes vacations registered for this user.


We introduce `fromStep` query parameter that allows skipping previous steps, allowing the operator to resume the user deletion from a failed step.
This option could ease operators in case the data migration fails in the middle.

## Consequences

- Makes it easier to claim GDPR compliance on top of James.
- Modular design for deleting users is extension friendly for tailor James servers.

## Alternatives

Only deleting the user from the UsersRepository only "disable" the user without affecting the underlying data.

We considered this to be desirable and thus decided not to link user data suppression to the actual user suppression.