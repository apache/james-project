Apache James - Karaf integration
================================

This Maven project deploy Apache James inside Apache Karaf OSGi container. Since James is built with Spring, we will
use Spring-DM for creating OSGi services thus allowing the same James components be used unchanged in or outside OSGi.

* http://karaf.apache.org/
* http://james.apache.org/
* http://static.springsource.org/osgi/docs/1.2.x/reference/html/

Why?
====

Apache Karaf provide a great container for deploying applications and managing their lifecycle. Read more about that on
the Karaf homepage.

OSGi promotes the use of services and modules that drive the creation of components easy to reuse and integrate into
other projects.

Running James inside Karaf container provides means for a secure administration console (over SSH).
This enable us to write commands to administer the server and do things like:

* start/stop IMAP/POP3/SMTP components without bringing down the entire server.
* add/remove users manually
* much more

Project structure
=================

* karaf/features - Builds a Karaf features that match James components to easy provision the application
  http://karaf.apache.org/manual/latest-2.3.x/users-guide/provisioning.html
* karaf/distribution - Builds a custom Karaf distribution bundled with James components. Uses the James features
* karaf/integration - Integration tests for deploying James inside Karaf

Build and run
=============

You can build the project by running the script provided in karaf folder:
    $ ./build.sh

You can also build each project individually by running **$ mvn clean install** inside each maven project.

You might wish to build the whole James project to ensure all artifacts are available in the local Maven repo. You can
do that by running **$ mvn clean install** in the project top level directory.

If everything goes well, look for a file called *distribution-VERSION-.tar.gz* under karaf/distribution/target.
Unpack, change the current directory to be inside the application and run **./bin/karaf**. This will start Karaf and
present you with a administration console. You are ready to install James features if not already installed and start
playing with James.

Run **features:install james-server-dnsservice-dnsjava** to provision and start the DNSService that
James uses to resolve mail hosts. To see the state of things you can run **list**. An output like the following means
the DNSService has started successfully:

~~~
    karaf@root> list
    START LEVEL 100 , List Threshold: 50
       ID   State         Blueprint      Spring    Level  Name
    [  97] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: commons-configuration (1.9.0.1)
    [  98] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: commons-beanutils (1.8.3.1)
    [  99] [Active     ] [            ] [       ] [   80] Commons JXPath (1.3)
    [ 100] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: xmlresolver (1.2.0.5)
    [ 101] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: commons-collections (3.2.1.3)
    [ 102] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: jdom (1.1.0.4)
    [ 103] [Active     ] [            ] [       ] [   80] Commons Codec (1.7.0)
    [ 104] [Active     ] [            ] [       ] [   80] Commons Lang (2.6)
    [ 105] [Active     ] [            ] [       ] [   80] Commons Digester (1.8.1)
    [ 106] [Active     ] [            ] [       ] [   80] Commons JEXL (2.1.1)
    [ 107] [Active     ] [            ] [       ] [   80] dnsjava (2.1.1)
    [ 108] [Active     ] [            ] [       ] [   80] Apache ServiceMix :: Bundles :: junit (4.11.0.1)
    [ 109] [Active     ] [            ] [       ] [   80] Apache James :: Server :: DNS Service :: Library (3.0.0.beta5-SNAPSHOT)
    [ 110] [Active     ] [            ] [       ] [   80] Apache James :: Server :: DNS Service :: API (3.0.0.beta5-SNAPSHOT)
    [ 111] [Active     ] [            ] [       ] [   80] Apache James :: Mailet API (2.5.1.SNAPSHOT)
    [ 112] [Active     ] [            ] [       ] [   80] Geronimo JavaMail 1.4 :: Mail (1.8.3)
    [ 113] [Active     ] [            ] [Started] [   80] Apache James :: Server :: DNS Service :: Implementation (3.0.0.beta5-SNAPSHOT)
    [ 114] [Active     ] [            ] [       ] [   80] Apache James :: Server :: Lifecycle API (3.0.0.beta5-SNAPSHOT)
~~~

If necessary, you can check the logs with **log:display**. You can also dynamically change the logging level via
**log:set**. These are all Karaf related things and you should check them out http://karaf.apache.org/manual/latest-2.3.x/commands/commands.html





