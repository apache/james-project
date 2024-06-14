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
mvn clean isntall -DskipTests --am --pl :james-server-mailet-ldap
```

Then copy 