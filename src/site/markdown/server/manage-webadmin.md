Web administration for JAMES
============================

The web administration supports for now the CRUD operations on the domains, the users, their mailboxes and their quotas,
 managing mail repositories, performing cassandra migrations, and much more, as described in the following sections.

**WARNING**: This API allow authentication only via the use of JWT. If not configured with JWT, an administrator should ensure an attacker can not use this API.

Please also note **webadmin** is only enabled with **Guice**. You can not use it when using James with **Spring**, as the required injections are not implemented.

In case of any error, the system will return an error message which is json format like this:

```
{
    statusCode: <error_code>,
    type: <error_type>,
    message: <the_error_message>
    cause: <the_detail_message_from_throwable>
}
```

## Administrating domains

### Create a domain

```
curl -XPUT http://ip:port/domains/domainToBeCreated
```

Resource name domainToBeCreated:

 - can not be null or empty
 - can not contain '@'
 - can not be more than 255 characters
 - can not contain '/'

Response codes:

 - 204: The domain was successfully added
 - 400: The domain name is invalid
 - 500: Internal error while adding the domain

### Delete a domain

```
curl -XDELETE http://ip:port/domains/domainToBeDeleted
```

Response codes:

 - 204: The domain was successfully removed
 - 500: Internal error while removing the domain

### Test if a domain exists

```
curl -XGET http://ip:port/domains/domainName
```

Response codes:

 - 204: The domain exists
 - 404: The domain does not exist
 - 500: Internal error while accessing the domains

### Get the list of domains

```
curl -XGET http://ip:port/domains
```

Possible response:

```
{"domains":["domain1", "domain2"]}
```

Response codes:

 - 200: The domain list was successfully retrieved
 - 500: Internal error while accessing the domains

## Administrating users

### Create a user

```
curl -XPUT http://ip:port/users/usernameToBeUsed -d '{"password":"passwordToBeUsed"}'
```

Resource name usernameToBeUsed:

 - can not be null or empty
 - can not be more than 255 characters
 - can not contain '/'

Response codes:

 - 204: The user was successfully created
 - 400: The user name or the payload is invalid
 - 409: Conflict: A concurrent modification make that query to fail
 - 500: Internal error while adding the user

Note: if the user is already, its password will be updated.

### Updating a user password

Same than Create, but a user need to exist.

If the user do not exist, then it will be created.

### Deleting a user

```
curl -XDELETE http://ip:port/users/userToBeDeleted
```

Response codes:

 - 204: The user was successfully deleted
 - 500: Internal error while deleting the user

### Retrieving the user list

```
curl -XGET http://ip:port/users
```

The answer looks like:

```
[{"username":"username@domain-jmapauthentication.tld"},{"username":"username@domain.tld"}]
```

Response codes:

 - 200: The user name list was successfully retrieved
 - 500: Internal error while retrieving the users

## Administrating user mailboxes

### Creating a mailbox

```
curl -XPUT http://ip:port/users/usernameToBeUsed/mailboxes/mailboxNameToBeCreated
```

Resource name usernameToBeUsed should be an existing user
Resource name mailboxNameToBeCreated should not be empty, nor contain # & % * characters.

Response codes:

 - 204: The mailbox now exists on the server
 - 400: Invalid mailbox name
 - 404: The user name does not exist
 - 500: Internal error

 To create nested mailboxes, for instance a work mailbox inside the INBOX mailbox, people should use the . separator. The sample query is:

```
curl -XDELETE http://ip:port/users/usernameToBeUsed/mailboxes/INBOX.work
```

### Deleting a mailbox and its children

```
curl -XDELETE http://ip:port/users/usernameToBeUsed/mailboxes/mailboxNameToBeCreated
```

Resource name usernameToBeUsed should be an existing user
Resource name mailboxNameToBeCreated should not be empty

Response codes:

 - 204: The mailbox now does not exist on the server
 - 400: Invalid mailbox name
 - 404: The user name does not exist
 - 500: Internal error

### Testing existence of a mailbox

```
curl -XGET http://ip:port/users/usernameToBeUsed/mailboxes/mailboxNameToBeCreated
```

Resource name usernameToBeUsed should be an existing user
Resource name mailboxNameToBeCreated should not be empty

Response codes:

 - 204: The mailbox exists
 - 400: Invalid mailbox name
 - 404: The user name does not exist, the mailbox does not exist
 - 500: Internal error

