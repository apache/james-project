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

Finally, please note that in case of a malformed URL the 400 bad request response will contain an HTML body.

## Navigation menu

 - [HealthCheck](#HealthCheck)
 - [Administrating domains](#Administrating_domains)
 - [Administrating users](#Administrating_users)
 - [Administrating mailboxes](#Administrating_mailboxes)
 - [Administrating messages](#Administrating_Messages)
 - [Administrating user mailboxes](#Administrating_user_mailboxes)
 - [Administrating quotas by users](#Administrating_quotas_by_users)
 - [Administrating quotas by domains](#Administrating_quotas_by_domains)
 - [Administrating global quotas](#Administrating_global_quotas)
 - [Cassandra Schema upgrades](#Cassandra_Schema_upgrades)
 - [Correcting ghost mailbox](#Correcting_ghost_mailbox)
 - [Creating address aliases](#Creating_address_aliases)
 - [Creating domain mappings](#Creating_domain_mappings)
 - [Creating address forwards](#Creating_address_forwards)
 - [Creating address group](#Creating_address_group)
 - [Creating regex mapping](#Creating_regex_mapping)
 - [Address Mappings](#Address_Mappings)
 - [User Mappings](#User_Mappings)
 - [Administrating mail repositories](#Administrating_mail_repositories)
 - [Administrating mail queues](#Administrating_mail_queues)
 - [Sending email over webAdmin](#Sending_email_over_webAdmin)
 - [Administrating DLP Configuration](#Administrating_DLP_Configuration)
 - [Administrating Sieve quotas](#Administrating_Sieve_quotas)
 - [Running blob garbage collection](#Running_blob_garbage_collection)
 - [Administrating jmap uploads](#Administrating_jmap_uploads)
 - [Deleted Messages Vault](#Deleted_Messages_Vault)
 - [Task management](#Task_management)
 - [Cassandra extra operations](#Cassandra_extra_operations)
 - [Event Dead Letter](#Event_Dead_Letter)

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

Will return a list of healthChecks execution result, with an aggregated result:

```
{
  "status": "healthy",
  "checks": [
    {
      "componentName": "Cassandra backend",
      "escapedComponentName": "Cassandra%20backend",
      "status": "healthy"
      "cause": null
    }
  ]
}
```

**status** field can be:

 - **healthy**: Component works normally
 - **degraded**: Component works in degraded mode. Some non-critical services may not be working, or latencies are high, for example. Cause contains explanations.
 - **unhealthy**: The component is currently not working. Cause contains explanations.

Supported health checks include:

 - **Cassandra backend**: Cassandra storage. Included in Cassandra Guice based products.
 - **OpenSearch Backend**: OpenSearch storage. Included in Cassandra Guice based products.
 - **EventDeadLettersHealthCheck**: Included in all Guice products.
 - **Guice application lifecycle**: included in all Guice products.
 - **JPA Backend**: JPA storage. Included in JPA Guice based products.
 - **MailReceptionCheck** We rely on a configured user, send an email to him and
 assert that the email is well received, and can be read within the given configured
 period. Unhealthy means that the email could not be received before reacing the timeout.
 - **MessageFastViewProjection**: included in memory and Cassandra based Guice products. 
 Health check of the component storing JMAP properties which are fast to retrieve. 
 Those properties are computed in advance from messages and persisted in order to archive a better performance. 
 There are some latencies between a source update and its projections updates. 
 Incoherency problems arise when reads are performed in this time-window. 
 We piggyback the projection update on missed JMAP read in order to decrease the outdated time window for a given entry. 
 The health is determined by the ratio of missed projection reads. (lower than 10% causes `degraded`)

 - **RabbitMQ backend**: RabbitMQ messaging. Included in Distributed Guice based products.

Response codes:

 - 200: All checks have answered with a Healthy or Degraded status. James services can still be used.
 - 503: At least one check have answered with a Unhealthy status

### Check single component

Performs a health check for the given component. The component is referenced by its URL encoded name.

```
curl -XGET http://ip:port/healthcheck/checks/Cassandra%20backend
```

Will return the component's name, the component's escaped name, the health status and a cause.

```
{
  "componentName": "Cassandra backend",
  "escapedComponentName": "Cassandra%20backend",
  "status": "healthy"
  "cause": null
}
```

Response codes:

 - 200: The check has answered with a Healthy or Degraded status.
 - 404: A component with the given name was not found.
 - 503: The check has anwered with a Unhealthy status.
 
### List all health checks
 
This endpoint lists all the available health checks.
 
```
curl -XGET http://ip:port/healthcheck/checks
```
 
Will return the list of all available health checks.
 
```
[
    {
        "componentName": "Cassandra backend",
        "escapedComponentName": "Cassandra%20backend"
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
curl -XDELETE http://ip:port/domains/{domainToBeDeleted}
```

Note: Deletion of an auto-detected domain, default domain or of an auto-detected ip is not supported. We encourage you instead to review 
your [domain list configuration](https://james.apache.org/server/config-domainlist.html).

Response codes:

 - 204: The domain was successfully removed

### Test if a domain exists

```
curl -XGET http://ip:port/domains/{domainName}
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
["domain1", "domain2"]
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

When sending an email to an email address having `source1.domain.tld` or `source2.domain.tld` as a domain part (example: `user@source1.domain.tld`), then
the domain part will be rewritten into destination.domain.tld (so into `user@destination.domain.tld`).

Response codes:

 - 200: The domain aliases was successfully retrieved
 - 400: destination.domain.tld has an invalid syntax
 - 404: destination.domain.tld is not part of handled domains and does not have local domains as aliases.

### Create an alias for a domain

To create a domain alias execute the following query:

```
curl -XPUT http://ip:port/domains/destination.domain.tld/aliases/source.domain.tld
```

When sending an email to an email address having `source.domain.tld` as a domain part (example: `user@source.domain.tld`), then
the domain part will be rewritten into `destination.domain.tld` (so into `user@destination.domain.tld`).


Response codes:

 - 204: The redirection now exists
 - 400: `source.domain.tld` or `destination.domain.tld` have an invalid syntax
 - 400: `source, domain` and `destination domain` are the same
 - 404: `source.domain.tld` are not part of handled domains.

Be aware that no checks to find possible loops that would result of this creation will be performed.

### Delete an alias for a domain


To delete a domain alias execute the following query:

```
curl -XDELETE http://ip:port/domains/destination.domain.tld/aliases/source.domain.tld
```

When sending an email to an email address having `source.domain.tld` as a domain part (example: `user@source.domain.tld`), then
the domain part will be rewritten into `destination.domain.tld` (so into `user@destination.domain.tld`).


Response codes:

 - 204: The redirection now no longer exists
 - 400: `source.domain.tld` or destination.domain.tld have an invalid syntax
 - 400: source, domain and destination domain are the same
 - 404: `source.domain.tld` are not part of handled domains.

## Administrating users

   - [Create a user](#Create_a_user)
   - [Updating a user password](#Updating_a_user_password)
   - [Testing a user existence](#Testing_a_user_existence)
   - [Deleting a user](#Deleting_a_user)
   - [Retrieving the user list](#Retrieving_the_user_list)
   - [Retrieving the list of allowed `From` headers for a given user](Retrieving_the_list_of_allowed_From_headers_for_a_given_user)

### Create a user

```
curl -XPUT http://ip:port/users/usernameToBeUsed \
  -d '{"password":"passwordToBeUsed"}' \ 
  -H "Content-Type: application/json"
```

Resource name usernameToBeUsed representing valid users, 
hence it should match the criteria at [User Repositories documentation](/server/config-users.html) 

Response codes:

 - 204: The user was successfully created
 - 400: The user name or the payload is invalid
 - 409: The user name already exists

Note: If the user exists already, its password cannot be updated using this. 
If you want to update a user's password, please have a look at [Update a user password](#Updating_a_user_password).

### Updating a user password

```
curl -XPUT http://ip:port/users/usernameToBeUsed?force \
  -d '{"password":"passwordToBeUsed"}' \ 
  -H "Content-Type: application/json"
```

Response codes:

- 204: The user's password was successfully updated
- 400: The user name or the payload is invalid

This also can be used to create a new user.

### Testing a user existence

```
curl -XHEAD http://ip:port/users/usernameToBeUsed
```

Resource name "usernameToBeUsed" represents a valid user,
hence it should match the criteria at [User Repositories documentation](/server/config-users.html) 

Response codes:

 - 200: The user exists
 - 400: The user name is invalid
 - 404: The user does not exist

### Updating a user password

Same than Create, but a user need to exist.

If the user do not exist, then it will be created.

### Deleting a user

```
curl -XDELETE http://ip:port/users/{userToBeDeleted}
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

### Retrieving the list of allowed `From` headers for a given user

```
curl -XGET http://ip:port/users/givenUser/allowedFromHeaders
```

The answer looks like:

```
["user@domain.tld","alias@domain.tld"]
```

Response codes:

 - 200: The list was successfully retrieved
 - 400: The user is invalid
 - 404: The user is unknown

### Add a delegated user of a base user

```
curl -XPUT http://ip:port/users/baseUser/authorizedUsers/delegatedUser
```

Response codes:

- 200: Addition of the delegated user succeeded
- 404: The base user does not exist
- 400: The delegated user does not exist

Note: Delegation is only available on top of Cassandra products and not implemented yet on top of JPA backends.

### Remove a delegated user of a base user

```
curl -XDELETE http://ip:port/users/baseUser/authorizedUsers/delegatedUser
```

Response codes:

- 200: Removal of the delegated user succeeded
- 404: The base user does not exist
- 400: The delegated user does not exist

Note: Delegation is only available on top of Cassandra products and not implemented yet on top of JPA backends.

### Retrieving the list of delegated users of a base user

```
curl -XGET http://ip:port/users/baseUser/authorizedUsers
```

The answer looks like:

```
["alice@domain.tld","bob@domain.tld"]
```

Response codes:

- 200: The list was successfully retrieved
- 404: The base user does not exist

Note: Delegation is only available on top of Cassandra products and not implemented yet on top of JPA backends.

### Remove all delegated users of a base user

```
curl -XDELETE http://ip:port/users/baseUser/authorizedUsers
```

Response codes:

- 200: Removal of the delegated users succeeded
- 404: The base user does not exist

Note: Delegation is only available on top of Cassandra products and not implemented yet on top of JPA backends.


### Change a username

```
curl -XPOST http://ip:port/users/oldUser/rename/newUser?action=rename
```

Would migrate account data from `oldUser` to `newUser`.

[More details about endpoints returning a task](#_endpoints_returning_a_task).

Implemented migration steps are:

 - `ForwardUsernameChangeTaskStep`: creates forward from old user to new user and migrates existing forwards
 - `FilterUsernameChangeTaskStep`: migrates users filtering rules
 - `DelegationUsernameChangeTaskStep`: migrates delegations where the impacted user is either delegatee or delegator

Response codes:

* 201: Success. Corresponding task id is returned.
* 400: Error in the request. Details can be found in the reported error.

The `fromStep` query parameter allows skipping previous steps, allowing to resume the username change from a failed step.

The scheduled task will have the following type `UsernameChangeTask` and the following `additionalInformation`:

```
{
        "type": "UsernameChangeTask",
        "oldUser": "jessy.jones@domain.tld",
        "newUser": "jessy.smith@domain.tld",
        "status": {
            "A": "DONE",
            "B": "FAILED",
            "C": "ABORTED"
        },
        "fromStep": null,
        "timestamp": "2023-02-17T02:54:01.246477Z"
}
```

Valid status includes:

 - `SKIPPED`: bypassed via `fromStep` setting
 - `WAITING`: Awaits execution
 - `IN_PROGRESS`: Currently executed
 - `FAILED`: Error encountered while executing this step. Check the logs.
 - `ABORTED`: Won't be executed because of previous step failures.

## Administrating mailboxes

### All mailboxes

Several actions can be performed on the server mailboxes.

Request pattern is:

```
curl -XPOST /mailboxes?action={action1},...
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The kind of task scheduled depends on the action parameter. See below for details.

#### Fixing mailboxes inconsistencies

This task is only available on top of Guice Cassandra products.

```
curl -XPOST /mailboxes?task=SolveInconsistencies
```

Will schedule a task for fixing inconsistencies for the mailbox deduplicated object stored in Cassandra.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

The `I-KNOW-WHAT-I-M-DOING` header is mandatory (you can read more information about it in the warning section below).

The scheduled task will have the following type `solve-mailbox-inconsistencies` and the following `additionalInformation`:

```
{
  "type":"solve-mailbox-inconsistencies",
  "processedMailboxEntries": 3,
  "processedMailboxPathEntries": 3,
  "fixedInconsistencies": 2,
  "errors": 1,
  "conflictingEntries":[{
    "mailboxDaoEntry":{
      "mailboxPath":"#private:user:mailboxName",
      "mailboxId":"464765a0-e4e7-11e4-aba4-710c1de3782b"
    }," +
    "mailboxPathDaoEntry":{
      "mailboxPath":"#private:user:mailboxName2",
      "mailboxId":"464765a0-e4e7-11e4-aba4-710c1de3782b"
    }
  }]
}
```

Note that conflicting entry inconsistencies will not be fixed and will require to explicitly use 
[ghost mailbox](#correcting-ghost-mailbox) endpoint in order to merge the conflicting mailboxes and prevent any message
loss.

**WARNING**: this task can cancel concurrently running legitimate user operations upon dirty read. As such this task 
should be run offline. 

A dirty read is when data is read between the two writes of the denormalization operations (no isolation).

In order to ensure being offline, stop the traffic on SMTP, JMAP and IMAP ports, for example via re-configuration or 
firewall rules.

Due to all of those risks, a `I-KNOW-WHAT-I-M-DOING` header should be positioned to `ALL-SERVICES-ARE-OFFLINE` in order 
to prevent accidental calls.

#### Recomputing mailbox counters

This task is only available on top of Guice Cassandra products.

```
curl -XPOST /mailboxes?task=RecomputeMailboxCounters
```

Will recompute counters (unseen & total count) for the mailbox object stored in Cassandra.

Cassandra maintains a per mailbox projection for message count and unseen message count. As with any projection, it can 
go out of sync, leading to inconsistent results being returned to the client.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

The scheduled task will have the following type `recompute-mailbox-counters` and the following `additionalInformation`:

```
{
  "type":"recompute-mailbox-counters",
  "processedMailboxes": 3,
  "failedMailboxes": ["464765a0-e4e7-11e4-aba4-710c1de3782b"]
}
```

Note that conflicting inconsistencies entries will not be fixed and will require to explicitly use 
[ghost mailbox](#correcting-ghost-mailbox) endpoint in order to merge the conflicting mailboxes and prevent any message
loss.

**WARNING**: this task do not take into account concurrent modifications upon a single mailbox counter recomputation. 
Rerunning the task will *eventually* provide the consistent result. As such we advise to run this task offline. 

In order to ensure being offline, stop the traffic on SMTP, JMAP and IMAP ports, for example via re-configuration or 
firewall rules.

`trustMessageProjection` query parameter can be set to `true`. Content of `messageIdTable` (listing messages by their 
mailbox context) table will be trusted and not compared against content of `imapUidTable` table (listing messages by their
messageId mailbox independent identifier). This will result in a better performance running the
task at the cost of safety in the face of message denormalization inconsistencies. 

Defaults to false, which generates 
additional checks. You can read 
[this ADR](https://github.com/apache/james-project/blob/master/src/adr/0022-cassandra-message-inconsistency.md) to 
better understand the message projection and how it can become inconsistent. 

#### Recomputing Global JMAP fast message view projection

This action is only available for backends supporting JMAP protocol.

Message fast view projection stores message properties expected to be fast to fetch but are actually expensive to compute,
in order for GetMessages operation to be fast to execute for these properties.

These projection items are asynchronously computed on mailbox events.

You can force the full projection recomputation by calling the following endpoint:

```
curl -XPOST /mailboxes?task=recomputeFastViewProjectionItems
```

Will schedule a task for recomputing the fast message view projection for all mailboxes.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed, per second. Defaults to 10.
 
This optional parameter must have a strictly positive integer as a value and be passed as query parameters.

Example:

```
curl -XPOST /mailboxes?task=recomputeFastViewProjectionItems&messagesPerSecond=20
```

The scheduled task will have the following type `RecomputeAllFastViewProjectionItemsTask` and the following `additionalInformation`:

```
{
  "type":"RecomputeAllPreviewsTask",
  "processedUserCount": 3,
  "processedMessageCount": 3,
  "failedUserCount": 2,
  "failedMessageCount": 1,
  "runningOptions": {
    "messagesPerSecond":20
  }
}
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

#### ReIndexing action

These tasks are only available on top of Guice Cassandra products or Guice JPA products. They are not part of Memory
Guice product. 

Be also aware of the limits of this API:

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

The following actions can be performed:

 - [ReIndexing all mails](#ReIndexing_all_mails)
 - [Fixing previously failed ReIndexing](#Fixing_previously_failed_ReIndexing)

##### ReIndexing all mails

```
curl -XPOST http://ip:port/mailboxes?task=reIndex
```

Will schedule a task for reIndexing all the mails stored on this James server.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed per second. Default is 50.

This optional parameter must have a strictly positive integer as a value and be passed as query parameter.

An admin can also specify the reindexing mode it wants to use when running the task:

 - `mode` the reindexing mode used. There are 2 modes for the moment:
   - `rebuildAll` allows to rebuild all indexes. It drops indexed entries prior reindexing. This is the default mode.
   - `rebuildAllNoCleanup` allows to rebuild all indexes. It skips the cleanup phase thus will not remove evicted entries
   upon reindex. However it yields better performances on a known to be empty index.
   - `fixOutdated` will check for outdated indexed document and reindex only those.
   
This optional parameter must be passed as query parameter. 

It's good to note as well that there is a limitation with the `fixOutdated` mode. As we first collect metadata of 
stored messages to compare them with the ones in the index, a failed `expunged` operation might not be well corrected
(as the message might not exist anymore but still be indexed).

Example:

`curl -XPOST http://ip:port/mailboxes?task=reIndex&messagesPerSecond=200&mode=rebuildAll`

The scheduled task will have the following type `full-reindexing` and the following `additionalInformation`:

```
{
  "type":"full-reindexing",
  "runningOptions":{
    "messagesPerSecond":200,
    "mode":"REBUILD_ALL"
  },
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "mailboxFailures": ["12", "23" ],
  "messageFailures": [
   {
     "mailboxId": "1",
      "uids": [1, 36]
   }]
}
```

##### Fixing previously failed ReIndexing

Will schedule a task for reIndexing all the mails which had failed to be indexed from the ReIndexingAllMails task.

Given `bbdb69c9-082a-44b0-a85a-6e33e74287a5` being a `taskId` generated for a reIndexing tasks

```
curl -XPOST 'http://ip:port/mailboxes?task=reIndex&reIndexFailedMessagesOf=bbdb69c9-082a-44b0-a85a-6e33e74287a5'
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed per second. Default is 50.

This optional parameter must have a strictly positive integer as a value and be passed as query parameter.

An admin can also specify the reindexing mode it wants to use when running the task:

 - `mode` the reindexing mode used. There are 2 modes for the moment:
   - `rebuildAll` allows to rebuild all indexes. It drops indexed entries prior reindexing. This is the default mode.
   - `rebuildAllNoCleanup` allows to rebuild all indexes. It skips the cleanup phase thus will not remove evicted entries
   upon reindex. However it yields better performances on a known to be empty index.
   - `fixOutdated` will check for outdated indexed document and reindex only those.
   
This optional parameter must be passed as query parameter.

It's good to note as well that there is a limitation with the `fixOutdated` mode. As we first collect metadata of 
stored messages to compare them with the ones in the index, a failed `expunged` operation might not be well corrected
(as the message might not exist anymore but still be indexed).

Example:

```
curl -XPOST http://ip:port/mailboxes?task=reIndex&reIndexFailedMessagesOf=bbdb69c9-082a-44b0-a85a-6e33e74287a5&messagesPerSecond=200&mode=rebuildAll
```

The scheduled task will have the following type `error-recovery-indexation` and the following `additionalInformation`:

```
{
  "type":"error-recovery-indexation"
  "runningOptions":{
    "messagesPerSecond":200,
    "mode":"REBUILD_ALL"
  },
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "mailboxFailures": ["12", "23" ],
  "messageFailures": [{
     "mailboxId": "1",
      "uids": [1, 36]
   }]
}
```

##### Create missing parent mailboxes

Will schedule a task for creating all the missing parent mailboxes in a hierarchical mailbox tree, which is the result 
of a partially failed rename operation of a child mailbox. 

```
curl -XPOST 'http://ip:port/mailboxes?task=createMissingParents'
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `createMissingParents` and the following `additionalInformation`:

```
{
  "type":"createMissingParents"    
  "created": ["1", "2" ],
  "totalCreated": 2,
  "failures": [],
  "totalFailure": 0
}
```

### Single mailbox

#### ReIndexing a mailbox mails

This task is only available on top of Guice Cassandra products or Guice JPA products. It is not part of Memory
Guice product. 

```
curl -XPOST http://ip:port/mailboxes/{mailboxId}?task=reIndex
```

Will schedule a task for reIndexing all the mails in one mailbox.

Note that 'mailboxId' path parameter needs to be a (implementation dependent) valid mailboxId.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed per second. Default is 50.

This optional parameter must have a strictly positive integer as a value and be passed as query parameter.

An admin can also specify the reindexing mode it wants to use when running the task:

 - `mode` the reindexing mode used. There are 2 modes for the moment:
   - `rebuildAll` allows to rebuild all indexes. It drops indexed entries prior reindexing. This is the default mode.
   - `rebuildAllNoCleanup` allows to rebuild all indexes. It skips the cleanup phase thus will not remove evicted entries
   upon reindex. However it yields better performances on a known to be empty index.
   - `fixOutdated` will check for outdated indexed document and reindex only those.
   
This optional parameter must be passed as query parameter.

It's good to note as well that there is a limitation with the `fixOutdated` mode. As we first collect metadata of 
stored messages to compare them with the ones in the index, a failed `expunged` operation might not be well corrected
(as the message might not exist anymore but still be indexed).

Example:

```
curl -XPOST http://ip:port/mailboxes/{mailboxId}?task=reIndex&messagesPerSecond=200&mode=fixOutdated
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `mailbox-reindexing` and the following `additionalInformation`:

```
{
  "type":"mailbox-reindexing",
  "runningOptions":{
    "messagesPerSecond":200,
    "mode":"FIX_OUTDATED"
  },   
  "mailboxId":"{mailboxId}",
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "mailboxFailures": ["12"],
  "messageFailures": [
   {
     "mailboxId": "1",
      "uids": [1, 36]
   }]
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

#### ReIndexing a single mail

This task is only available on top of Guice Cassandra products or Guice JPA products. It is not part of Memory
Guice product.

```
curl -XPOST http://ip:port/mailboxes/{mailboxId}/uid/{uid}?task=reIndex
```

Will schedule a task for reIndexing a single email.

Note that 'mailboxId' path parameter needs to be a (implementation dependent) valid mailboxId.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `message-reindexing` and the following `additionalInformation`:

```
{
  "mailboxId":"{mailboxId}",
  "uid":18
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

## Administrating Messages

### ReIndexing a single mail by messageId

This task is only available on top of Guice Cassandra products or Guice JPA products. It is not part of Memory
Guice product.

```
curl -XPOST http://ip:port/messages/{messageId}?task=reIndex
```

Will schedule a task for reIndexing a single email in all the mailboxes containing it.

Note that 'messageId' path parameter needs to be a (implementation dependent) valid messageId.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `messageId-reindexing` and the following `additionalInformation`:

```
{
  "messageId":"18"
}
```

Warning: During the re-indexing, the result of search operations might be altered.

### Fixing message inconsistencies

This task is only available on top of Guice Cassandra products.

```
curl -XPOST /messages?task=SolveInconsistencies
```

Will schedule a task for fixing message inconsistencies created by the message denormalization process. 

Messages are denormalized and stored in separated data tables in Cassandra, so they can be accessed 
by their unique identifier or mailbox identifier & local mailbox identifier through different protocols. 

Failure in the denormalization process will lead to inconsistencies, for example:

```
BOB receives a message
The denormalization process fails
BOB can read the message via JMAP
BOB cannot read the message via IMAP

BOB marks a message as SEEN
The denormalization process fails
The message is SEEN via JMAP
The message is UNSEEN via IMAP
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate of messages to be processed per second. Default is 100.

This optional parameter must have a strictly positive integer as a value and be passed as query parameter.

An admin can also specify the reindexing mode it wants to use when running the task:

 - `mode` the reindexing mode used. There are 2 modes for the moment:
   - `rebuildAll` allows to rebuild all indexes. It drops indexed entries prior reindexing. This is the default mode.
   - `rebuildAllNoCleanup` allows to rebuild all indexes. It skips the cleanup phase thus will not remove evicted entries
   upon reindex. However it yields better performances on a known to be empty index.
   - `fixOutdated` will check for outdated indexed document and reindex only those.
   
This optional parameter must be passed as query parameter.

It's good to note as well that there is a limitation with the `fixOutdated` mode. As we first collect metadata of 
stored messages to compare them with the ones in the index, a failed `expunged` operation might not be well corrected
(as the message might not exist anymore but still be indexed).

Example:

```
curl -XPOST /messages?task=SolveInconsistencies&messagesPerSecond=200&mode=rebuildAll
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `solve-message-inconsistencies` and the following `additionalInformation`:

```
{
  "type":"solve-message-inconsistencies",
  "timestamp":"2007-12-03T10:15:30Z",
  "processedImapUidEntries": 2,
  "processedMessageIdEntries": 1,
  "addedMessageIdEntries": 1,
  "updatedMessageIdEntries": 0,
  "removedMessageIdEntries": 1,
  "runningOptions":{
    "messagesPerSecond": 200,
    "mode":"REBUILD_ALL"
  },
  "fixedInconsistencies": [
    {
      "mailboxId": "551f0580-82fb-11ea-970e-f9c83d4cf8c2",
      "messageId": "d2bee791-7e63-11ea-883c-95b84008f979",
      "uid": 1
    },
    {
      "mailboxId": "551f0580-82fb-11ea-970e-f9c83d4cf8c2",
      "messageId": "d2bee792-7e63-11ea-883c-95b84008f979",
      "uid": 2
    }
  ],
  "errors": [
    {
      "mailboxId": "551f0580-82fb-11ea-970e-f9c83d4cf8c2",
      "messageId": "ffffffff-7e63-11ea-883c-95b84008f979",
      "uid": 3
    }
  ]
}
```

User actions concurrent to the inconsistency fixing task could result in concurrency issues. New inconsistencies 
could be created. 

However the source of truth will not be impacted, hence rerunning the task will eventually fix all issues.

This task could be run safely online and can be scheduled on a recurring basis outside of peak traffic 
by an admin to ensure Cassandra message consistency.

## Administrating user mailboxes

 - [Creating a mailbox](#Creating_a_mailbox)
 - [Deleting a mailbox and its children](#Deleting_a_mailbox_and_its_children)
 - [Testing existence of a mailbox](#Testing_existence_of_a_mailbox)
 - [Listing user mailboxes](#Listing_user_mailboxes)
 - [Deleting user mailboxes](#Deleting_user_mailboxes)
 - [Exporting user mailboxes](#Exporting_user_mailboxes)
 - [ReIndexing a user mails](#ReIndexing_a_user_mails)
 - [Recomputing User JMAP fast message view projection](#Recomputing_User_JMAP_fast_message_view_projection)
 - [Counting emails](#Counting_emails)
 - [Counting unseen emails](#Couting_unseen_emails)
 - [Clearing mailbox content][#Clearing_mailbox_content]   
### Creating a mailbox

```
curl -XPUT http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxNameToBeCreated}
```

Resource name `usernameToBeUsed` should be an existing user
Resource name `mailboxNameToBeCreated` should not be empty, nor contain `% *` characters, nor starting with `#`.

Response codes:

 - 204: The mailbox now exists on the server
 - 400: Invalid mailbox name
 - 404: The user name does not exist

 To create nested mailboxes, for instance a work mailbox inside the INBOX mailbox, people should use the . separator. The sample query is:

```
curl -XDELETE http://ip:port/users/{usernameToBeUsed}/mailboxes/INBOX.work
```

### Deleting a mailbox and its children

```
curl -XDELETE http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxNameToBeDeleted}
```

Resource name `usernameToBeUsed` should be an existing user
Resource name `mailboxNameToBeDeleted` should not be empty

Response codes:

 - 204: The mailbox now does not exist on the server
 - 400: Invalid mailbox name
 - 404: The user name does not exist

### Testing existence of a mailbox

```
curl -XGET http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxNameToBeTested}
```

Resource name `usernameToBeUsed` should be an existing user
Resource name `mailboxNameToBeTested` should not be empty

Response codes:

 - 204: The mailbox exists
 - 400: Invalid mailbox name
 - 404: The user name does not exist, the mailbox does not exist

### Listing user mailboxes

```
curl -XGET http://ip:port/users/{usernameToBeUsed}/mailboxes
```

The answer looks like:

```
[{"mailboxName":"INBOX"},{"mailboxName":"outbox"}]
```

Resource name `usernameToBeUsed` should be an existing user

Response codes:

 - 200: The mailboxes list was successfully retrieved
 - 404: The user name does not exist

### Deleting user mailboxes

```
curl -XDELETE http://ip:port/users/{usernameToBeUsed}/mailboxes
```

Resource name `usernameToBeUsed` should be an existing user

Response codes:

 - 204: The user do not have mailboxes anymore
 - 404: The user name does not exist

### Exporting user mailboxes

```
curl -XPOST http://ip:port/users/{usernameToBeUsed}/mailboxes?action=export
```

Resource name `usernameToBeUsed` should be an existing user

Response codes:

 - 201: Success. Corresponding task id is returned
 - 404: The user name does not exist

The scheduled task will have the following type `MailboxesExportTask` and the following `additionalInformation`:

```
{
  "type":"MailboxesExportTask",
  "timestamp":"2007-12-03T10:15:30Z",
  "username": "user",
  "stage": "STARTING"
}
```

### ReIndexing a user mails
 
```
curl -XPOST http://ip:port/users/{usernameToBeUsed}/mailboxes?task=reIndex
```

Will schedule a task for reIndexing all the mails in "user@domain.com" mailboxes (encoded above).
 
[More details about endpoints returning a task](#Endpoints_returning_a_task).
 
An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed per second. Default is 50.

This optional parameter must have a strictly positive integer as a value and be passed as query parameter.

An admin can also specify the reindexing mode it wants to use when running the task:

 - `mode` the reindexing mode used. There are 2 modes for the moment:
   - `rebuildAll` allows to rebuild all indexes. It drops indexed entries prior reindexing. This is the default mode.
   - `rebuildAllNoCleanup` allows to rebuild all indexes. It skips the cleanup phase thus will not remove evicted entries
   upon reindex. However it yields better performances on a known to be empty index.
   - `fixOutdated` will check for outdated indexed document and reindex only those.
   
This optional parameter must be passed as query parameter.

It's good to note as well that there is a limitation with the `fixOutdated` mode. As we first collect metadata of 
stored messages to compare them with the ones in the index, a failed `expunged` operation might not be well corrected
(as the message might not exist anymore but still be indexed).

Example:

```
curl -XPOST http://ip:port/users/{usernameToBeUsed}/mailboxes?task=reIndex&messagesPerSecond=200&mode=fixOutdated
```

Response codes:
 
 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.

The scheduled task will have the following type `user-reindexing` and the following `additionalInformation`:

```
{
  "type":"user-reindexing",
  "runningOptions":{
    "messagesPerSecond":200,
    "mode":"FIX_OUTDATED"
  }, 
  "user":"user@domain.com",
  "successfullyReprocessedMailCount":18,
  "failedReprocessedMailCount": 3,
  "mailboxFailures": ["12", "23" ],
  "messageFailures": [
   {
     "mailboxId": "1",
      "uids": [1, 36]
   }]
}
```

Warning: During the re-indexing, the result of search operations might be altered.

Warning: Canceling this task should be considered unsafe as it will leave the currently reIndexed mailbox as partially indexed.

Warning: While we have been trying to reduce the inconsistency window to a maximum (by keeping track of ongoing events),
concurrent changes done during the reIndexing might be ignored.

### Counting emails

```bash
curl -XGET http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxName}/messageCount
```

Will return the total count of messages within the mailbox of that user.

Resource name `usernameToBeUsed` should be an existing user.    
Resource name `mailboxName` should not be empty, nor contain `% *` characters, nor starting with `#`.

Response codes:

- 200: The number of emails in a given mailbox
- 400: Invalid mailbox name
- 404: Invalid get on user mailboxes. The `usernameToBeUsed` or `mailboxName` does not exit'

### Counting unseen emails

```bash
curl -XGET http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxName}/unseenMessageCount
```

Will return the total count of unseen messages within the mailbox of that user.

Resource name `usernameToBeUsed` should be an existing user.    
Resource name `mailboxName` should not be empty, nor contain `% *` characters, nor starting with `#`.

Response codes:

- 200: The number of unseen emails in a given mailbox
- 400: Invalid mailbox name
- 404: Invalid get on user mailboxes. The `usernameToBeUsed` or `mailboxName` does not exit'


### Clearing mailbox content

```
curl -XDELETE http://ip:port/users/{usernameToBeUsed}/mailboxes/{mailboxName}/messages
```

Will schedule a task for clearing all the mails in `mailboxName` mailbox of `usernameToBeUsed`.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Resource name `usernameToBeUsed` should be an existing user.

Resource name `mailboxName` should not be empty, nor contain `% *` characters, nor starting with `#`.

Response codes:

- 201: Success. Corresponding task id is returned.
- 400: Invalid mailbox name
- 404: Invalid get on user mailboxes. The `username` or `mailboxName` does not exit

The scheduled task will have the following type `ClearMailboxContentTask` and
the following `additionalInformation`:

```
{
    "mailboxName": "mbx1",
    "messagesFailCount": 9,
    "messagesSuccessCount": 10,
    "timestamp": "2007-12-03T10:15:30Z",
    "type": "ClearMailboxContentTask",
    "username": "bob@domain.tld"
}
```

### Subscribing a user to all of its mailboxes
 
```
curl -XPOST http://ip:port/users/{usernameToBeUsed}/mailboxes?task=subscribeAll
```

Will schedule a task for subscribing a user to all of its mailboxes.
 
[More details about endpoints returning a task](#Endpoints_returning_a_task).

Most users are unaware of what an IMAP subscription is, nor how they can manage it. If the subscription list gets out
of sync with the mailbox list, it could result in downgraded user experience (see MAILBOX-405). This task allow
to reset the subscription list to the mailbox list on a per user basis thus working around the aforementioned issues.

Response codes:
 
 - 201: Success. Corresponding task id is returned.
 - 404: No such user

The scheduled task will have the following type `SubscribeAllTask` and the following `additionalInformation`:

```
{
  "type":"SubscribeAllTask",
  "username":"user@domain.com",
  "subscribedCount":18,
  "unsubscribedCount": 3
}
```

### Recomputing User JMAP fast message view projection

This action is only available for backends supporting JMAP protocol.

Message fast view projection stores message properties expected to be fast to fetch but are actually expensive to compute,
in order for GetMessages operation to be fast to execute for these properties.

These projection items are asynchronously computed on mailbox events.

You can force the full projection recomputation by calling the following endpoint:

```
curl -XPOST /users/{usernameToBeUsed}/mailboxes?task=recomputeFastViewProjectionItems
```

Will schedule a task for recomputing the fast message view projection for all mailboxes of `usernameToBeUsed`.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `messagesPerSecond` rate at which messages should be processed, per second. Defaults to 10.
 
This optional parameter must have a strictly positive integer as a value and be passed as query parameters.

Example:

```
curl -XPOST /mailboxes?task=recomputeFastViewProjectionItems&messagesPerSecond=20
```

The scheduled task will have the following type `RecomputeUserFastViewProjectionItemsTask` and the following `additionalInformation`:

```
{
  "type":"RecomputeUserFastViewProjectionItemsTask",
  "username": "{usernameToBeUsed}",
  "processedMessageCount": 3,
  "failedMessageCount": 1,
  "runningOptions": {
    "messagesPerSecond":20
  }
}
```

Response codes:

 - 201: Success. Corresponding task id is returned.
 - 400: Error in the request. Details can be found in the reported error.
 - 404: User not found.

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
 - [Recomputing current quotas for users](#Recomputing_current_quotas_for_users)

### Getting the quota for a user

```
curl -XGET http://ip:port/quota/users/{usernameToBeUsed}
```

Resource name `usernameToBeUsed` should be an existing user

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
    "size":13,
    "count":21,
    "ratio": {
      "size":0.25,
      "count":0.5,
      "max":0.5
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
curl -XPUT http://ip:port/quota/users/{usernameToBeUsed}
```

Resource name `usernameToBeUsed` should be an existing user

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

### Getting the quota count for a user

```
curl -XGET http://ip:port/quota/users/{usernameToBeUsed}/count
```

Resource name `usernameToBeUsed` should be an existing user

The answer looks like:

```
52
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 204: No quota count limit is defined at the user level for this user
 - 404: The user does not exist

### Updating the quota count for a user

```
curl -XPUT http://ip:port/quota/users/{usernameToBeUsed}/count
```

Resource name `usernameToBeUsed` should be an existing user

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist

### Deleting the quota count for a user

```
curl -XDELETE http://ip:port/quota/users/{usernameToBeUsed}/count
```

Resource name `usernameToBeUsed` should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 404: The user does not exist

### Getting the quota size for a user

```
curl -XGET http://ip:port/quota/users/{usernameToBeUsed}/size
```

Resource name `usernameToBeUsed` should be an existing user

The answer looks like:

```
52
```

Response codes:

 - 200: The user's quota was successfully retrieved
 - 204: No quota size limit is defined at the user level for this user
 - 404: The user does not exist

### Updating the quota size for a user

```
curl -XPUT http://ip:port/quota/users/{usernameToBeUsed}/size
```

Resource name `usernameToBeUsed` should be an existing user

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The user does not exist

### Deleting the quota size for a user

```
curl -XDELETE http://ip:port/quota/users/{usernameToBeUsed}/size
```

Resource name `usernameToBeUsed` should be an existing user

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 404: The user does not exist

### Searching user by quota ratio

```
curl -XGET 'http://ip:port/quota/users?minOccupationRatio=0.8&maxOccupationRatio=0.99&limit=100&offset=200&domain=domain.com'
```

Will return:

```
[
  {
    "username":"user@domain.com",
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
        "size":48,
        "count":21,
        "ratio": {
          "size":0.9230,
          "count":0.5,
          "max":0.9230
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
 
### Recomputing current quotas for users

This task is available on top of Cassandra & JPA products.

```
curl -XPOST /quota/users?task=RecomputeCurrentQuotas
```

Will recompute current quotas (count and size) for all users stored in James.

James maintains per quota a projection for current quota count and size. As with any projection, it can 
go out of sync, leading to inconsistent results being returned to the client.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

An admin can specify the concurrency that should be used when running the task:

 - `usersPerSecond` rate at which users quotas should be reprocessed, per second. Defaults to 1.
 
This optional parameter must have a strictly positive integer as a value and be passed as query parameters.

Example:

```
curl -XPOST /quota/users?task=RecomputeCurrentQuotas&usersPerSecond=20
```

The scheduled task will have the following type `recompute-current-quotas` and the following `additionalInformation`:

```
{
  "type":"recompute-current-quotas",
  "processedQuotaRoots": 3,
  "failedQuotaRoots": ["#private&bob@localhost"],
  "runningOptions": {
    "usersPerSecond":20
  }
}
```

**WARNING**: this task do not take into account concurrent modifications upon a single current quota recomputation. 
Rerunning the task will *eventually* provide the consistent result.

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
curl -XGET http://ip:port/quota/domains/{domainToBeUsed}
```

Resource name `domainToBeUsed` should be an existing domain. For example:

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
curl -XPUT http://ip:port/quota/domains/{domainToBeUsed}
```

Resource name `domainToBeUsed` should be an existing domain.

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

### Getting the quota count for a domain

```
curl -XGET http://ip:port/quota/domains/{domainToBeUsed}/count
```

Resource name `domainToBeUsed` should be an existing domain.

The answer looks like:

```
52
```

Response codes:

 - 200: The domain's quota was successfully retrieved
 - 204: No quota count limit is defined at the domain level for this domain
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Updating the quota count for a domain

```
curl -XPUT http://ip:port/quota/domains/{domainToBeUsed}/count
```

Resource name `domainToBeUsed` should be an existing domain.

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Deleting the quota count for a domain

```
curl -XDELETE http://ip:port/quota/domains/{domainToBeUsed}/count
```

Resource name `domainToBeUsed` should be an existing domain.

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Getting the quota size for a domain

```
curl -XGET http://ip:port/quota/domains/{domainToBeUsed}/size
```

Resource name `domainToBeUsed` should be an existing domain.

The answer looks like:

```
52
```

Response codes:

 - 200: The domain's quota was successfully retrieved
 - 204: No quota size limit is defined at the domain level for this domain
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Updating the quota size for a domain

```
curl -XPUT http://ip:port/quota/domains/{domainToBeUsed}/size
```

Resource name `domainToBeUsed` should be an existing domain.

The body can contain a fixed value or an unlimited value (-1):

```
52
```

Response codes:

 - 204: The quota has been updated
 - 400: The body is not a positive integer neither an unlimited value (-1).
 - 404: The domain does not exist
 - 405: Domain Quota configuration not supported when virtual hosting is desactivated.

### Deleting the quota size for a domain

```
curl -XDELETE http://ip:port/quota/domains/{domainToBeUsed}/size
```

Resource name `domainToBeUsed` should be an existing domain.

Response codes:

 - 204: The quota has been updated to unlimited value.
 - 404: The domain does not exist

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
curl -XGET http://ip:port/quota
```

The answer is the details of the global quota.

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
 - 204: No quota count limit is defined at the global level

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
 - 204: No quota size limit is defined at the global level

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

### Deleting the global quota size

```
curl -XDELETE http://ip:port/quota/size
```

Response codes:

 - 204: The quota has been updated to unlimited value.

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

 - [Retrieving current Cassandra schema version](#Retrieving_current_Cassandra_schema_version)
 - [Retrieving latest available Cassandra schema version](#Retrieving_latest_available_Cassandra_schema_version)
 - [Upgrading to a specific version](#Upgrading_to_a_specific_version)
 - [Upgrading to the latest version](#Upgrading_to_the_latest_version)

### Retrieving current Cassandra schema version

```
curl -XGET http://ip:port/cassandra/version
```

Will return:

```
{"version": 2}
```

Where the number corresponds to the current schema version of the database you are using.

Response codes:

 - 200: Success

### Retrieving latest available Cassandra schema version

```
curl -XGET http://ip:port/cassandra/version/latest
```

Will return:

```
{"version": 3}
```

Where the number corresponds to the latest available schema version of the database you are using. This means you can be
migrating to this schema version.

Response codes:

 - 200: Success

### Upgrading to a specific version

```
curl -XPOST -H "Content-Type: application/json http://ip:port/cassandra/version/upgrade -d '3'
```

Will schedule the run of the migrations you need to reach schema version 3.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 200: Success. The scheduled task `taskId` is returned.
 - 400: The version is invalid. The version should be a strictly positive number.
 - 410: Error while planning this migration. This resource is gone away. Reason is mentionned in the body.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.


The scheduled task will have the following type `cassandra-migration` and the following `additionalInformation`:

```
{"targetVersion":3}
```

### Upgrading to the latest version

```
curl -XPOST http://ip:port/cassandra/version/upgrade/latest
```

Will schedule the run of the migrations you need to reach the latest schema version.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 200: Success. The scheduled task `taskId` is returned.
 - 410: Error while planning this migration. This resource is gone away. Reason is mentionned in the body.

Note that several calls to this endpoint will be run in a sequential pattern.

If the server restarts during the migration, the migration is silently aborted.

The scheduled task will have the following type `cassandra-migration` and the following `additionalInformation`:

```
{"toVersion":2}
```

## Correcting ghost mailbox

This is a temporary workaround for the **Ghost mailbox** bug encountered using the Cassandra backend, as described in MAILBOX-322.

You can use the mailbox merging feature in order to merge the old "ghosted" mailbox with the new one.

```
curl -XPOST http://ip:port/cassandra/mailbox/merging \
  -d '{"mergeOrigin":"{id1}", "mergeDestination":"{id2}"}' \
  -H "Content-Type: application/json"
```

Will scedule a task for :

 - Delete references to `id1` mailbox
 - Move it's messages into `id2` mailbox
 - Union the rights of both mailboxes

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 400: Unable to parse the body.

The scheduled task will have the following type `mailbox-merging` and the following `additionalInformation`:

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
 - 409: The addition of the group member would lead to a loop and thus cannot be performed

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

 - [Listing Forwards](#Listing_Forwards)
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
  {"mailAddress":"destination1@domain.com"},
  {"mailAddress":"destination2@domain.com"}
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
 - 409: The creation of the forward would lead to a loop and thus cannot be performed

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
 - 400: Source and destination can't be the same!
 - 400: Domain in the destination or source is not managed by the DomainList
 - 409: The alias source exists as an user already
 - 409: The creation of the alias would lead to a loop and thus cannot be performed

### Removing an alias of an user

```
curl -XDELETE http://ip:port/address/aliases/user@domain.com/sources/alias@domain.com
```

Will remove alias@domain.com from user@domain.com, removing the alias if needed

Response codes:

 - 204: OK
 - 400: Alias structure or member is not valid

## Creating domain mappings

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

Be aware that no checks to find possible loops that would result of this creation will be performed.

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

## Creating regex mapping

You can use **webadmin** to create regex mappings.

A regex mapping contains a mapping source and a Java Regular Expression (regex) in String as the mapping value.
Everytime, if a mail containing a recipient matched with the mapping source,
then that mail will be re-routed to a new recipient address
which is re written by the regex.

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and
requires the [RecipientRewriteTable API](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

 - [Adding a regex mapping](#Adding_a_regex_mapping)
 - [Removing a regex mapping](#Removing_a_regex_mapping)

### Adding a regex mapping

```
POST /mappings/regex/mappingSource/targets/regex
```

Where:

 - the `mappingSource` is the path parameter represents for the Regex Mapping mapping source
 - the `regex` is the path parameter represents for the Regex Mapping regex

The route will add a regex mapping made from `mappingSource` and `regex` to RecipientRewriteTable.

Example:

```
curl -XPOST http://ip:port/mappings/regex/james@domain.tld/targets/james@.*:james-intern@james.org
```

Response codes:

 - 204: Mapping added successfully.
 - 400: Invalid `mappingSource` path parameter.
 - 400: Invalid `regex` path parameter.

Be aware that no checks to find possible loops that would result of this creation will be performed.

### Removing a regex mapping

```
DELETE /mappings/regex/{mappingSource}/targets/{regex}
```

Where:

 - the `mappingSource` is the path parameter representing the Regex Mapping mapping source
 - the `regex` is the path parameter representing the Regex Mapping regex

The route will remove the regex mapping made from `regex` from the mapping source `mappingSource` 
to RecipientRewriteTable.

Example:

```
curl -XDELETE http://ip:port/mappings/regex/james@domain.tld/targets/[O_O]:james-intern@james.org
```

Response codes:

 - 204: Mapping deleted successfully.
 - 400: Invalid `mappingSource` path parameter.
 - 400: Invalid `regex` path parameter.

## Address Mappings

You can use **webadmin** to define address mappings.

When a specific email is sent to the base mail address, every destination addresses will receive it.

This feature uses [Recipients rewrite table](/server/config-recipientrewritetable.html) and requires
the [RecipientRewriteTable mailet](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java)
to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

Please use address mappings with caution, as it's not a typed address. If you know the type of your address (forward, alias, domain, group, etc), prefer using the corresponding routes to those types.

Here are the following actions available on address mappings:

 - [List all address mappings](#List_all_address_mappings)
 - [Add an address mapping](#Add_an_address_mapping)
 - [Remove an address mapping](#Remove_an_address_mapping)

### List all address mappings

```
curl -XGET http://ip:port/mappings
```

Get all mappings from the [Recipients rewrite table](/server/config-recipientrewritetable.html)
Supported mapping types are the following:

 - [Alias](#Creating_address_aliases)
 - [Address](#Address_Mappings)
 - [Domain](#Creating_address_domain)
 - Error
 - [Forward](#Creating_address_forwards)
 - [Group](#Creating_address_group)
 - Regex

Response body:

```
{
  "alias@domain.tld": [
    {
      "type": "Alias",
      "mapping": "user@domain.tld"
    },
    {
      "type": "Group",
      "mapping": "group-user@domain.tld"
    }
  ],
  "aliasdomain.tld": [
    {
      "type": "Domain",
      "mapping": "realdomain.tld"
    }
  ],
  "group@domain.tld": [
    {
      "type": "Address",
      "mapping": "user@domain.tld"
    }
  ]
}
```

Response code:

 - 200: OK

### Add an address mapping

```
curl -XPOST http://ip:port/mappings/address/{mappingSource}/targets/{destinationAddress}
```

Add an address mapping to the [Recipients rewrite table](/server/config-recipientrewritetable.html)
Mapping source is the value of {mappingSource}
Mapping destination is the value of {destinationAddress}
Type of mapping destination is Address

Response codes:

- 204: Action successfully performed
- 400: Invalid parameters
- 409: The creation of the address mapping would lead to a loop and thus cannot be performed

### Remove an address mapping

```
curl -XDELETE http://ip:port/mappings/address/{mappingSource}/targets/{destinationAddress}
```

 - Remove an address mapping from the [Recipients rewrite table](/server/config-recipientrewritetable.html)
 - Mapping source is the value of `mappingSource`
 - Mapping destination is the value of `destinationAddress`
 - Type of mapping destination is Address

Response codes:

- 204: Action successfully performed
- 400: Invalid parameters


## User Mappings

 - [Listing User Mappings](#Listing_User_Mappings)

### Listing User Mappings

This endpoint allows receiving all mappings of a corresponding user.

```
curl -XGET http://ip:port/mappings/user/{userAddress}
```

Return all mappings of a user where:

 - `userAddress`: is the selected user

Response body:

```
[
  {
    "type": "Address",
    "mapping": "user123@domain.tld"
  },
  {
    "type": "Alias",
    "mapping": "aliasuser123@domain.tld"
  },
  {
    "type": "Group",
    "mapping": "group123@domain.tld"
  }
]
```

Response codes:

- 200: OK
- 400: Invalid parameter value

## Administrating mail repositories

 - [Create a mail repository](#Create_a_mail_repository)
 - [Listing mail repositories](#Listing_mail_repositories)
 - [Getting additional information for a mail repository](#Getting_additional_information_for_a_mail_repository)
 - [Listing mails contained in a mail repository](#Listing_mails_contained_in_a_mail_repository)
 - [Reading/downloading a mail details](#Reading.2Fdownloading_a_mail_details)
 - [Removing a mail from a mail repository](#Removing_a_mail_from_a_mail_repository)
 - [Removing all mails from a mail repository](#Removing_all_mails_from_a_mail_repository)
 - [Reprocessing mails from a mail repository](#Reprocessing_mails_from_a_mail_repository)
 - [Reprocessing a specific mail from a mail repository](#Reprocessing_a_specific_mail_from_a_mail_repository)

### Create a mail repository

```
curl -XPUT http://ip:port/mailRepositories/{encodedPathOfTheRepository}?protocol={someProtocol}
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
curl -XGET http://ip:port/mailRepositories/{encodedPathOfTheRepository}
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

```
curl -XGET http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F
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
curl -XGET http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails
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
curl -XGET 'http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?limit=100&offset=500'
```

Response codes:

 - 200: The list of mail keys contained in that mail repository
 - 400: Invalid parameters
 - 404: This repository can not be found

### Reading/downloading a mail details

```
curl -XGET http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails/mailKey
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

Additional query parameter `additionalFields` add the existing information 
to the response for the supported values (only work with "application/json" Accept header):

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
curl -XDELETE http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails/mailKey
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
curl -XDELETE http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

```
curl -XDELETE http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 404: Could not find that mail repository

The scheduled task will have the following type `clear-mail-repository` and the following `additionalInformation`:

```
{
  "mailRepositoryPath":"var/mail/error/",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing mails from a mail repository

Sometime, you want to re-process emails stored in a mail repository. For instance, you can make a configuration error, or there can be a James bug that makes processing of some mails fail. Those mail will be stored in a mail repository. Once you solved the problem, you can reprocess them.

To reprocess mails from a repository:

```
curl -XPATCH http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails?action=reprocess
```

Resource name `encodedPathOfTheRepository` should be the resource path of an existing mail repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?action=reprocess
```

Additional query parameters are supported:
 - `queue` allows you to target the mail queue you want to enqueue the mails in. Defaults to `spool`.
 - `processor` allows you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.
 Defaults to the `state` field of each processed email.
 - `consume` (boolean defaulting to `true`) whether the reprocessing should consume the mail in its originating mail repository. Passing
 this value to `false` allows non destructive reprocessing as you keep a copy of the email in the mail repository and can be valuable
 when debugging.
 - `limit` (integer value. Optional, default is empty). It enables to limit the count of elements reprocessed.
 If unspecified the count of the processed elements is unbounded.
 - `maxRetries` Optional integer, defaults to no max retries limit. Only processed emails that had been retried less 
 than this value. Ignored by default.


For instance:

```
curl -XPATCH 'http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails?action=reprocess&processor=transport&queue=spool'
```

Note that the `action` query parameter is compulsary and can only take value `reprocess`.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 404: Could not find that mail repository

The scheduled task will have the following type `reprocessing-all` and the following `additionalInformation`:

```
{
  "mailRepositoryPath":"var/mail/error/",
  "targetQueue":"spool",
  "targetProcessor":"transport",
  "initialCount": 243,
  "remainingCount": 17
}
```

### Reprocessing a specific mail from a mail repository

To reprocess a specific mail from a mail repository:

```
curl -XPATCH http://ip:port/mailRepositories/{encodedPathOfTheRepository}/mails/mailKey?action=reprocess
```

Resource name `encodedPathOfTheRepository` should be the resource id of an existing mail repository. Resource name `mailKey` should be the key of a mail stored in that repository. Example:

For instance:

```
curl -XPATCH http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/name1?action=reprocess
```

Additional query parameters are supported:
 - `queue` allows you to target the mail queue you want to enqueue the mails in. Defaults to `spool`.
 - `processor` allows you to overwrite the state of the reprocessing mails, and thus select the processors they will start their processing in.
 Defaults to the `state` field of each processed email.
 - `consume` (boolean defaulting to `true`) whether the reprocessing should consume the mail in its originating mail repository. Passing
 this value to `false` allows non destructive reprocessing as you keep a copy of the email in the mail repository and can be valuable
 when debugging.

While `processor` being an optional parameter, not specifying it will result reprocessing the mails in their current state ([see documentation about processors and state](https://james.apache.org/server/feature-mailetcontainer.html#Processors)).
Consequently, only few cases will give a different result, definitively storing them out of the mail repository.

For instance:

```
curl -XPATCH 'http://ip:port/mailRepositories/var%2Fmail%2Ferror%2F/mails/name1?action=reprocess&processor=transport&queue=spool'
```

Note that the `action` query parameter is compulsary and can only take value `reprocess`.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 404: Could not find that mail repository

The scheduled task will have the following type `reprocessing-one` and the following `additionalInformation`:

```
{
  "mailRepositoryPath":"var/mail/error/",
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
 - [RabbitMQ republishing a mail queue from cassandra](#RabbitMQ_republishing_a_mail_queue_from_cassandra)

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
curl -XGET http://ip:port/mailQueues/{mailQueueName}
```

Resource name `mailQueueName` is the name of a mail queue, this command will return the details of the given mail queue. For instance:

```
{"name":"outgoing","size":0}
```

Response codes:

 - 200: Success
 - 400: Mail queue is not valid
 - 404: The mail queue does not exist

### Listing the mails of a mail queue

```
curl -XGET http://ip:port/mailQueues/{mailQueueName}/mails
```

Additional URL query parameters:

 - `limit`: Maximum number of mails returned in a single call. Only strictly positive integer values are accepted. Example:
 
```
curl -XGET http://ip:port/mailQueues/{mailQueueName}/mails?limit=100
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
curl -XDELETE http://ip:port/mailQueues/{mailQueueName}/mails?sender=senderMailAddress
```

This request should have exactly one query parameter from the following list:

* sender: which is a mail address (i.e. sender@james.org)
* name: which is a string
* recipient: which is a mail address (i.e. recipient@james.org)

The mails from the given mail queue matching the query parameter will be deleted.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 400: Invalid request
 - 404: The mail queue does not exist

The scheduled task will have the following type `delete-mails-from-mail-queue` and the following `additionalInformation`:

```
{
  "queue":"outgoing",
  "initialCount":10,
  "remainingCount": 5,
  "sender": "sender@james.org",
  "name": "Java Developer",
  "recipient: "recipient@james.org"
}
```

### Clearing a mail queue

```
curl -XDELETE http://ip:port/mailQueues/{mailQueueName}/mails
```

All mails from the given mail queue will be deleted.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - 400: Invalid request
 - 404: The mail queue does not exist

The scheduled task will have the following type `clear-mail-queue` and the following `additionalInformation`:

```
{
  "queue":"outgoing",
  "initialCount":10,
  "remainingCount": 0
}
```

### Flushing mails from a mail queue

```
curl -XPATCH http://ip:port/mailQueues/{mailQueueName}?delayed=true \
  -d '{"delayed": false}' \
  -H "Content-Type: application/json"
```

This request should have the query parameter *delayed* set to *true*, in order to indicate only delayed mails are affected.
The payload should set the `delayed` field to false inorder to remove the delay. This is the only supported combination,
and it performs a flush.

The mails delayed in the given mail queue will be flushed.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 204: Success (No content)
 - 400: Invalid request
 - 404: The mail queue does not exist
 
### RabbitMQ republishing a mail queue from cassandra

```
curl -XPOST 'http://ip:port/mailQueues/{mailQueueName}?action=RepublishNotProcessedMails&olderThan=1d'
```

This method is specific to the distributed flavor of James, which relies on Cassandra and RabbitMQ for implementing a mail queue.
In case of a RabbitMQ crash resulting in a loss of messages, this task can be launched to repopulate the
`mailQueueName` queue in RabbitMQ using the information stored in Cassandra.

The `olderThan` parameter is mandatory. It filters the mails to be restored, by taking into account only
the mails older than the given value.
The expected value should be expressed in the following format: `Nunit`.
`N` should be strictly positive.
`unit` could be either in the short form (`h`, `d`, `w`, etc.), or in the long form (`day`, `week`, `month`, etc.).

Examples:

 - `5h`
 - `7d`
 - `1y`

Response codes:

 - 201: Task created
 - 400: Invalid request

 The response body contains the id of the republishing task.
 ```
 {
     "taskId": "a650a66a-5984-431e-bdad-f1baad885856"
 }
 ```

## Sending email over webAdmin

```
curl -XPOST /mail-transfer-service

{MIME message}
```

Will send the following email to the recipients specified in the MIME message.

The `{MIME message}` payload must match `message/rfc822` format.

## Administrating DLP Configuration

DLP (stands for Data Leak Prevention) is supported by James. A DLP matcher will, on incoming emails,
execute regular expressions on email sender, recipients or content, in order to report suspicious emails to
an administrator. WebAdmin can be used to manage these DLP rules on a per `senderDomain` basis.

`senderDomain` is domain of the sender of incoming emails, for example: `apache.org`, `james.org`,...
Each `senderDomain` correspond to a distinct DLP configuration.

- [List DLP configuration by sender domain](#List_DLP_configuration_by_sender_domain)
- [Store DLP configuration by sender domain](#Store_DLP_configuration_by_sender_domain)
- [Remove DLP configuration by sender domain](#Remove_DLP_configuration_by_sender_domain)
- [Fetch a DLP configuration item by sender domain and rule id](#Fetch_a_DLP_configuration_item_by_sender_domain_and_rule_id)

### List DLP configuration by sender domain

Retrieve a DLP configuration for corresponding `senderDomain`, a configuration contains list of configuration items

```
curl -XGET http://ip:port/dlp/rules/{senderDomain}
```

Response codes:

 - 200: A list of dlp configuration items is returned
 - 400: Invalid `senderDomain` or payload in request
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
curl -XPUT http://ip:port/dlp/rules/{senderDomain}
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
 - 400: Invalid `senderDomain` or payload in request
 - 404: The domain does not exist.

### Remove DLP configuration by sender domain

Remove a DLP configuration for corresponding `senderDomain`

```
curl -XDELETE http://ip:port/dlp/rules/{senderDomain}
```

Response codes:

 - 204: DLP configuration is removed
 - 400: Invalid `senderDomain` or payload in request
 - 404: The domain does not exist.


### Fetch a DLP configuration item by sender domain and rule id

Retrieve a DLP configuration rule for corresponding `senderDomain` and a `ruleId`

```
curl -XGET http://ip:port/dlp/rules/{senderDomain}/rules/{ruleId}
```

Response codes:

 - 200: A dlp configuration item is returned
 - 400: Invalid `senderDomain` or payload in request
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

 - [Retrieving global sieve quota](#Retrieving_global_sieve_quota)
 - [Updating global sieve quota](#Updating_global_sieve_quota)
 - [Removing global sieve quota](#Removing_global_sieve_quota)
 - [Retrieving user sieve quota](#Retrieving_user_sieve_quota)
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

This endpoints allows to remove the Sieve quota of a user. There will no more quota for this user:

```
curl -XDELETE http://ip:port/sieve/quota/users/user@domain.com
```

Response codes:

 - 204: Operation succeeded

## Running blob garbage collection

When deduplication is enabled one needs to explicitly run a garbage collection in order to delete no longer referenced
blobs.

To do so:

```
curl -XDELETE http://ip:port/blobs?scope=unreferenced
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Additional parameters include Bloom filter tuning parameters:

 - **associatedProbability**: Allow to define the targeted false positive rate. Note that subsequent runs do not have the
same false-positives. Defaults to `0.01`.
 - **expectedBlobCount**: Expected count of blobs used to size the bloom filters. Defaults to `1.000.000`.
 
These settings directly impacts the memory footprint of the bloom filter. [Simulators](https://hur.st/bloomfilter/) can
help understand those parameters.

The created task has the following additional information:

```json
{
  "referenceSourceCount": 3456,
  "blobCount": 5678,
  "gcedBlobCount": 1234,
  "bloomFilterExpectedBlobCount": 10000,
  "bloomFilterAssociatedProbability": 0.01
}
```

Where:

 - **bloomFilterExpectedBlobCount** correspond to the supplied **expectedBlobCount** query parameter.
 - **bloomFilterAssociatedProbability** correspond to the supplied **associatedProbability** query parameter.
 - **referenceSourceCount** is the count of distinct blob references encountered while populating the bloom filter.
 - **blobCount** is the count of blobs tried against the bloom filter. This value can be used to better size the bloom
filter in later runs.
 - **gcedBlobCount** is the count of blobs that were garbage collected.

## Administrating Jmap Uploads

- [Cleaning upload repository](#Cleaning_upload_repository)


### Cleaning upload repository

```
curl -XDELETE http://ip:port/jmap/uploads?scope=expired
```

Will schedule a task for clearing expired upload entries.

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Query parameter `scope` is required and have the value `expired`. 

Response codes:

- 201: Success. Corresponding task id is returned.
- 400: Scope invalid

The scheduled task will have the following type `UploadRepositoryCleanupTask` and
the following `additionalInformation`:

```
{
  "scope": "expired",
  "timestamp": "2007-12-03T10:15:30Z",
  "type": "UploadRepositoryCleanupTask"
}
```

## Event Dead Letter

The EventBus allows to register 'group listeners' that are called in a (potentially) distributed fashion. These group
listeners enable the implementation of some advanced mailbox manager feature like indexing, spam reporting, quota management
and the like.

Upon exceptions, a bounded number of retries are performed (with exponential backoff delays). If after those retries the listener is still
failing, then the event will be stored in the "Event Dead Letter". This API allows diagnosing issues, as well as performing event replay (not implemented yet).

 - [Event Dead Letter](#Event_Dead_Letter)
 - [Listing mailbox listener groups](#Listing_mailbox_listener_groups)
 - [Listing failed events](#Listing_failed_events)
 - [Getting event details](#Getting_event_details)
 - [Deleting an event](#Deleting_an_event)
 - [Deleting all events of a group](#Deleting_all_events_of_a_group)
 - [Redeliver all events](#Redeliver_all_events)
 - [Redeliver group events](#Redeliver_group_events)
 - [Redeliver a single event](#Redeliver_a_single_event)
 - [Rescheduling group execution](#Rescheduling_group_execution)

### Listing mailbox listener groups

This endpoint allows discovering the list of mailbox listener groups.

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
 - 400: Invalid group name or `insertionId`
 - 404: No event with this `insertionId`

### Deleting an event

```
curl -XDELETE http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA/6e0dd59d-660e-4d9b-b22f-0354479f47b4
```

Will delete this event.

Response codes:

 - 204: Success
 - 400: Invalid group name or `insertionId`

### Deleting all events of a group

```
curl -XDELETE http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA
```

Will delete all events of this group.

Response codes:

- 204: Success
- 400: Invalid group name

### Redeliver all events

```
curl -XPOST http://ip:port/events/deadLetter?action=reDeliver
```

Will create a task that will attempt to redeliver all events stored in "Event Dead Letter".
If successful, redelivered events will then be removed from "Dead Letter".

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid action argument

### Redeliver group events

```
curl -XPOST http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA
```

Will create a task that will attempt to redeliver all events of a particular group stored in "Event Dead Letter".
If successful, redelivered events will then be removed from "Dead Letter".

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid group name or action argument

### Redeliver a single event

```
curl -XPOST http://ip:port/events/deadLetter/groups/org.apache.james.mailbox.events.EventBusTestFixture$GroupA/6e0dd59d-660e-4d9b-b22f-0354479f47b4?action=reDeliver
```

Will create a task that will attempt to redeliver a single event of a particular group stored in "Event Dead Letter".
If successful, redelivered event will then be removed from "Dead Letter".

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes:

 - 201: the taskId of the created task
 - 400: Invalid group name, insertion id or action argument
 - 404: No event with this insertionId

### Rescheduling group execution

Not implemented yet.

## Deleted Messages Vault

The 'Deleted Message Vault plugin' allows you to keep users deleted messages during a given retention time. This set of routes allow you to *restore* users deleted messages or export them in an archive.

To move deleted messages in the vault, you need to specifically configure the DeletedMessageVault PreDeletionHook.

Here are the following actions available on the 'Deleted Messages Vault'

 - [Restore Deleted Messages](#Restore_Deleted_Messages)
 - [Export Deleted Messages](#Export_Deleted_Messages)
 - [Purge Deleted Messages](#Purge_Deleted_Messages)
 - [Permanently Remove Deleted Message](#Permanently_Remove_Deleted_Message)

 Note that the 'Deleted Messages Vault' feature is supported on top of all available Guice products.

### Restore Deleted Messages

Deleted messages of a specific user can be restored by calling the following endpoint:

```
curl -XPOST http://ip:port/deletedMessages/users/userToRestore@domain.ext?action=restore

{
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

The requested Json body is made from a list of criterion objects which have the following structure:

```
{
  "fieldName": "supportedFieldName",
  "operator": "supportedOperator",
  "value": "A plain string representing the matching value of the corresponding field"
}
```

Deleted Messages which are matched with the **all** criterion in the query body will be restored. Here are a list of supported fieldName for the restoring:

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
   
Messages in the Deleted Messages Vault of a specified user that are matched with Query Json Object in the body will be appended to his 'Restored-Messages' mailbox, which will be created if needed.

**Note**:

 - Query parameter `action` is required and should have the value `restore` to represent the restoring feature. Otherwise, a bad request response will be returned
 - Query parameter `action` is case sensitive
 - fieldName & operator passed to the routes are case sensitive
 - Currently, we only support query combinator `and` value, otherwise, requests will be rejected 
 - If you only want to restore by only one criterion, the json body could be simplified to a single criterion:

```
{
  "fieldName": "subject", 
  "operator": "containsIgnoreCase", 
  "value": "Apache James"
}
```

 - For restoring all deleted messages, passing a query json with an empty criterion list to represent `matching all deleted messages`:

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
 
[More details about endpoints returning a task](#Endpoints_returning_a_task).

The scheduled task will have the following type `deleted-messages-restore` and the following `additionalInformation`:

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
curl -XPOST 'http://ip:port/deletedMessages/users/userExportFrom@domain.ext?action=export&exportTo=userReceiving@domain.ext'

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

[More details about endpoints returning a task](#Endpoints_returning_a_task).

The scheduled task will have the following type `deleted-messages-export` and the following `additionalInformation`:

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
 
You can overwrite 'retentionPeriod' configuration in 'deletedMessageVault' configuration file or use the default value of 1 year.

Purge all deleted messages older than the configured 'retentionPeriod'

```
curl -XDELETE http://ip:port/deletedMessages?scope=expired
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response code:

 - 201: Task for purging has been created
 - 400: Bad request: 
   - action query param is not present
   - action query param is not a valid action

You may want to call this endpoint on a regular basis.

### Permanently Remove Deleted Message

Delete a Deleted Message with `MessageId`

```
curl -XDELETE http://ip:port/deletedMessages/users/user@domain.ext/messages/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response code:

 - 201: Task for deleting message has been created
 - 400: Bad request: 
   - user parameter is invalid
   - messageId parameter is invalid
 - 404: User not found
 
The scheduled task will have the following type `deleted-messages-delete` and the following `additionalInformation`:
 
```
 {
   "userName": "user@domain.ext",
   "messageId": "3294a976-ce63-491e-bd52-1b6f465ed7a2"
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
 - [Endpoints returning a task](#Endpoints_returning_a_task)

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
    "type": "type-of-the-task"
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
curl -XGET http://ip:port/tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2/await?timeout=duration
```

An Execution Report will be returned.

`timeout` is optional.
By default it is set to 365 days (the maximum value).
The expected value is expressed in the following format: `Nunit`.
`N` should be strictly positive.
`unit` could be either in the short form (`s`, `m`, `h`, etc.), or in the long form (`day`, `week`, `month`, etc.).

Examples:

 - `30s`
 - `5m`
 - `7d`
 - `1y`

Response codes:

 - 200: The specific task was found and the execution report exposed above is returned
 - 400: Invalid task ID or invalid timeout
 - 404: Task ID was not found
 - 408: The timeout has been reached

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
curl -XGET http://ip:port/tasks
```

Will return a list of Execution reports

One can filter the above results by status. For example:

```
curl -XGET http://ip:port/tasks?status=inProgress
```

Will return a list of Execution reports that are currently in progress. This list is sorted by 
reverse submitted date (recent tasks goes first).

Response codes:

 - 200: A list of corresponding tasks is returned
 - 400: Invalid status value
 
Additionnal optional task parameters are supported:

 - `status` one of `waiting`, `inProgress`, `canceledRequested`, `completed`, `canceled`, `failed`. Only
 tasks with the given status are returned.
 - `type`: only tasks with the given type are returned.
 - `offset`: Integer, number of tasks to skip in the response. Useful for paging.
 - `limit`: Integer, maximum number of tasks to return in one call
 
### Endpoints returning a task

Many endpoints do generate a task.

Example:

```
curl -XPOST /endpoint?action={action}
```

The response to these requests will be the scheduled `taskId` :

```
{"taskId":"5641376-02ed-47bd-bcc7-76ff6262d92a"}
```

Positionned headers:

 - Location header indicates the location of the resource associated with the scheduled task. Example:

```
Location: /tasks/3294a976-ce63-491e-bd52-1b6f465ed7a2
```

Response codes:

 - 201: Task generation succeeded. Corresponding task id is returned.
 - Other response codes might be returned depending on the endpoint

The additional information returned depends on the scheduled task type and is documented in the endpoint documentation.

## Cassandra extra operations

Some webadmin features to manage some extra operations on Cassandra tables, like solving inconsistencies on projection tables.
Such inconsistencies can be for example created by a fail of the DAO to add a mapping into 'mappings_sources`, while it was successful
regarding the `rrt` table.

 - [Operations on mappings sources](#Operations_on_mappings_sources)

### Operations on mappings sources

You can do a series of action on `mappings_sources` projection table :

```
curl -XPOST /cassandra/mappings?action={action}
```

Will return the taskId corresponding to the related task. Actions supported so far are :

 - SolveInconsistencies : cleans up first all the mappings in `mappings_sources` index and then repopulate it correctly. In the meantime,
listing sources of a mapping might create temporary inconsistencies during the process.

For example :

```
curl -XPOST /cassandra/mappings?action=SolveInconsistencies
```

[More details about endpoints returning a task](#Endpoints_returning_a_task).

Response codes :

 - 201: the taskId of the created task
 - 400: Invalid action argument for performing operation on mappings data
