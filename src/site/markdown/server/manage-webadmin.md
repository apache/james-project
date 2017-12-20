Web administration for JAMES
============================

The web administration supports for now the CRUD operations on the domains,the users, their mailboxes and their quotas, as described in the following sections.

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

## Administrating quotas

A quota with a value of -1 means unlimited

### Reading per quotaroot mail count limitation

```
curl -XGET http://ip:port/quota/count
```

The answer looks like:

```
100000
```

Response codes:
 - 200: Nothing special
 - 500: Internal error

### Updating per quotaroot mail count limitation

```
curl -XPUT http://ip:port/quota/count -d '1024000000'
```

Response codes:

 - 204: Value updated
 - 400: The body is not a positive integer
 - 500: Internal error

### Removing per quotaroot mail count limitation

It removes the limitation, and the quota becomes UNILIMITED.

```
curl -XDELETE http://ip:port/quota/count
```

Response codes:

 - 204: Value updated to UNLIMITED
 - 500: Internal error

### Reading per quotaroot size limitation

```
curl -XGET http://ip:port/quota/size
```

The answer looks like:

```
100000
```

It represent the allowed Byte count of the mailboxes belonging to this quotaroot.

Response codes:

 - 200: Nothing special
 - 500: Internal error

### Updating per quotaroot size limitation

```
curl -XPUT http://ip:port/quota/size -d '1024000000'
```

Response codes:

 - 204: Value updated
 - 400: The body is not a positive integer
 - 500: Internal error

### Removing per quotaroot size limitation

It removes the limitation, and the quota becomes UNILIMITED.

```
curl -XDELETE http://ip:port/quota/size
```

Response codes:

 - 204: Value updated to UNLIMITED
 - 500: Internal error

### Managing count and size at the same time

```
curl -XGET http://ip:port/quota/
```

Will return:

```
{"count":52,"size":42}
```

Response codes:

 - 200: Success
 - 500: Internal error

You can also write the value the same way:

```
curl -XPUT http://ip:port/quota/ -d '{"count":52,"size":42}'
```

Response codes:

 - 204: Success
 - 400: Invalid JSON, or numbers are less than -1.
 - 500: Internal error

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

Will run the migrations you need to reach schema version 3.

Response codes:

 - 204: Success
 - 400: The version is invalid. The version should be a strictly positive number.
 - 410: Error running the migration. This resource is gone away. Reason is mentionned in the body.
 - 500: Internal error. This can result from a partial migration, in which case the reason is contained in the body.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.

### Upgrading to the latest version

```
curl -XPOST http://ip:port/cassandra/version/upgrade/latest
```

Will run the migrations you need to reach the latest schema version.

Response codes:

 - 204: Success
 - 410: Error running the migration. This resource is gone away. Reason is mentionned in the body.
 - 500: Internal error. This can result from a partial migration, in which case the reason is contained in the body.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.

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