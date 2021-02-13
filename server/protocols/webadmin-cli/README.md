# A WebAdmin based CLI for Apache James

## Development

Webadmin command-line interface is an upcoming replacement for the outdated, security-vulnerable JMX command-line interface. It also aims at providing a more modern and intuitive interface.

## Run the command line

## Syntax

General syntax to run the command line

```   
$ ./james-cli [OPTION] ENTITY ACTION {ARGUMENT}
```

where

    OPTION: optional parameter when running the command line,
  
    ENTITY: represents the entity to perform action on,
  
    ACTION: name of the action to perform,
  
    ARGUMENT: arguments needed for the action.

Example: 
```
$ ./james-cli --url http://127.0.0.1:9999 domain list
```

The above command lists all domain names available on domain route at address http://127.0.0.1:9999. 
It does not require any argument to execute. 

The following options can be used:

--url : Without it, the default value is http://127.0.0.1:8000.

When James server's jwt setting is enabled, jwt options are required:

--jwt-token : pass the jwt token directly as plain text. E.g: 
```
$ ./james-cli --url http://127.0.0.1:8000 --jwt-token eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlc24iLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNjA0Mjg3OTU3fQ.IY3AWg9lQE4muXg8iRu1nbluTm786_6aLcoEylCGcbtGcEOp81Neani1-_17xGp2aF6kxBJva0_f_ADUplhfirGXwoxM8evcsdmQBhNGDfa-oXT_a-dd6n7MMPweGOFGhjxnTRvNHpyqDcfNuGGGcB8OfzOsN5epWNkhlivk2OSuY8vR8fHB9Es1Oiv1i8D5H93-K5IXParVLpAtj7uLZT9b7G-kjxlQagApH8USx6JlqIjMwhvM--79SrzPKZ2AtM59NfDT4g6GPaMNntQvXr06Xu3Leys97cot2rsXNfXY1OohorXiYiZ66-eMRcQ7pHF-NyS6dlg87rnMQlpKHw domain list
```
Commands with valid token with admin=true claim should pass James's authentication.

--jwt-from-file : pass the jwt token through a file (such as token.jwt). E.g:
```
$ ./james-cli --url http://127.0.0.1:8000 --jwt-from-file token.jwt domain list
```

As for other commands, arguments might be required after the sub-command (ACTION such as list, add and remove).

Note: the command line before ENTITY will be documented as {cli}.

## Navigation menu

