#46. Generalize EventBus

Date: 2020-06-11

## Status

Implemented, used for JMAP notifications, however not yet used on top of the user entity as 
described in this document.

## Context

User email storage usage is limited both in size and count via quotas (IMAP RFC-2087). In order to ease administrating large user bases, the quota search extension allows administrator
to retrieve all users whose email usages are exceeding a given occupation ratio.

When searching for users by quota ratio if we set the value of the parameters to 0, for example: `/quotas/users?minOccupationRatio=0&maxOccupationRatio=0`, the search feature is supposed to return newly created users
who have not received any email yet at that point. However, this is not the case because the quotas are currently being initialized only after
a user has received the first email.

We need to initialize user quotas upon user creation time. The problem is: there is currently no event at user creation 
and since the quota-search feature is a plugin of James, it cannot be hardwired into the domain logic of user management to initialize the quota for a just created user.

## Decision

For quota-search to be initialized/removed for a given user while keeping this feature as a plugin, we decided to adopt the Event Driven pattern we already use in Mailbox-api. 
We can create new events related to user management (UserCreated, UserRemoved and so on).

To achieve that, we will extract the EventBus out of mailbox-api in order to make it a utility component (eventbus-api), then we will make both mailbox-api and data-api depend on that new module. 

## Consequences

Mailbox-api would leverage the EventBus to keep exposing the mailbox-listener-api without changes on top of the generified EventBus. We need to define a common Event interface in eventbus-api, 
then each EventBus usage will define its own sealed event hierarchy implementing Event.

DeadLetter storage needs to be reworked in order to store events of various EventBus separately (which is needed for knowing which EventBus the event should be reprocessed on 
and knowing which sealed hierarchy an event belongs to.)

As a consequence, we will need a Cassandra data migration to add the EventBus name as part of the EventDeadLetter primary key. 

We could rely on the EventBus reliability for building any feature in James.