### Listing user mailboxes

```
curl -XGET http://ip:port/users/usernameToBeUsed/mailboxes
```

The answer looks like:

```
[{"mailboxName":"INBOX"},{"mailboxName":"outbox"}]
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 200: The mailboxes list was successfully retrieved
 - 404: The user name does not exist
 - 500: Internal error

### Deleting user mailboxes

```
curl -XDELETE http://ip:port/users/usernameToBeUsed/mailboxes
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The user do not have mailboxes anymore
 - 404: The user name does not exist
 - 500: Internal error


## Administrating quotas by users

### Getting the quota for a user

```
curl -XGET http://ip:port/quota/users/usernameToBeUsed
```

Resource name usernameToBeUsed should be an existing user

The answer can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 404: The user does not exist
 - 500: Internal error while accessing the user's quota

### Updating the quota for a user

```
curl -XPUT http://ip:port/quota/users/usernameToBeUsed
```

Resource name usernameToBeUsed should be an existing user

The body can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer or not unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Getting the quota count for a user

```
curl -XGET http://ip:port/quota/users/usernameToBeUsed/count
```

Resource name usernameToBeUsed should be an existing user

The answer looks like:

```
52
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 404: The user does not exist
 - 500: Internal error while accessing the user's quota

### Updating the quota count for a user

```
curl -XPUT http://ip:port/quota/users/usernameToBeUsed/count
```

Resource name usernameToBeUsed should be an existing user

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer or not unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Deleting the quota count for a user

```
curl -XDELETE http://ip:port/quota/users/usernameToBeUsed/count
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer or not unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Getting the quota size for a user

```
curl -XGET http://ip:port/quota/users/usernameToBeUsed/size
```

Resource name usernameToBeUsed should be an existing user

The answer looks like:

```
52
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 404: The user does not exist
 - 500: Internal error while accessing the user's quota

### Updating the quota size for a user

```
curl -XPUT http://ip:port/quota/users/usernameToBeUsed/size
```

Resource name usernameToBeUsed should be an existing user

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer or not unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Deleting the quota size for a user

```
curl -XDELETE http://ip:port/quota/users/usernameToBeUsed/size
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer or not unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

 
## Cassandra Schema upgrades

Cassandra upgrades implies the creation of a new table. Thus restarting James is needed, as new tables are created on restart.

Once done, we ship code that tries to read from new tables, and if not possible backs up to old tables. You can thus safely run
without running additional migrations.

On the fly migration can be enabled. However, one might want to force the migration in a controlled fashion, and update
automatically current schema version used (assess in the database old versions is no more used, as the corresponding tables are empty).
Note that this process is safe: we ensure the service is not running concurrently on this James instance, that it does not bump
version upon partial failures, that race condition in version upgrades will be idempotent, etc...

These schema updates can be triggered by webadmin using the Cassandra backend.

Note that currently the progress can be tracked by logs.

### Retrieving current Cassandra schema version

```
curl -XGET http://ip:port/cassandra/version
```

Will return:

```
2
```

Where the number corresponds to the current schema version of the database you are using.

Response codes:

 - 200: Success
 - 500: Internal error

### Retrieving latest available Cassandra schema version

```
curl -XGET http://ip:port/cassandra/version
```

Will return:

```
3
```

Where the number corresponds to the latest available schema version of the database you are using. This means you can be
migrating to this schema version.

Response codes:

 - 200: Success
 - 500: Internal error

### Upgrading to a specific version

```
curl -XPOST http://ip:port/cassandra/version/upgrade -d '3'
```


Will schedule the run of the migrations you need to reach schema version 3. The `taskId` will allow you to monitor and manage this process.

```
{"taskId":"3294a976-ce63-491e-bd52-1b6f465ed7a2"}
```

Response codes:

 - 200: Success. The scheduled task taskId is returned.
 - 400: The version is invalid. The version should be a strictly positive number.
 - 410: Error while planning this migration. This resource is gone away. Reason is mentionned in the body.
 - 500: Internal error while creating the migration task.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.


The scheduled task will have the following type `CassandraMigration` and the following `additionalInformation`:

```
{"toVersion":3}
```

### Upgrading to the latest version

```
curl -XPOST http://ip:port/cassandra/version/upgrade/latest
```