- [Manage Domains](#manage-domains)
   - [Create a domain](#create-a-domain)
   - [Delete a domain](#delete-a-domain)
   - [Check if a domain exists](#check-if-a-domain-exists)
   - [Get the list of domains](#get-the-list-of-domains)
- [Manage Users](#manage-users) 
   - [Create a user](#create-a-user)
   - [Update a user password](#update-a-user-password)  
   - [Test a user existence](#test-a-user-existence)
   - [Delete a user](#delete-a-user)
   - [Get users list](#get-users-list)
   - [Update a user password](#update-a-user-password)
- [Manage Mailboxes](#manage-mailboxes)
   - [Create a mailbox](#create-a-mailbox)
   - [Test a mailbox existence](#test-a-mailbox-existence)
   - [Delete a mailbox and its children](#delete-a-mailbox-and-its-children)
   - [Delete all mailboxes of a user](#delete-all-mailboxes-of-a-user)
   - [Get mailboxes list](#get-mailboxes-list)
- [Manage Domain Mappings](#manage-domain-mappings)
   - [Listing all domain mappings](#listing-all-domain-mappings)
   - [Listing all destination domains for a source domain](#listing-all-destination-domains-for-a-source-domain)
   - [Adding a domain mapping](#adding-a-domain-mapping)
   - [Removing a domain mapping](#removing-a-domain-mapping)
- [Manage Regex Mappings](#manage-regex-mappings)
   - [Adding a regex mapping](#adding-a-regex-mapping)
   - [Removing a regex mapping](#removing-a-regex-mapping)
- [Manage Address Mappings](#manage-address-mappings)
   - [Add an address mapping](#add-an-address-mapping)
   - [Remove an address mapping](#remove-an-address-mapping)
- [Manage User Mappings](#manage-user-mappings)
  - [Listing User Mappings](#listing-user-mappings)
- [Manage Users Quotas](#manage-users-quotas)
- [Manage Domains Quotas](#manage-domains-quotas)
- [Manage Global Quotas](#manage-global-quotas)

## Manage Domains

### Create a domain
Add a domain to the domain list.
```
{cli} domain create <domainToBeCreated>
```
Resource name **domainToBeCreated**:

- cannot be null or empty
- cannot contain ‘@’
- cannot be more than 255 characters
- cannot contain ‘/’

### Delete a domain

Remove a domain from the domain list.
```
{cli} domain delete <domainToBeDeleted>
```
Note: Deletion of an auto-detected domain, default domain or of an auto-detected ip is not supported. We encourage you instead to review your [domain list configuration](https://james.apache.org/server/config-domainlist.html).

### Check if a domain exists
Check whether a domain exists on the domain list or not.
```
{cli} domain exist <domainToBeChecked>
```

### Get the list of domains
Show all domains' name on the list.
```
{cli} domain list
```

### List domain aliases for a domain
Show all domains' name on the list.
```
{cli} domain listAliases <domain>
```

### Create a domain alias for a domain

```
{cli} domain addAlias <domain> <sourceOfTheAlias>
```

### Remove a domain alias for a domain

```
{cli} domain removeAlias <domain> <sourceOfTheAlias>
```



## Manage Users

### Create a user

Add a user to the user list.
```
{cli} user create <username> --password
```
Then the Command Line will prompt users to enter password (password will not be printed on the screen for security):
```
Enter value for --password (Password):
```
Resource name <username> representing valid users, hence it should match the criteria at [User Repositories documentation](https://james.apache.org/server/config-users.html)

Note: If the user exists already, its password cannot be updated using this.
If you want to update a user's password, please have a look at [Update a user password](#update-a-user-password).

### Update a user password

```
{cli} user create --force <username> --password
```
Then the Command Line will prompt users to enter password (password will not be printed on the screen for security):
```
Enter value for --password (Password):
```
Resource name <username> representing valid users, hence it should match the criteria at [User Repositories documentation](https://james.apache.org/server/config-users.html)

Note: This also can be used to create a new user.

### Test a user existence

Check whether a user exists on the user list or not.
```
{cli} user exist <username>
```
Resource name <username> representing valid users, hence it should match the criteria at [User Repositories documentation](https://james.apache.org/server/config-users.html)

### Delete a user

Remove a user from the user list.
```
{cli} user delete <username>
```

### Get users list

Show all users' name on the list.

```
{cli} user list
```

### Update a user password
Same as Create, but a user need to exist.

If the user do not exist, then it will be created.

## Manage Mailboxes

### Create a mailbox
Create a specific mailbox for a user:

```
{cli} mailbox create <username> <mailboxName>
```

Resource name username should be an existing user.

Resource name mailboxName should not be empty, nor contain # & % * characters.

To create nested mailboxes, for instance a work mailbox inside the INBOX mailbox, people should use the . separator. E.g: INBOX.work

### Test a mailbox existence

```
{cli} mailbox exist <usernameToBeUsed> <mailboxNameToBeTested>
```

Resource name usernameToBeUsed should be an existing user 

Resource name mailboxNameToBeTested should not be empty

### Delete a mailbox and its children

```
{cli} mailbox delete <usernameToBeUsed> <mailboxNameToBeDeleted>
```

Resource name usernameToBeUsed should be an existing user 

Resource name mailboxNameToBeDeleted should not be empty

### Delete all mailboxes of a user

```
{cli} mailbox deleteAll <usernameToBeUsed>
```

Resource name usernameToBeUsed should be an existing user

### Get mailboxes list

```
{cli} mailbox list <usernameToBeUsed>
```

Resource name usernameToBeUsed should be an existing user

## Manage Domain Mappings

Given a configured source (from) domain and a destination (to) domain, when an email is sent to an address belonging to the source domain, then the domain part of this address is overwritten, the destination domain is then used. A source (from) domain can have many destination (to) domains.

For example: with a source domain james.apache.org maps to two destination domains james.org and apache-james.org, when a mail is sent to admin@james.apache.org, then it will be routed to admin@james.org and admin@apache-james.org

This feature uses [Recipients rewrite table](https://james.apache.org/server/config-recipientrewritetable.html) and requires the [RecipientRewriteTable](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java) mailet to be configured.

Note that email addresses are restricted to ASCII character set. Mail addresses not matching this criteria will be rejected.

### Listing all domain mappings

Show all configured domain mappings
```
{cli} domain-mapping listAll
```

### Listing all destination domains for a source domain

With sourceDomain.tld as the value passed to fromDomain resource name, this command will show all destination domains configured to that domain
```
{cli} domain-mapping list <sourceDomain.tld>
```

### Adding a domain mapping

With sourceDomain.tld as the value passed to fromDomain resource name, this command will add a destination domain to that source domain

```
{cli} domain-mapping create <sourceDomain.tld> <destination.tld>
```

### Removing a domain mapping

With sourceDomain.tld as the value passed to fromDomain resource name, this command will remove a destination domain mapped to that domain
```
{cli} domain-mapping delete <sourceDomain.tld> <destination.tld>
```

## Manage Regex Mappings

A regex mapping contains a mapping source and a Java Regular Expression (regex) in String as the mapping value. Everytime, if a mail containing a recipient matched with the mapping source, then that mail will be re-routed to a new recipient address which is re written by the regex.

This feature uses [Recipients rewrite table](https://james.apache.org/server/config-recipientrewritetable.html) and requires the [RecipientRewriteTable API](https://github.com/apache/james-project/blob/master/server/mailet/mailets/src/main/java/org/apache/james/transport/mailets/RecipientRewriteTable.java) to be configured.

### Adding a regex mapping

The command will add a regex mapping made from mappingSource and regex to RecipientRewriteTable.

```
{cli} regex-mapping create <mappingSource> <regex>
```
Where:

- mappingSource represents for the Regex Mapping mapping source
- regex represents for the Regex Mapping regex


### Removing a regex mapping

The command will remove the regex mapping made from regex and the mapping source mappingSource out of RecipientRewriteTable.
```
{cli} regex-mapping delete <mappingSource> <regex>
```
Where:

- the mappingSource represents the Regex Mapping mapping source
- the regex represents the Regex Mapping regex

## Manage Address Mappings

When a specific email is sent to the mappingSource mail address, every destination addresses will receive it.

### Add an address mapping

This command will add an address mapping to the Recipients rewrite table
```
{cli} address-mapping create <mappingSource> <destinationAddress>
```
Type of both parameters are Address.

### Remove an address mapping

Remove an address mapping from the Recipients rewrite table
```
{cli} address-mapping delete <mappingSource> <destinationAddress>
```
Type of both parameters are Address.

## Manage User Mappings

### Listing User Mappings

Receiving all mappings of a corresponding user.
```
{cli} user-mapping list <userAddress>
```