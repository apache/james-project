# Performance test Postgres app

To provision and benchmark an IMAP server backed by PostgreSQL, please have a look at following steps:
1. Build and extract the Postgres app docker image.
   - `mvn clean install -DskipTests -Dmaven.skip.doc=true`
   - `docker load -i ./target/jib-image.tar`
2. Run the Postgres app: `docker compose up`
3. Provision users and IMAP mailboxes + messages: `./provision.sh`
4. Performance test IMAP server using [james-gatling](https://github.com/linagora/james-gatling)
   
   Sample IMAP simulation: `gatling:testOnly org.apache.james.gatling.simulation.imap.PlatformValidationSimulation`.