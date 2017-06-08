Web administration for JAMES
============================

The web administration supports for now the CRUD operations on the domains,the users, their mailboxes and their quotas, as described in the following sections.

**WARNING**: This API allow authentication only via the use of JWT. If not configured with JWT, an administrator should ensure an attacker can not use this API.

Please also note **webadmin** is only enabled with **Guice**. You can not use it when using James with **Spring**, as the required injections are not implemented.

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