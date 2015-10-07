= Cassandra Mailbox implementation

This Mailbox sub-project is about providing a scalable mailbox implementation relying on Cassandra database.

Concurrency is handled by this implementation while performing writes using Lightweight transactions. You do not need to lock anything, or provide utils to lock anything, when using this implementation.

== Configuration

The configuration is achieved through Spring. The file is 'src/main/resources/META-INF/spring/mailbox-cassandra.xml' .

The components are instanciated and wired together.

What might interest you the most is the way you want to connect your Cassandra cluster.

Factories are used. You have :
  * ClusterFactory : you specify which Cassandra servers you want to connect, with ( optional ) which user name and password to use.
  * ClusterWithKeyspaceCreatedFactory : This ( optional ) component creates a Keyspace if it does not already exists. You may want to skip this step in production environment.
  * SessionFactory : Connect the appropriated Keyspace, to create a Session our application can work with.