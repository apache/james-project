# 45. Support JMAP Push with Mailbox/changes implementation

Date: 2020-12-08

## Status

Accepted (lazy consensus).

Implemented.

## Context

JMAP Push notifications allow clients to efficiently update (almost) instantly to stay in sync with data changes on the server. 

In order to support this, we need to handle the **state** property that comes with JMAP get/set request. This means that James needs to be able 
to record a new state for objects whenever a change happens as well as return the most recent state to the client when fetching objects. 

First step is to implement Mailbox/changes. 

## Decision

We will implement a mechanism to record all the changes happening to Mailbox objects in the form of a list of **mailboxId**. When an event such as  
created/updated/destroyed occur, or when message is appended to a mailbox we will store their **mailboxIds** along with a **state** object
in a Cassandra table.  

Each state will have a list of changes, and all the **mailboxId** will be stored as separated lists corresponding to the change which they represent: **created**, **updated**, **destroyed**.
For the case when messages are appended to a mailbox, it will be counted as an updated event and that mailboxId should be stored in **updated** list. 

Leveraging the **MailboxChanges** table, we can now fetch all the changes that have occurred since a particular **state**.

States are stored in Cassandra as time based UUID (**TimeUUID**). This ensures that no conflicting changes will happen in the case when two or more events occur at the same point in time.
**TimeUUID** also allows **state** to be sorted in chronological order.

Components that need to be implemented:

- MailboxChangesRepository: Allows storing and fetching the **state** along with the lists of **mailboxId** in **MailboxChanges** table.
- MailboxChangeListener: Listens to changes and triggers the record creation in **MailboxChanges** table.
- MailboxChangeMethod: Handles the **state** property, allowing client to fetch the changes since a particular state. 
- MailboxSetMethod/MailboxGetMethod needs to query the MailboxChangesRepository for their states properties.
 
## Example of a Mailbox/changes request/response

**Request**

```
[["Mailbox/changes", {
  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
  "sinceState": "dd3721a6-b3ee-4884-8762-fccc0c576438"
}, "t0"]]
```

**Response**

```
[["Mailbox/changes", {
  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
  "oldState": "dd3721a6-b3ee-4884-8762-fccc0c576438",
  "newState": "2433b670-3554-4f55-bab1-147848e89e5d",
  "hasMoreChanges": false,
  "created": [ 
    "1", 
    "2" 
  ],
 "updated": [],
 "destroyed": []
}, "t0" ]]
```

## Consequences

- Due to the limitation of the event listening mechanism of the listeners, we can only store one change (one **mailboxId**) for each state instead of many.  
However, by keeping the data type of changes as separated lists, we will be more opened for future improvements.        
- Changes can only be fetched in a linear fashion from oldest to newest, as opposed to how it should prioritize newer changes first according to the spec.

## Cassandra table structure

Only one table is required:

```
TABLE mailbox_changes
PRIMARY KEY accountId
CLUSTERING COLUMN state
COLUMN created
COLUMN updated
COLUMN destroyed
COLUMN isCountChange
ORDERED BY state
```

## References

- [Support JMAP HTTP PUSH](https://issues.apache.org/jira/browse/JAMES-3457)
- [Implement a MailboxChangeRepository](https://issues.apache.org/jira/browse/JAMES-3459)
- [Implement a JMAP MailboxChangeListener](https://issues.apache.org/jira/browse/JAMES-3460)
- [Implement Mailbox/changes method and related contract tests](https://issues.apache.org/jira/browse/JAMES-3461)
- [Implement CassandraMailboxChangeRepository](https://issues.apache.org/jira/browse/JAMES-3462)
- [Mailbox/get should handle state property](https://issues.apache.org/jira/browse/JAMES-3463)
- [Mailbox/set should handle oldState & newState](https://issues.apache.org/jira/browse/JAMES-3464)
- [Mailbox/changes updatedProperties handling](https://issues.apache.org/jira/browse/JAMES-3465)
- [ADR for Mailbox/changes](https://github.com/apache/james-project/pull/276)