Will schedule the run of the migrations you need to reach the latest schema version. The `taskId` will allow you to monitor and manage this process.

```
{"taskId":"3294a976-ce63-491e-bd52-1b6f465ed7a2"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 200: Success. The scheduled task taskId is returned.
 - 410: Error while planning this migration. This resource is gone away. Reason is mentionned in the body.
 - 500: Internal error while creating the migration task.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.

The scheduled task will have the following type `CassandraMigration` and the following `additionalInformation`:

```
{"toVersion":2}
```

## Correcting ghost mailbox

This is a temporary workaround for the **Ghost mailbox** bug encountered using the Cassandra backend, as described in MAILBOX-322.

You can use the mailbox merging feature in order to merge the old "ghosted" mailbox with the new one.

```
curl -XPOST http://ip:port/cassandra/mailbox/merging -d '{"mergeOrigin":"id1", "mergeDestination":"id2"}'
```

Will scedule a task for :

 - Delete references to `id1` mailbox
 - Move it's messages into `id2` mailbox
 - Union the rights of both mailboxes

The response to that request will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Unable to parse the body.

The scheduled task will have the following type `mailboxMerging` and the following `additionalInformation`:

```
{
  "oldMailboxId":"5641376-02ed-47bd-bcc7-76ff6262d92a",
  "newMailboxId":"4555159-52ae-895f-ccb7-586a4412fb50",
  "totalMessageCount": 1,
  "messageMovedCount": 1,
  "messageFailedCount": 0
}
```

## Creating address group

You can use **webadmin** to define address groups.

When a specific email is sent to the group mail address, every group member will receive it.

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

### Listing groups

```
curl -XGET http://ip:port/address/groups
```

Will return the groups as a list of JSON Strings representing mail addresses. For instance:

```
["group1@domain.com", "group2@domain.com"]
```

Response codes:

 - 200: Success
 - 500: Internal error

### Listing members of a group

```
curl -XGET http://ip:port/address/groups/group@domain.com
```

Will return the group members as a list of JSON Strings representing mail addresses. For instance:

```
["member1@domain.com", "member2@domain.com"]
```

Response codes:

 - 200: Success
 - 400: Group structure is not valid
 - 404: The group does not exist
 - 500: Internal error

### Adding a group member

```
curl -XPUT http://ip:port/address/groups/group@domain.com/member@domain.com
```

Will add member@domain.com to group@domain.com, creating the group if needed

Response codes:

 - 200: Success
 - 400: Group structure or member is not valid
 - 403: Server does not own the requested domain
 - 409: Requested group address is already used for another purpose
 - 500: Internal error

### Removing a group member

```
curl -XDELETE http://ip:port/address/groups/group@domain.com/member@domain.com
```

Will remove member@domain.com from group@domain.com, removing the group if group is empty after deletion

Response codes:

 - 200: Success
 - 400: Group structure or member is not valid
 - 500: Internal error

## Administrating mail repositories

### Listing mail repositories

```
curl -XGET http://ip:port/mailRepositories
```

The answer looks like:

```
[
    {
        "repository": "file://var/mail/error/",
        "id": "file%3A%2F%2Fvar%2Fmail%2Ferror%2F"
    },
    {
        "repository": "file://var/mail/relay-denied/",
        "id": "file%3A%2F%2Fvar%2Fmail%2Frelay-denied%2F"
    },
    {
        "repository": "file://var/mail/spam/",
        "id": "file%3A%2F%2Fvar%2Fmail%2Fspam%2F"
    },
    {
        "repository": "file://var/mail/address-error/",
        "id": "file%3A%2F%2Fvar%2Fmail%2Faddress-error%2F"
    }
]
```

You can use `id`, the encoded URL of the repository, to access it in later requests.

Response codes:

 - 200: The list of mail repositories
 - 500: Internal error

### Getting additional information for a mail repository

```
curl -XGET http://ip:port/mailRepositories/encodedUrlOfTheRepository/
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Example:

```
curl -XGET http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/
```

The answer looks like:

```
{
   "repository": "file://var/mail/error/",
   "id": "file%3A%2F%2Fvar%2Fmail%2Ferror%2F",
   "size": 243
}
```

Response codes:

 - 200: Additonnal information for that repository
 - 404: This repository can not be found
 - 500: Internal error

### Listing mails contained in a mail repository

