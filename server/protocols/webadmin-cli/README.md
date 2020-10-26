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
It does not require any argument to execute. Options --url are optional. Without it, the default value is http://127.0.0.1:8000.
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
   - [Test a user existence](#test-a-user-existence)
   - [Delete a user](#delete-a-user)
   - [Get users list](#get-users-list)
   - [Update a user password](#update-a-user-password)

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



## Manage Users

### Create a user

Add a user to the user list.
```
{cli} user create <username> <password>
```
Resource name <username> representing valid users, hence it should match the criteria at [User Repositories documentation](https://james.apache.org/server/config-users.html)

Note: if the user exists already, its password will be updated.

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