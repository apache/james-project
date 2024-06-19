# LDAP matchers for Apache James

This project is an extension for Apache James that allows configuring conditions based on LDAP content within Apache 
James mailetcontainer.

Those include:

 - LDAP conditions based on attributes of the recipients
 - LDAP conditions based on attributes of the sender

Support is planned for LDAP groups both for sender and recipients.

## Set up

Build this project. For the root of the repository:

```bash
mvn clean install -DskipTests --am --pl :james-server-mailet-ldap
```

Then copy the JAR located in `/target` into the `extensions-jars` folder of your Apache James installation.

## Configuration

The LDAP settings are reused from the `usersrepository.xml` configuration file.

Then LDAP matchers can be configured within `mailetcontainer.xml`. For instance:

```xml
<mailet matcher="HasLDAPAttibute=description:blocked" class="Null">
    
</mailet>
```

or

```xml
<mailet matcher="SenderHasLDAPAttibute=description:blocked" class="Null">
    
</mailet>
```