```
curl -XGET http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Example:

```
curl -XGET http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails
```

The answer will contains all mailKey contained in that repository.

```
[
    "mail-key-1",
    "mail-key-2",
    "mail-key-3"
]
```

Note that this can be used to read mail details.

You can pass additional URL parameters to this call in order to limit the output:
 - A limit: no more elements than the specified limit will be returned. This needs to be strictly positive. If no value is specified, no limit will be applied.
 - An offset: allow to skip elements. This needs to be positive. Default value is zero.

Example:

```
curl -XGET http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails?limit=100&offset=500
```

Response codes:

 - 200: The list of mail keys contained in that mail repository
 - 400: Invalid parameters
 - 404: This repository can not be found
 - 500: Internal error

### Reading a mail details

```
curl -XGET http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails/mailKey
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

```
curl -XGET http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails/mail-key-1
```

Response looks like:

```
{
    "name": "mail-key-1",
    "sender": "sender@domain.com",
    "recipients": ["recipient1@domain.com", "recipient2@domain.com"],
    "state": "address-error",
    "error": "A small message explaining what happened to that mail..."
}
```

Response codes:

 - 200: Details of the mail
 - 404: This repository or mail can not be found
 - 500: Internal error

### Removing a mail from a mail repository

```
curl -XDELETE http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails/mailKey
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

```
curl -XDELETE http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails/mail-key-1
```

Response codes:

 - 204: This mail no longer exists in this repository
 - 404: This repository can not be found
 - 500: Internal error

### Removing all mails from a mail repository


```
curl -XDELETE http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Example:

```
curl -XDELETE http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails
```

The response to that request will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 404: Could not find that mail repository
 - 500: Internal error

The scheduled task will have the following type `clearMailRepository` and the following `additionalInformation`:

