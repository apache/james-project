Web administration for JAMES
============================

The web administration supports for now the CRUD operations on the domains, the users, their mailboxes and their quotas,
 managing mail repositories, performing cassandra migrations, and much more, as described in the following sections.

**WARNING**: This API allow authentication only via the use of JWT. If not configured with JWT, an administrator should ensure an attacker can not use this API.

By the way, some endpoints are not filtered by authentication. Those endpoints are not related to data stored in James, for example: Swagger documentation & James health checks.

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

Also be aware that, in case things go wrong, all endpoints might return a 500 internal error (with a JSON body formatted
as exposed above). To avoid information duplication, this is ommited on endpoint specific documentation.

## Navigation menu

 - [HealthCheck](#HealthCheck)
 - [Administrating domains](#Administrating_domains)
 - [Administrating users](#Administrating_users)
 - [Administrating user mailboxes](#Administrating_user_mailboxes)
 - [Administrating quotas by users](#Administrating_quotas_by_users)
 - [Administrating quotas by domains](#Administrating_quotas_by_domains)
 - [Administrating global quotas](#Administrating_global_quotas)
 - [Cassandra Schema upgrades](#Cassandra_Schema_upgrades)
 - [Correcting ghost mailbox](#Correcting_ghost_mailbox)
 - [Creating address group](#Creating_address_group)
 - [Creating address forwards](#Creating_address_forwards)
 - [Creating address aliases](#Creating_address_aliases)
 - [Creating address domain](#Creating_address_domain)
 - [Administrating mail repositories](#Administrating_mail_repositories)
 - [Administrating mail queues](#Administrating_mail_queues)
 - [Administrating DLP Configuration](#Administrating_DLP_Configuration)
 - [Administrating Sieve quotas](#Administrating_Sieve_quotas)
 - [ReIndexing](#ReIndexing)
 - [Event Dead Letter](#Event_Dead_Letter)
 - [Deleted Messages Vault](#Deleted_Messages_Vault)
 - [Task management](#Task_management)
 - [Cassandra extra operations](#Cassandra_extra_operations)

## HealthCheck

   - [Check all components](#Check_all_components)
   - [Check single component](#Check_single_component)
   - [List all health checks](#List_all_health_checks)
   

### Check all components

This endpoint is simple for now and is just returning the http status code corresponding to the state of checks (see below).
The user has to check in the logs in order to have more information about failing checks.

```
curl -XGET http://ip:port/healthcheck
```

Response codes:

 - 200: All checks have answered with a Healthy status
 - 500: At least one check have answered with a Unhealthy or Degraded status

### Check single component

Performs a health check for the given component. The component is referenced by its URL encoded name.

```
curl -XGET http://ip:port/healthcheck/checks/Cassandra%20Backend
```

Will return the component's name, the component's escaped name, the health status and a cause.

```
{
  "componentName": "Cassandra Backend",
  "escapedComponentName": "Cassandra%20Backend",
  "status": "HEALTHY"
  "cause": null
}
```

Response codes:

 - 200: The check has answered with a Healthy status.
 - 404: A component with the given name was not found.
 - 500: The check has anwered with a Unhealthy or Degraded status.
 
### List all health checks
 
This endpoint lists all the available health checks.
 
```
curl -XGET http://ip:port/healthcheck/checks
```
 
Will return the list of all available health checks.
 
```
[
    {
        "componentName": "Cassandra Backend",
        "escapedComponentName": "Cassandra%20Backend"
    }
]
```
 
Response codes:
 
  - 200: List of available health checks

## Administrating domains

   - [Create a domain](#Create_a_domain)
   - [Delete a domain](#Delete_a_domain)
   - [Test if a domain exists](#Test_if_a_domain_exists)
   - [Get the list of domains](#Get_the_list_of_domains)
   - [Get the list of aliases for a domain](#Get_the_list_of_aliases_for_a_domain)
   - [Create an alias for a domain](#Create_an_alias_for_a_domain)
   - [Delete an alias for a domain](#Delete_an_alias_for_a_domain)

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

### Delete a domain

```
curl -XDELETE http://ip:port/domains/domainToBeDeleted
```

Response codes:

 - 204: The domain was successfully removed

### Test if a domain exists

```
curl -XGET http://ip:port/domains/domainName
```

Response codes:

 - 204: The domain exists
 - 404: The domain does not exist

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

### Get the list of aliases for a domain

```
curl -XGET http://ip:port/domains/destination.domain.tld/aliases
```

Possible response:

```
[
  {"source": "source1.domain.tld"},
  {"source": "source2.domain.tld"}
]
```

When sending an email to an email address having source1.domain.tld or source2.domain.tld as a domain part (exemple: benwa@source1.domain.tld), then
the domain part will be rewritten into destination.domain.tld (so into benwa@destination.domain.tld).

Response codes:

 - 200: The domain aliases was successfully retrieved
 - 400: destination.domain.tld has an invalid syntax
 - 404: destination.domain.tld is not part of handled domains and does not have local domains as aliases.

### Create an alias for a domain

To create a domain alias execute the following query:

```
curl -XPUT http://ip:port/domains/destination.domain.tld/aliases/source.domain.tld
```

When sending an email to an email address having source.domain.tld as a domain part (exemple: benwa@source.domain.tld), then
the domain part will be rewritten into destination.domain.tld (so into benwa@destination.domain.tld).


Response codes:

 - 204: The redirection now exists
 - 400: source.domain.tld or destination.domain.tld have an invalid syntax
 - 400: source domain and destination domain are the same
 - 404: source.domain.tld are not part of handled domains.

### Delete an alias for a domain


To delete a domain alias execute the following query:

```
curl -XDELETE http://ip:port/domains/destination.domain.tld/aliases/source.domain.tld
```

When sending an email to an email address having source.domain.tld as a domain part (exemple: benwa@source.domain.tld), then
the domain part will be rewritten into destination.domain.tld (so into benwa@destination.domain.tld).


Response codes:

 - 204: The redirection now exists
 - 400: source.domain.tld or destination.domain.tld have an invalid syntax
 - 400: source domain and destination domain are the same
 - 404: source.domain.tld are not part of handled domains.

## Administrating users

   - [Create a user](#Create_a_user)
   - [Updating a user password](#Updating_a_user_password)
   - [Deleting a domain](#Deleting_a_user)
   - [Retrieving the user list](#Retrieving_the_user_list)

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

## Administrating user mailboxes

 - [Creating a mailbox](#Creating_a_mailbox)
 - [Deleting a mailbox and its children](#Deleting_a_mailbox_and_its_children)
 - [Testing existence of a mailbox](#Testing_existence_of_a_mailbox)
 - [Listing user mailboxes](#Listing_user_mailboxes)
 - [Deleting_user_mailboxes](#Deleting_user_mailboxes)

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

### Deleting user mailboxes

```
curl -XDELETE http://ip:port/users/usernameToBeUsed/mailboxes
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The user do not have mailboxes anymore
 - 404: The user name does not exist

## Administrating quotas by users

 - [Getting the quota for a user](#Getting_the_quota_for_a_user)
 - [Updating the quota for a user](#Updating_the_quota_for_a_user)
 - [Getting the quota count for a user](#Getting_the_quota_count_for_a_user)
 - [Updating the quota count for a user](#Updating_the_quota_count_for_a_user)
 - [Deleting the quota count for a user](#Deleting_the_quota_count_for_a_user)
 - [Getting the quota size for a user](#Getting_the_quota_size_for_a_user)
 - [Updating the quota size for a user](#Updating_the_quota_size_for_a_user)
 - [Deleting the quota size for a user](#Deleting_the_quota_size_for_a_user)
 - [Searching user by quota ratio](#Searching_user_by_quota_ratio)

### Getting the quota for a user

```
curl -XGET http://ip:port/quota/users/usernameToBeUsed
```

Resource name usernameToBeUsed should be an existing user

The answer is the details of the quota of that user.

```
{
  "global": {
    "count":252,
    "size":242
  },
  "domain": {
    "count":152,
    "size":142
  },
  "user": {
    "count":52,
    "size":42
  },
  "computed": {
    "count":52,
    "size":42
  },
  "occupation": {
    "size":1000,
    "count":10000,
    "ratio": {
      "size":0.8,
      "count":0.6,
      "max":0.8
    }
  }
}
```

 - The `global` entry represent the quota limit allowed on this James server.
 - The `domain` entry represent the quota limit allowed for the user of that domain.
 - The `user` entry represent the quota limit allowed for this specific user.
 - The `computed` entry represent the quota limit applied for this user, resolved from the upper values.
 - The `occupation` entry represent the occupation of the quota for this user. This includes used count and size as well as occupation ratio (used / limit).

Note that `quota` object can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 404: The user does not exist

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
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

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
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

### Deleting the quota count for a user

```
curl -XDELETE http://ip:port/quota/users/usernameToBeUsed/count
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

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
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

### Deleting the quota size for a user

```
curl -XDELETE http://ip:port/quota/users/usernameToBeUsed/size
```

Resource name usernameToBeUsed should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

### Searching user by quota ratio

```
curl -XGET http://ip:port/quota/users?minOccupationRatio=0.8&maxOccupationRatio=0.99&limit=100&offset=200&domain=oppen-paas.org
```

Will return:

```
[
  {
    "username":"user@open-paas.org",
    "detail": {
      "global": {
        "count":252,
        "size":242
      },
      "domain": {
        "count":152,
        "size":142
      },
      "user": {
        "count":52,
        "size":42
      },
      "computed": {
        "count":52,
        "size":42
      },
      "occupation": {
        "size":1000,
        "count":10000,
        "ratio": {
          "size":0.8,
          "count":0.6,
          "max":0.8
        }
      }
    }
  },
  ...
]
```

Where:

 - **minOccupationRatio** is a query parameter determining the minimum occupation ratio of users to be returned.
 - **maxOccupationRatio** is a query parameter determining the maximum occupation ratio of users to be returned.
 - **domain** is a query parameter determining the domain of users to be returned.
 - **limit** is a query parameter determining the maximum number of users to be returned.
 - **offset** is a query parameter determining the number of users to skip.

Please note that users are alphabetically ordered on username.

The response is a list of usernames, with attached quota details as defined [here](#getting-the-quota-for-a-user).

Response codes:

 - 200: List of users had successfully been returned.
 - 400: Validation issues with parameters

## Administrating quotas by domains

 - [Getting the quota for a domain](#Getting_the_quota_for_a_domain)
 - [Updating the quota for a domain](#Updating_the_quota_for_a_domain)
 - [Getting the quota count for a domain](#Getting_the_quota_count_for_a_domain)
 - [Updating the quota count for a domain](#Updating_the_quota_count_for_a_domain)
 - [Deleting the quota count for a domain](#Deleting_the_quota_count_for_a_domain)
 - [Getting the quota size for a domain](#Getting_the_quota_size_for_a_domain)
 - [Updating the quota size for a domain](#Updating_the_quota_size_for_a_domain)
 - [Deleting the quota size for a domain](#Deleting_the_quota_size_for_a_domain)

### Getting the quota for a domain

```
curl -XGET http://ip:port/quota/domains/domainToBeUsed
```

Resource name domainToBeUsed should be an existing domain. For example:

```
curl -XGET http://ip:port/quota/domains/james.org
```

The answer will detail the default quota applied to users belonging to that domain:

```
{
  "global": {
    "count":252,
    "size":null
  },
  "domain": {
    "count":null,
    "size":142
  },
  "computed": {
    "count":252,
    "size":142
  }
}
```

 - The `global` entry represents the quota limit defined on this James server by default.
 - The `domain` entry represents the quota limit allowed for the user of that domain by default.
 - The `computed` entry represents the quota limit applied for the users of that domain, by default, resolved from the upper values.

Note that `quota` object can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 200: The domain's quota was successfully retrieved
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Updating the quota for a domain

```
curl -XPUT http://ip:port/quota/domains/domainToBeUsed
```

Resource name domainToBeUsed should be an existing domain.

The body can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.
 - 409: The requested restriction can’t be enforced right now.

### Getting the quota count for a domain

```
curl -XGET http://ip:port/quota/domains/domainToBeUsed/count
```

Resource name domainToBeUsed should be an existing domain.

The answer looks like:

```
52
```

Response codes:

 - 200: The domain's quota was successfully retrieved
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.
 - 500: Internal error while accessing the domain's quota

### Updating the quota count for a domain

```
curl -XPUT http://ip:port/quota/domains/domainToBeUsed/count
```

Resource name domainToBeUsed should be an existing domain.

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.
 - 409: The requested restriction can’t be enforced right now.

### Deleting the quota count for a domain

```
curl -XDELETE http://ip:port/quota/domains/domainToBeUsed/count
```

Resource name domainToBeUsed should be an existing domain.

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Getting the quota size for a domain

```
curl -XGET http://ip:port/quota/domains/domainToBeUsed/size
```

Resource name domainToBeUsed should be an existing domain.

The answer looks like:

```
52
```

Response codes:

 - 200: The domain's quota was successfully retrieved
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Updating the quota size for a domain

```
curl -XPUT http://ip:port/quota/domains/domainToBeUsed/size
```

Resource name domainToBeUsed should be an existing domain.

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

### Deleting the quota size for a domain

```
curl -XDELETE http://ip:port/quota/domains/domainToBeUsed/size
```

Resource name domainToBeUsed should be an existing domain.

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 409: The requested restriction can’t be enforced right now.
 - 500: Internal server error - Something went bad on the server side.

## Administrating global quotas

 - [Getting the global quota](#Getting_the_global_quota)
 - [Updating global quota](#Updating_global_quota)
 - [Getting the global quota count](#Getting_the_global_quota_count)
 - [Updating the global quota count](#Updating_the_global_quota_count)
 - [Deleting the global quota count](#Deleting_the_global_quota_count)
 - [Getting the global quota size](#Getting_the_global_quota_size)
 - [Updating the global quota size](#Updating_the_global_quota_size)
 - [Deleting the global quota size](#Deleting_the_global_quota_size)

### Getting the global quota

```
curl -XGET http://ip:port/quota/
```

Resource name usernameToBeUsed should be an existing user

The answer is the details of the quota of that user.

```
{
  "count":252,
  "size":242
}
```

Note that `quota` object can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 200: The quota was successfully retrieved

### Updating global quota

```
curl -XPUT http://ip:port/quota
```

The body can contain a fixed value, an empty value (null) or an unlimited value (-1):

```
{"count":52,"size":42}

{"count":null,"size":null}

{"count":52,"size":-1}
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).

### Getting the global quota count

```
curl -XGET http://ip:port/quota/count
```

Resource name usernameToBeUsed should be an existing user

The answer looks like:

```
52
```

Response codes:

 - 200: The quota was successfully retrieved

### Updating the global quota count

```
curl -XPUT http://ip:port/quota/count
```


The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).

### Deleting the global quota count

```
curl -XDELETE http://ip:port/quota/count
```

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist
 - 409: The requested restriction can’t be enforced right now.

### Getting the global quota size

```
curl -XGET http://ip:port/quota/size
```


The answer looks like:

```
52
```

Response codes:

 - 200: The quota was successfully retrieved

### Updating the global quota size

```
curl -XPUT http://ip:port/quota/size
```

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 409: The requested restriction can’t be enforced right now.

### Deleting the global quota size

```
curl -XDELETE http://ip:port/quota/size
```

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 409: The requested restriction can’t be enforced right now.

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

 - [Retrieving current Cassandra schema version](#Retrieving_current_cassandra_schema_version)
 - [Retrieving latest available Cassandra schema version](#Retrieving_latest_available_cassandra_schema_version)
 - [Upgrading to a specific version](#Upgrading_to_a_specific_version)
 - [Upgrading to the latest version](#Upgrading_to_the_latest_version)

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

Note that the group mail address is virtual: it does not correspond to an existing user.

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

 - [Listing groups](#Listing_groups)
 - [Listing members of a group](#Listing_members_of_a_group)
 - [Adding a group member](#Adding_a_group_member)
 - [Removing a group member](#Removing_a_group_member)

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

### Adding a group member

```
curl -XPUT http://ip:port/address/groups/group@domain.com/member@domain.com
```

Will add member@domain.com to group@domain.com, creating the group if needed

Response codes:

 - 204: Success
 - 400: Group structure or member is not valid
 - 400: Domain in the source is not managed by the DomainList
 - 409: Requested group address is already used for another purpose

### Removing a group member

```
curl -XDELETE http://ip:port/address/groups/group@domain.com/member@domain.com
```

Will remove member@domain.com from group@domain.com, removing the group if group is empty after deletion

Response codes:

 - 204: Success
 - 400: Group structure or member is not valid

## Creating address forwards

You can use **webadmin** to define address forwards.

When a specific email is sent to the base mail address, every forward destination addresses will receive it.

Please note that the base address can be optionaly part of the forward destination. In that case, the base recipient
also receive a copy of the mail. Otherwise he is ommitted.

Forwards can be defined for existing users. It then defers from "groups".

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

 - [Listing Forwards](#Listing_forwards)
 - [Listing destinations in a forward](#Listing_destinations_in_a_forward)
 - [Adding a new destination to a forward](#Adding_a_new_destination_to_a_forward)
 - [Removing a destination of a forward](#Removing_a_destination_of_a_forward)

### Listing Forwards

```
curl -XGET http://ip:port/address/forwards
```

Will return the users having forwards configured as a list of JSON Strings representing mail addresses. For instance:

```
["user1@domain.com", "user2@domain.com"]
```

Response codes:

 - 200: Success

### Listing destinations in a forward

```
curl -XGET http://ip:port/address/forwards/user@domain.com
```

Will return the destination addresses of this forward as a list of JSON Strings representing mail addresses. For instance:

```
[
  {"mailAddres":"destination1@domain.com"},
  {"mailAddres":"destination2@domain.com"}
]
```

Response codes:

 - 200: Success
 - 400: Forward structure is not valid
 - 404: The given user don't have forwards or does not exist

### Adding a new destination to a forward

```
curl -XPUT http://ip:port/address/forwards/user@domain.com/targets/destination@domain.com
```

Will add destination@domain.com to user@domain.com, creating the forward if needed

Response codes:

 - 204: Success
 - 400: Forward structure or member is not valid
 - 400: Domain in the source is not managed by the DomainList
 - 404: Requested forward address does not match an existing user

### Removing a destination of a forward

```
curl -XDELETE http://ip:port/address/forwards/user@domain.com/targets/destination@domain.com
```

Will remove destination@domain.com from user@domain.com, removing the forward if forward is empty after deletion

Response codes:

 - 204: Success
 - 400: Forward structure or member is not valid

## Creating address aliases

You can use **webadmin** to define aliases for an user.

When a specific email is sent to the alias address, the destination address of the alias will receive it.

Aliases can be defined for existing users.

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

 - [Listing users with aliases](#Listing_users_with_aliases)
 - [Listing alias sources of an user](#Listing_alias_sources_of_an_user)
 - [Adding a new alias to an user](#Adding_a_new_alias_to_an_user)
 - [Removing an alias of an user](#Removing_an_alias_of_an_user)

### Listing users with aliases

```
curl -XGET http://ip:port/address/aliases
```

Will return the users having aliases configured as a list of JSON Strings representing mail addresses. For instance:

```
["user1@domain.com", "user2@domain.com"]
```

Response codes:

 - 200: Success

### Listing alias sources of an user

```
curl -XGET http://ip:port/address/aliases/user@domain.com
```

Will return the aliases of this user as a list of JSON Strings representing mail addresses. For instance:

```
[
  {"source":"alias1@domain.com"},
  {"source":"alias2@domain.com"}
]
```

Response codes:

 - 200: Success
 - 400: Alias structure is not valid

### Adding a new alias to an user

```
curl -XPUT http://ip:port/address/aliases/user@domain.com/sources/alias@domain.com
```

Will add alias@domain.com to user@domain.com, creating the alias if needed

Response codes:

 - 204: OK
 - 400: Alias structure or member is not valid
 - 400: The alias source exists as an user already
 - 400: Source and destination can't be the same!
 - 400: Domain in the source is not managed by the DomainList

### Removing an alias of an user

```
curl -XDELETE http://ip:port/address/aliases/user@domain.com/sources/alias@domain.com
```

Will remove alias@domain.com from user@domain.com, removing the alias if needed

Response codes:

 - 204: OK
 - 400: Alias structure or member is not valid

## Creating address domain

You can use **webadmin** to define domain mappings.

Given a configured source (from) domain and a destination (to) domain, when an email is sent to an address belonging to the source domain, then the domain part of this address is overwritten, the destination domain is then used.
A source (from) domain can have many destination (to) domains. 

For example: with a source domain `james.apache.org` maps to two destination domains `james.org` and `apache-james.org`, when a mail is sent to `admin@james.apache.org`, then it will be routed to `admin@james.org` and `admin@apache-james.org`

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

 - [Listing all domain mappings](#Listing_all_domain_mappings)
 - [Listing all destination domains for a source domain](#Listing_all_destination_domains_for_a_source_domain)
 - [Adding a domain mapping](#Adding_a_domain_mapping)
 - [Removing a domain mapping](#Removing_a_domain_mapping)

### Listing all domain mappings
```
curl -XGET http://ip:port/domainMappings
```

Will return all configured domain mappings
```
{
  "firstSource.org" : ["firstDestination.com", "secondDestination.net"],
  "secondSource.com" : ["thirdDestination.com", "fourthDestination.net"],
}
```

Response codes:

 - 200: OK

### Listing all destination domains for a source domain
```
curl -XGET http://ip:port/domainMappings/sourceDomain.tld
```

With `sourceDomain.tld` as the value passed to `fromDomain` resource name, the API will return all destination domains configured to that domain

```
["firstDestination.com", "secondDestination.com"]
```

Response codes:

 - 200: OK
 - 400: The `fromDomain` resource name is invalid
 - 400: The `fromDomain` is not managed by the DomainList
 - 404: The `fromDomain` resource name is not found

### Adding a domain mapping
```
curl -XPUT http://ip:port/domainMappings/sourceDomain.tld
```

Body:
```
destination.tld
```

With `sourceDomain.tld` as the value passed to `fromDomain` resource name, the API will add a destination domain specified in the body to that domain

Response codes:

 - 204: OK
 - 400: The `fromDomain` resource name is invalid
 - 400: The destination domain specified in the body is invalid

### Removing a domain mapping
```
curl -XDELETE http://ip:port/domainMappings/sourceDomain.tld
```

Body:
```
destination.tld
```

With `sourceDomain.tld` as the value passed to `fromDomain` resource name, the API will remove a destination domain specified in the body mapped to that domain

Response codes:

 - 204: OK
 - 400: The `fromDomain` resource name is invalid
 - 400: The destination domain specified in the body is invalid

## Administrating mail repositories

 - [Create a mail repository](#Create_a_mail_repository)
 - [Listing mail repositories](#Listing_mail_repositories)
 - [Getting additional information for a mail repository](#Getting_additional_information_for_a_mail_repository)
 - [Listing mails contained in a mail repository](#Listing_mails_contained_in_a_mail_repository)
 - [Reading a mail details](#Reading_a_mail_details)
 - [Removing a mail from a mail repository](#Removing_a_mail_from_a_mail_repository)
 - [Removing all mails from a mail repository](#Removing_all_mails_from_a_mail_repository)
 - [Reprocessing mails from a mail repository](#Reprocessing_mails_from_a_mail_repository)
 - [Reprocessing a specific mail from a mail repository](#Reprocessing_a_specific_mail_from_a_mail_repository)

### Create a mail repository

```
curl -XPUT http://ip:port/mailRepositories/encodedPathOfTheRepository?protocol=someProtocol
```

Resource name `encodedPathOfTheRepository` should be the resource path of the created mail repository. Example:

```
curl -XPUT http://ip:port/mailRepositories/mailRepo?protocol=file
```

Response codes:

 - 204: The repository is created

### Listing mail repositories

```
curl -XGET http://ip:port/mailRepositories
```

The answer looks like:

```
[
    {
        "repository": "var/mail/error/",
        "path": "var%2Fmail%2Ferror%2F"
    },
    {
        "repository": "var/mail/relay-denied/",
        "path": "var%2Fmail%2Frelay-denied%2F"
    },
    {
        "repository": "var/mail/spam/",
        "path": "var%2Fmail%2Fspam%2F"
    },
    {
        "repository": "var/mail/address-error/",
        "path": "var%2Fmail%2Faddress-error%2F"
    }
]
```

You can use `id`, the encoded URL of the repository, to access it in later requests.

Response codes:

 - 200: The list of mail repositories

### Getting additional information for a mail repository

```
curl -XGET http://ip:port/mailRepositories/encodedPathOfTheRepository/
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

```
curl -XGET http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/
```

The answer looks like:

```
{
   "repository": "var/mail/error/",
   "path": "mail%2Ferror%2F",
   "size": 243
}
```

Response codes:

 - 200: Additonnal information for that repository
 - 404: This repository can not be found

### Listing mails contained in a mail repository

```
curl -XGET http://ip:port/mailRepositories/encodedPathOfTheRepository/mails
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

```
curl -XGET http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails
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
curl -XGET http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?limit=100&offset=500
```

Response codes:

 - 200: The list of mail keys contained in that mail repository
 - 400: Invalid parameters
 - 404: This repository can not be found

### Reading/downloading a mail details

```
curl -XGET http://ip:port/mailRepositories/encodedPathOfTheRepository/mails/mailKey
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

```
curl -XGET http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/mail-key-1
```

If the Accept header in the request is "application/json", then the response looks like:

```
{
    "name": "mail-key-1",
    "sender": "sender@domain.com",
    "recipients": ["recipient1@domain.com", "recipient2@domain.com"],
    "state": "address-error",
    "error": "A small message explaining what happened to that mail...",
    "remoteHost": "111.222.333.444",
    "remoteAddr": "127.0.0.1",
    "lastUpdated": null
}
```
If the Accept header in the request is "message/rfc822", then the response will be the _eml_ file itself.

Additional query parameter `additionalFields` add the existing informations to the response for the supported values:
 - attributes
 - headers
 - textBody
 - htmlBody
 - messageSize
 - perRecipientsHeaders

```
curl -XGET http://ip:port/mailRepositories/file%3A%2F%2Fvar%2Fmail%2Ferror%2F/mails/mail-key-1?additionalFields=attributes,headers,textBody,htmlBody,messageSize,perRecipientsHeaders
```

Give the following kind of response:

```
{
    "name": "mail-key-1",
    "sender": "sender@domain.com",
    "recipients": ["recipient1@domain.com", "recipient2@domain.com"],
    "state": "address-error",
    "error": "A small message explaining what happened to that mail...",
    "remoteHost": "111.222.333.444",
    "remoteAddr": "127.0.0.1",
    "lastUpdated": null,
    "attributes": {
      "name2": "value2",
      "name1": "value1"
    },
    "perRecipientsHeaders": {
      "third@party": {
        "headerName1": [
          "value1",
          "value2"
        ],
        "headerName2": [
          "value3",
          "value4"
        ]
      }
    },
    "headers": {
      "headerName4": [
        "value6",
        "value7"
      ],
      "headerName3": [
        "value5",
        "value8"
      ]
    },
    "textBody": "My body!!",
    "htmlBody": "My <em>body</em>!!",
    "messageSize": 42424242
}
```

Response codes:

 - 200: Details of the mail
 - 404: This repository or mail can not be found

### Removing a mail from a mail repository

```
curl -XDELETE http://ip:port/mailRepositories/encodedPathOfTheRepository/mails/mailKey
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

```
curl -XDELETE http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/mail-key-1
```

Response codes:

 - 204: This mail no longer exists in this repository
 - 404: This repository can not be found

### Removing all mails from a mail repository


```
curl -XDELETE http://ip:port/mailRepositories/encodedPathOfTheRepository/mails
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

```
curl -XDELETE http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails
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

The scheduled task will have the following type `clearMailRepository` and the following `additionalInformation`:

```
{
  "repositoryPath":"var/mail/error/",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing mails from a mail repository

Sometime, you want to re-process emails stored in a mail repository. For instance, you can make a configuration error, or there can be a James bug that makes processing of some mails fail. Those mail will be stored in a mail repository. Once you solved the problem, you can reprocess them.

To reprocess mails from a repository:

```
curl -XPATCH http://ip:port/mailRepositories/encodedPathOfTheRepository/mails?action=reprocess
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?action=reprocess
```

Additional query paramaters are supported:
 - `queue` allow you to target the mail queue you want to enqueue the mails in.
 - `processor` allow you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.


For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?action=reprocess&processor=transport&queue=spool
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

The scheduled task will have the following type `reprocessingAllTask` and the following `additionalInformation`:

```
{
  "repositoryPath":"var/mail/error/",
  "targetQueue":"spool",
  "targetProcessor":"transport",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing a specific mail from a mail repository

To reprocess a specific mail from a mail repository:

```
curl -XPATCH http://ip:port/mailRepositories/encodedPathOfTheRepository/mails/mailKey?action=reprocess
```

Resource name `encodedPathOfTheRepository` should be the resource id of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/name1?action=reprocess
```

Additional query paramaters are supported:
 - `queue` allow you to target the mail queue you want to enqueue the mails in.
 - `processor` allow you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.


For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/name1?action=reprocess&processor=transport&queue=spool
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

The scheduled task will have the following type `reprocessingOneTask` and the following `additionalInformation`:

```
{
  "repositoryPath":"var/mail/error/",
  "targetQueue":"spool",
  "targetProcessor":"transport",
  "mailKey":"name1"
}
```

## Administrating mail queues

 - [Listing mail queues](#Listing_mail_queues)
 - [Getting a mail queue details](#Getting_a_mail_queue_details)
 - [Listing the mails of a mail queue](#Listing_the_mails_of_a_mail_queue)
 - [Deleting mails from a mail queue](#Deleting_mails_from_a_mail_queue)
 - [Clearing a mail queue](#Clearing_a_mail_queue)
 - [Flushing mails from a mail queue](#Flushing_mails_from_a_mail_queue)

### Listing mail queues

```
curl -XGET http://ip:port/mailQueues
```

The answer looks like:

```
["outgoing","spool"]
```

Response codes:

 - 200: The list of mail queues

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

 - 201: Success. Corresponding task id is returned.
 - 400: Invalid request
 - 404: The mail queue does not exist

The scheduled task will have the following type `deleteMailsFromMailQueue` and the following `additionalInformation`:

```
{
  "mailQueueName":"outgoing",
  "initialCount":10,
  "remainingCount": 5,
  "sender": "sender@james.org",
  "name": "Java Developer",
  "recipient: "recipient@james.org"
}
```

### Clearing a mail queue

```
curl -XDELETE http://ip:port/mailQueues/mailQueueName/mails
```

All mails from the given mail queue will be deleted.


Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Invalid request
 - 404: The mail queue does not exist

The scheduled task will have the following type `clearMailQueue` and the following `additionalInformation`:

```
{
  "mailQueueName":"outgoing",
  "initialCount":10,
  "remainingCount": 0
}
```

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

## Administrating DLP Configuration

DLP (stands for Data Leak Prevention) is supported by James. A DLP matcher will, on incoming emails,
execute regular expressions on email sender, recipients or content, in order to report suspicious emails to
an administrator. WebAdmin can be used to manage these DLP rules on a per `senderDomain` basis.

`senderDomain` is domain of the sender of incoming emails, for example: `apache.org`, `james.org`,...
Each `senderDomain` correspond to a distinct DLP configuration.

- [List DLP configuration by sender domain](List_dlp_configuration_by_sender_domain)
- [Store DLP configuration by sender domain](Store_dlp_configuration_by_sender_domain)
- [Remove DLP configuration by sender domain](Remove_dlp_configuration_by_sender_domain)
- [Fetch a DLP configuration item by sender domain and rule id](Fetch_a_dlp_configuration_item_by_sender_domain_and_rule_id)

### List DLP configuration by sender domain

Retrieve a DLP configuration for corresponding `senderDomain`, a configuration contains list of configuration items

```
curl -XGET http://ip:port/dlp/rules/senderDomain
```

Response codes:

 - 200: A list of dlp configuration items is returned
 - 400: Invalid senderDomain or payload in request
 - 404: The domain does not exist.

This is an example of returned body. The rules field is a list of rules as described below.

```
{"rules : [
  {
    "id": "1",
    "expression": "james.org",
    "explanation": "Find senders or recipients containing james[any char]org",
    "targetsSender": true,
    "targetsRecipients": true,
    "targetsContent": false
  },
  {
    "id": "2",
    "expression": "Find senders containing apache[any char]org",
    "explanation": "apache.org",
    "targetsSender": true,
    "targetsRecipients": false,
    "targetsContent": false
  }
]}
```

### Store DLP configuration by sender domain

Store a DLP configuration for corresponding `senderDomain`, if any item of DLP configuration in the request is stored before, 
it will not be stored anymore

```
curl -XPUT http://ip:port/dlp/rules/senderDomain
```

The body can contain a list of DLP configuration items formed by those fields: 
- `id`(String) is mandatory, unique identifier of the configuration item
- `expression`(String) is mandatory, regular expression to match contents of targets
- `explanation`(String) is optional, description of the configuration item
- `targetsSender`(boolean) is optional and defaults to false. If true, `expression` will be applied to Sender and to From headers of the mail
- `targetsContent`(boolean) is optional and defaults to false. If true, `expression` will be applied to Subject headers and textual bodies (text/plain and text/html) of the mail
- `targetsRecipients`(boolean) is optional and defaults to false. If true, `expression` will be applied to recipients of the mail

This is an example of returned body. The rules field is a list of rules as described below.

```
{"rules": [
  {
    "id": "1",
    "expression": "james.org",
    "explanation": "Find senders or recipients containing james[any char]org",
    "targetsSender": true,
    "targetsRecipients": true,
    "targetsContent": false
  },
  {
    "id": "2",
    "expression": "Find senders containing apache[any char]org",
    "explanation": "apache.org",
    "targetsSender": true,
    "targetsRecipients": false,
    "targetsContent": false
  }
]}
```

Response codes:

 - 204: List of dlp configuration items is stored
 - 400: Invalid senderDomain or payload in request
 - 404: The domain does not exist.

### Remove DLP configuration by sender domain

Remove a DLP configuration for corresponding `senderDomain`

```
curl -XDELETE http://ip:port/dlp/rules/senderDomain
```

Response codes:

 - 204: DLP configuration is removed
 - 400: Invalid senderDomain or payload in request
 - 404: The domain does not exist.


### Fetch a DLP configuration item by sender domain and rule id

Retrieve a DLP configuration rule for corresponding `senderDomain` and a `ruleId`

```
curl -XGET http://ip:port/dlp/rules/senderDomain/rules/ruleId
```

Response codes:

 - 200: A dlp configuration item is returned
 - 400: Invalid senderDomain or payload in request
 - 404: The domain and/or the rule does not exist.

This is an example of returned body.

```
{
  "id": "1",
  "expression": "james.org",
  "explanation": "Find senders or recipients containing james[any char]org",
  "targetsSender": true,
  "targetsRecipients": true,
  "targetsContent": false
}
```

## Administrating Sieve quotas

Some limitations on space Users Sieve script can occupy can be configured by default, and overridden by user.

 - [Retrieving global sieve quota](#Retieving_global_sieve_quota)
 - [Updating global sieve quota](#Updating_global_sieve_quota)
 - [Removing global sieve quota](#Removing_global_sieve_quota)
 - [Retieving user sieve quota](#Retieving_user_sieve_quota)
 - [Updating user sieve quota](#Updating_user_sieve_quota)
 - [Removing user sieve quota](#Removing_user_sieve_quota)

### Retrieving global sieve quota

This endpoints allows to retrieve the global Sieve quota, which will be users default:

```
curl -XGET http://ip:port/sieve/quota/default
```

Will return the bytes count allowed by user per default on this server.

```
102400
```

Response codes:
 - 200: Request is a success and the value is returned
 - 204: No default quota is being configured

### Updating global sieve quota

This endpoints allows to update the global Sieve quota, which will be users default:

```
curl -XPUT http://ip:port/sieve/quota/default
```

With the body being the bytes count allowed by user per default on this server.

```
102400
```

Response codes:
 - 204: Operation succeeded
 - 400: Invalid payload

### Removing global sieve quota

This endpoints allows to remove the global Sieve quota. There will no more be users default:

```
curl -XDELETE http://ip:port/sieve/quota/default
```

Response codes:
 - 204: Operation succeeded

### Retrieving user sieve quota

This endpoints allows to retrieve the Sieve quota of a user, which will be this users quota:

```
curl -XGET http://ip:port/sieve/quota/users/user@domain.com
```

Will return the bytes count allowed for this user.

```
102400
```

Response codes:
 - 200: Request is a success and the value is returned
 - 204: No quota is being configured for this user

### Updating user sieve quota

This endpoints allows to update the Sieve quota of a user, which will be users default:

```
curl -XPUT http://ip:port/sieve/quota/users/user@domain.com
```

With the body being the bytes count allowed for this user on this server.

```
102400
```

Response codes:
 - 204: Operation succeeded
 - 400: Invalid payload

### Removing user sieve quota

This endpoints allows to remove the Sieve quota of a user. There will no more quota for this userrrrrrr:

```
curl -XDELETE http://ip:port/sieve/quota/users/user@domain.com
```

Response codes:
 - 204: Operation succeeded


## ReIndexing

 - [ReIndexing all mails](#ReIndexing_all_mails)
 - [ReIndexing a user mails](#ReIndexing_a_user_mails)
 - [ReIndexing a mailbox mails](#ReIndexing_a_mailbox_mails)
 - [ReIndexing a single mail](#ReIndexing_a_single_mail)
 - [ReIndexing a single mail by messageId](#ReIndexing_a_single_mail_by_messageId)

Be also aware of the limits of these APIs:

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

### ReIndexing all mails

```
curl -XPOST http://ip:port/mailboxes?task=reIndex
```

Will schedule a task for reIndexing all the mails stored on this James server.

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
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `FullReIndexing` and the following `additionalInformation`:

```
{
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "failures": {
    "mbx1": [{"uid": 35}, {"uid": 45}],
    "mbx2": [{"uid": 38}]
  }
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

### ReIndexing a user mails

```
curl -XPOST http://ip:port/mailboxes?task=reIndex,user=bob%40domain.com
```

Will schedule a task for reIndexing all the mails in "bob@domain.com" mailboxes (encoded above).

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
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `userReIndexing` and the following `additionalInformation`:

```
{
  "user":"bob@domain.com",
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "failures": {
    "mbx1": [{"uid": 35}, {"uid": 45}],
    "mbx2": [{"uid": 38}]
  }
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

### ReIndexing a mailbox mails

```
curl -XPOST http://ip:port/mailboxes/{mailboxId}?task=reIndex
```

Will schedule a task for reIndexing all the mails in one mailbox.

Note that 'mailboxId' path parameter needs to be a (implementation dependent) valid mailboxId.

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
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `mailboxReIndexing` and the following `additionalInformation`:

```
{
  "mailboxId":"{mailboxId}",
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "failures": {
    "mbx1": [{"uid": 35}, {"uid": 45}],
    "mbx2": [{"uid": 38}]
  }
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

### Fixing previously failed ReIndexing

Given `bbdb69c9-082a-44b0-a85a-6e33e74287a5` being a taskId generated for a reIndexing tasks

```
curl -XPOST http://ip:port/mailboxes?task=reIndex&reIndexFailedMessagesOf=bbdb69c9-082a-44b0-a85a-6e33e74287a5
```

Will schedule a task for reIndexing all the mails that this task failed to reIndex.

The response to that request will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positioned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `ReIndexPreviousFailures` and the following `additionalInformation`:

```
{
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "failures": {
    "mbx1": [{"uid": 35}, {"uid": 45}],
    "mbx2": [{"uid": 38}]
  }
}
```

### ReIndexing a single mail

```
curl -XPOST http://ip:port/mailboxes/{mailboxId}/uid/36?task=reIndex
```

Will schedule a task for reIndexing a single email.

Note that 'mailboxId' path parameter needs to be a (implementation dependent) valid mailboxId.

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
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `messageReIndexing` and the following `additionalInformation`:

```
{
  "mailboxId":"{mailboxId}",
  "uid":18
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

### ReIndexing a single mail by messageId

```
curl -XPOST http://ip:port/messages/{messageId}?task=reIndex
```

Will schedule a task for reIndexing a single email in all the mailboxes containing it.

Note that 'messageId' path parameter needs to be a (implementation dependent) valid messageId.

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
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `MessageIdReIndexingTask` and the following `additionalInformation`:

```
{
  "messageId":"18"
}
```

Warning: During the re-indexing, the result of search operations might be altered.

## Event Dead Letter

The EventBus allows to register 'group listeners' that are called in a (potentially) distributed fashion. These group
listeners enable the implementation of some advanced mailbox manager feature like indexing, spam reporting, quota management
and the like.

Upon exceptions, a bounded number of retries are performed (with exponential backoff delays). If after those retries the listener is still
failing, then the event will be stored in the "Event Dead Letter". This API allows diagnosing issues, as well as performing event replay (not implemented yet).

 - [Listing groups](#Listing_groups)
 - [Listing failed events](#Listing_failed_events)
 - [Getting event details](#Getting_event_details)
 - [Deleting an event](#Deleting_an_event)
 - [Redeliver all events](#Redeliver_all_events)
 - [Redeliver group events](#Redeliver_group_events)
 - [Redeliver a single event](#Redeliver_a_single_event)
 - [Rescheduling group execution](#Rescheduling_group_execution)

### Listing groups

This endpoint allows discovering the list of groups.

```
curl -XGET http://ip:port/events/deadLetter/groups
```

Will return a list of group names that can be further used to interact with the dead letter API:

```
["org.apache.james.mailbox.events.EventBusTestFixture$GroupA", "org.apache.james.mailbox.events.GenericGroup-abc"]
```

Response codes:

 - 200: Success. A list of group names is returned.

### Listing failed events

This endpoint allows listing failed events for a given group:

```
curl -XGET http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA
```

Will return a list of insertionIds:

```
["6e0dd59d-660e-4d9b-b22f-0354479f47b4", "58a8f59d-660e-4d9b-b22f-0354486322a2"]
```

Response codes:

 - 200: Success. A list of insertion ids is returned.
 - 400: Invalid group name

### Getting event details

```
curl -XGET http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA/6e0dd59d-660e-4d9b-b22f-0354479f47b4
```

Will return the full JSON associated with this event.

Response codes:

 - 200: Success. A JSON representing this event is returned.
 - 400: Invalid group name or insertionId

### Deleting an event

```
curl -XDELETE http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA/6e0dd59d-660e-4d9b-b22f-0354479f47b4
```

Will delete this event.

Response codes:

 - 204: Success
 - 400: Invalid group name or insertionId

### Redeliver all events

```
curl -XPOST http://ip:port/events/deadLetter
```

Will create a task that will attempt to redeliver all events stored in "Event Dead Letter".
If successful, redelivered events will then be removed from "Dead Letter".

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid action argument

### Redeliver group events

```
curl -XPOST http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA
```

Will create a task that will attempt to redeliver all events of a particular group stored in "Event Dead Letter".
If successful, redelivered events will then be removed from "Dead Letter".

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid group name or action argument

### Redeliver a single event

```
curl -XPOST http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA/6e0dd59d-660e-4d9b-b22f-0354479f47b4
```

Will create a task that will attempt to redeliver a single event of a particular group stored in "Event Dead Letter".
If successful, redelivered event will then be removed from "Dead Letter".

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid group name, insertion id or action argument
 - 404: No event with this insertionId

### Rescheduling group execution

Not implemented yet.

## Deleted Messages Vault

The 'Deleted Message Vault plugin' allows you to keep users deleted messages during a given retention time. This set of routes allow you to *restore* users deleted messages or export them in an archive (not implemented yet).

To move deleted messages in the vault, you need to specifically configure the DeletedMessageVault PreDeletionHook.

Here are the following actions available on the 'Deleted Messages Vault'

 - [Restore Deleted Messages](#Restore_Deleted_Messages)
 - [Export Deleted Messages](#Export_Deleted_Messages)
 - [Purge Deleted Messages](#Purge_Deleted_Messages)
 - [Permanently Remove Deleted Message](#Permanently_Remove_Deleted_Message)

 Note that the 'Deleted Messages Vault' feature is only supported on top of Cassandra-Guice.

### Restore Deleted Messages

Deleted messages of a specific user can be restored by calling the following endpoint:

```
curl -XPOST http://ip:port/deletedMessages/users/userToRestore@domain.ext?action=restore

{"
  "combinator": "and",
  "criteria": [
    {
      "fieldName": "subject",
      "operator": "containsIgnoreCase",
      "value": "Apache James"
    },
    {
      "fieldName": "deliveryDate",
      "operator": "beforeOrEquals",
      "value": "2014-10-30T14:12:00Z"
    },
    {
      "fieldName": "deletionDate",
      "operator": "afterOrEquals",
      "value": "2015-10-20T09:08:00Z"
    },
    {
      "fieldName": "recipients","
      "operator": "contains","
      "value": "recipient@james.org"
    },
    {
      "fieldName": "hasAttachment",
      "operator": "equals",
      "value": "false"
    },
    {
      "fieldName": "sender",
      "operator": "equals",
      "value": "sender@apache.org"
    },
    {
      "fieldName": "originMailboxes",
      "operator": "contains",
      "value":  "02874f7c-d10e-102f-acda-0015176f7922"
    }
  ]
};
```

The requested Json body is made from list of criterion objects which have following structure:

```
{
  "fieldName": "supportedFieldName",
  "operator": "supportedOperator",
  "value": "A plain string represents for the matching value of the corresponding field"
}
```

Deleted Messages which are matched with **all** criterions in the query body will be restored. Here are list of supported fieldName for the restoring:

 - subject: represents for deleted message `subject` field matching. Supports below string operators:
   - contains
   - containsIgnoreCase
   - equals
   - equalsIgnoreCase
 - deliveryDate: represents for deleted message `deliveryDate` field matching. Tested value should follow the right date time with zone offset format (ISO-8601) like
   `2008-09-15T15:53:00+05:00` or `2008-09-15T15:53:00Z` 
   Supports below date time operators:
   - beforeOrEquals: is the deleted message's `deliveryDate` before or equals the time of tested value.
   - afterOrEquals: is the deleted message's `deliveryDate` after or equals the time of tested value
 - deletionDate: represents for deleted message `deletionDate` field matching. Tested value & Supports operators: similar to `deliveryDate`
 - sender: represents for deleted message `sender` field matching. Tested value should be a valid mail address. Supports mail address operator:
   - equals: does the tested sender equal to the sender of the tested deleted message ?   
 - recipients: represents for deleted message `recipients` field matching. Tested value should be a valid mail address. Supports list mail address operator:
   - contains: does the tested deleted message's recipients contain tested recipient ?
 - hasAttachment: represents for deleted message `hasAttachment` field matching. Tested value could be `false` or `true`. Supports boolean operator:
   - equals: does the tested deleted message's hasAttachment property equal to the tested hasAttachment value?
 - originMailboxes: represents for deleted message `originMailboxes` field matching. Tested value is a string serialized of mailbox id. Supports list mailbox id operators:
   - contains: does the tested deleted message's originMailbox ids contain tested mailbox id ?
   
Messages in the Deleted Messages Vault of an specified user that are matched with Query Json Object in the body will be appended to his 'Restored-Messages' mailbox, which will be created if needed.

**Note**:

 - Query parameter `action` is required and should have value `restore` to represent for restoring feature. Otherwise, a bad request response will be returned
 - Query parameter `action` is case sensitive
 - fieldName & operator for passing to the routes are case sensitive
 - Currently, we only support query combinator `and` value, otherwise, requests will be rejected 
 - If you only want to restore by only one criterion, the json body could be simplified to a single criterion:

```
{
  "fieldName": "subject", 
  "operator": "containsIgnoreCase", 
  "value": "Apache James"
}
```

 - For restoring all deleted messages, passing a query json with empty criterion list to represent `matching all deleted messages`: 

```
{
  "combinator": "and",
  "criteria": []
}
```

**Warning**: Current web-admin uses `US` locale as the default. Therefore, there might be some conflicts when using String `containsIgnoreCase` comparators to apply 
on the String data of other special locales stored in the Vault. More details at [JIRA](https://issues.apache.org/jira/browse/MAILBOX-384) 

Response code:

 - 201: Task for restoring deleted has been created
 - 400: Bad request: 
   - action query param is not present
   - action query param is not a valid action
   - user parameter is invalid
   - can not parse the JSON body
   - Json query object contains unsupported operator, fieldName
   - Json query object values violate parsing rules 
 - 404: User not found

The scheduled task will have the following type `deletedMessages/restore` and the following `additionalInformation`:

```
{
  "successfulRestoreCount": 47,
  "errorRestoreCount": 0,
  "user": "userToRestore@domain.ext"
}
```

while:
 - successfulRestoreCount: number of restored messages
 - errorRestoreCount: number of messages that failed to restore
 - user: owner of deleted messages need to restore

### Export Deleted Messages

Retrieve deleted messages matched with requested query from an user then share the content to a targeted mail address (exportTo)

```
curl -XPOST http://ip:port/deletedMessages/users/userExportFrom@domain.ext?action=export&exportTo=userReceiving@domain.ext

BODY: is the json query has the same structure with Restore Deleted Messages section
```
**Note**: Json query passing into the body follows the same rules & restrictions like in [Restore Deleted Messages](#Restore_deleted_messages)

Response code:

 - 201: Task for exporting has been created
 - 400: Bad request: 
   - exportTo query param is not present
   - exportTo query param is not a valid mail address
   - action query param is not present
   - action query param is not a valid action
   - user parameter is invalid
   - can not parse the JSON body
   - Json query object contains unsupported operator, fieldName
   - Json query object values violate parsing rules 
 - 404: User not found

The scheduled task will have the following type `deletedMessages/export` and the following `additionalInformation`:

```
{
  "userExportFrom": "userToRestore@domain.ext",
  "exportTo": "userReceiving@domain.ext",
  "totalExportedMessages": 1432
}
```

while:
 - userExportFrom: export deleted messages from this user
 - exportTo: content of deleted messages have been shared to this mail address
 - totalExportedMessages: number of deleted messages match with json query, then being shared to sharee
 
### Purge Deleted Messages
 
You can overwrite 'retentionPeriod' configuration in 'deletedMessageVault' configuration file or use default value is 1 year.

Purge all deleted messages older than configured 'retentionPeriod'

```
curl -XDEL http://ip:port/deletedMessages?scope=expired
```

Response code:

 - 201: Task for purging has been created
 - 400: Bad request: 
   - action query param is not present
   - action query param is not a valid action

You may want to call this endpoint on a regular basis.

### Permanently Remove Deleted Message

Delete a Deleted Message with MessageId

```
curl -XDEL http://ip:port/deletedMessages/users/user@domain.ext/messages/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response code:

 - 201: Task for deleting message has been created
 - 400: Bad request: 
   - user parameter is invalid
   - messageId parameter is invalid
 - 404: User not found
 
 The scheduled task will have the following type `deletedMessages/delete` and the following `additionalInformation`:
 
 ```
 {
   "user": "user@domain.ext",
   "deleteMessageId": "3294a976-ce63-491e-bd52-1b6f465ed7a2"
 }
 ```
 
while:
 - user: delete deleted messages from this user
 - deleteMessageId: messageId of deleted messages will be delete

## Task management

Some webadmin features schedules tasks. The task management API allow to monitor and manage the execution of the following tasks.

Note that the `taskId` used in the following APIs is returned by other WebAdmin APIs scheduling tasks.

 - [Getting a task details](#Getting_a_task_details)
 - [Awaiting a task](#Awaiting_a_task)
 - [Cancelling a task](#Cancelling_a_task)
 - [Listing tasks](#Listing_tasks)

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

## Cassandra extra operations

Some webadmin features to manage some extra operations on Cassandra tables, like solving inconsistencies on projection tables.
Such inconsistencies can be for example created by a fail of the DAO to add a mapping into 'mappings_sources`, while it was successful
regarding the `rrt` table.

 - [Operations on mappings sources](#Operations_on_mappings_sources)

### Operations on mappings sources

You can do a series of action on `mappings_sources` projection table :

```
curl -XPOST /cassandra/mappings?action=[ACTION]
```

Will return the taskId corresponding to the related task. Actions supported so far are :

 - SolveInconsistencies : cleans up first all the mappings in `mappings_sources` index and then repopulate it correctly. In the meantime,
listing sources of a mapping might create temporary inconsistencies during the process.

For example :

```
curl -XPOST /cassandra/mappings?action=SolveInconsistencies
```

Response codes :

 - 201: the taskId of the created task
 - 400: Invalid action argument for performing operation on mappings data