```
{
  "repositoryUrl":"file://var/mail/error/",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing mails from a mail repository

Sometime, you want to re-process emails stored in a mail repository. For instance, you can make a configuration error, or there can be a James bug that makes processing of some mails fail. Those mail will be stored in a mail repository. Once you solved the problem, you can reprocess them.

To reprocess mails from a repository:

```
curl -XPATCH http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails?action=reprocess
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails?action=reprocess
```

Additional query paramaters are supported:
 - `queue` allow you to target the mail queue you want to enqueue the mails in.
 - `processor` allow you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.


For instance:

```
curl -XPATCH http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails?action=reprocess&processor=transport&queue=spool
```

Note that the `action` query parameter is compulsary and can only take value `reprocess`.


The response to that request will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 404: Could not find that mail repository
 - 500: Internal error

The scheduled task will have the following type `reprocessingAllTask` and the following `additionalInformation`:

```
{
  "repositoryUrl":"file://var/mail/error/",
  "targetQueue":"spool",
  "targetProcessor":"transport",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing a specific mail from a mail repository

To reprocess a specific mail from a mail repository:

```
curl -XPATCH http://ip:port/mailRepositories/encodedUrlOfTheRepository/mails/mailKey?action=reprocess
```

Resource name `encodedUrlOfTheRepository` should be the resource id of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails/name1?action=reprocess
```

Additional query paramaters are supported:
 - `queue` allow you to target the mail queue you want to enqueue the mails in.
 - `processor` allow you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.


For instance:

```
curl -XPATCH http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails/name1?action=reprocess&processor=transport&queue=spool
```

Note that the `action` query parameter is compulsary and can only take value `reprocess`.


The response to that request will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 404: Could not find that mail repository
 - 500: Internal error

The scheduled task will have the following type `reprocessingOneTask` and the following `additionalInformation`:

```
{
  "repositoryUrl":"file://var/mail/error/",
  "targetQueue":"spool",
  "targetProcessor":"transport",
  "mailKey":"name1"
}
```

## Administrating mail queues

### Listing mail queues

```
curl -XGET http://ip:port/mailQueues
```

The answer looks like:

```
["outgoing","spool"]
```

Response codes:

 - 200: The list of mail queuess
 - 500: Internal error

### Getting a mail queue details

```
curl -XGET http://ip:port/mailQueues/mailQueueName
```

Resource name mailQueueName is the name of a mail queue, this command will return the details of the given mail queue. For instance:

```
{"name":"outgoing","size":0}
```

Response codes:

 - 200: Success
 - 400: Mail queue is not valid
 - 404: The mail queue does not exist
 - 500: Internal error

### Listing the mails of a mail queue

```
curl -XGET http://ip:port/mailQueues/mailQueueName/mails
```

Additional URL query parameters:

 - `limit`: Maximum number of mails returned in a single call. Only strictly positive integer values are accepted. Example:
 
```
curl -XGET http://ip:port/mailQueues/mailQueueName/mails?limit=100
```

The answer looks like:

```
[{
  "name": "Mail1516976156284-8b3093b9-eebf-4c40-9c26-1450f4fcdc3c-to-test.com",
  "sender": "user@james.linagora.com",
  "recipients": ["someone@test.com"],
  "nextDelivery": "1969-12-31T23:59:59.999Z"
}]
```

Response codes:

 - 200: Success
 - 400: Mail queue is not valid or limit is invalid
 - 404: The mail queue does not exist
 - 500: Internal error

### Deleting mails from a mail queue

```
curl -XDELETE http://ip:port/mailQueues/mailQueueName/mails?sender=senderMailAddress
```

This request should have exactly one query parameter from the following list:
* sender: which is a mail address (i.e. sender@james.org)
* name: which is a string
* recipient: which is a mail address (i.e. recipient@james.org)

The mails from the given mail queue matching the query parameter will be deleted.


Response codes:

 - 204: Success (No content)
 - 400: Invalid request
 - 404: The mail queue does not exist
 - 500: Internal error

### Flushing mails from a mail queue

```
curl -XPATCH http://ip:port/mailQueues/mailQueueName?delayed=true \
  -d '{"delayed": false}'
```

This request should have the query parameter *delayed* set to *true*, in order to indicate only delayed mails are affected.
The payload should set the `delayed` field to false inorder to remove the delay. This is the only supported combination,
and it performs a flush.

The mails delayed in the given mail queue will be flushed.

Response codes:

 - 204: Success (No content)
 - 400: Invalid request
 - 404: The mail queue does not exist
 - 500: Internal error

## Task management

Some webadmin features schedules tasks. The task management API allow to monitor and manage the execution of the following tasks.

Note that the `taskId` used in the following APIs is returned by other WebAdmin APIs scheduling tasks.

### Getting a task details

```
curl -XGET http://ip:port/tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

An Execution Report will be returned:

```
{
    "submitDate": "2017-12-27T15:15:24.805+0700",
    "startedDate": "2017-12-27T15:15:24.809+0700",
    "completedDate": "2017-12-27T15:15:24.815+0700",
    "cancelledDate": null,
    "failedDate": null,
    "taskId": "3294a976-ce63-491e-bd52-1b6f465ed7a2",
    "additionalInformation": {},
    "status": "completed",
    "type": "typeOfTheTask"
}
```

Note that:

 - `status` can have the value:
    - `waiting`: The task is scheduled but its execution did not start yet
    - `inProgress`: The task is currently executed
    - `cancelled`: The task had been cancelled
    - `completed`: The task execution is finished, and this execution is a success
    - `failed`: The task execution is finished, and this execution is a failure

 - `additionalInformation` is a task specific object giving additional information and context about that task. The structure
   of this `additionalInformation` field is provided along the specific task submission endpoint.

Response codes:

 - 200: The specific task was found and the execution report exposed above is returned
 - 400: Invalid task ID
 - 404: Task ID was not found

### Awaiting a task

One can await the end of a task, then receive it's final execution report.

That feature is especially usefull for testing purpose but still can serve real-life scenari.

```
curl -XGET http://ip:port/tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2/await
```

An Execution Report will be returned.

Response codes:

 - 200: The specific task was found and the execution report exposed above is returned
 - 400: Invalid task ID
 - 404: Task ID was not found

### Cancelling a task

You can cancel a task by calling:

```
curl -XDELETE http://ip:port/tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:
 - 204: Task had been cancelled
 - 400: Invalid task ID

### Listing tasks

A list of all tasks can be retrieved:

```
curl -XGET /tasks
```

Will return a list of Execution reports

One can filter the above results by status. For example:

```
curl -XGET /tasks?status=inProgress
```

Will return a list of Execution reports that are currently in progress.

Response codes:

 - 200: A list of corresponding tasks is returned
 - 400: Invalid status value
