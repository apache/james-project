# Grafana reporting

The following command allow you to run a fresh grafana server :

```
docker run -i -p 3000:3000 grafana/grafana
```

Once running, you need to set up an ElasticSearch data-source :
 - select proxy mode
 - Select version 2.x of ElasticSearch
 - make the URL point your ES node
 - Specify the index name. By default, it should be :

```
[james-metrics-]YYYY-MM
```

Import the different dashboards in this directory.

You then need to enable reporting through ElasticSearch. Modify your James ElasticSearch configuration file accordingly.
To help you doing this, you can take a look to [GitHub](https://github.com/apache/james-project/blob/master/dockerfiles/run/guice/cassandra/destination/conf/elasticsearch.properties).
Note that you need to run a guice version of James.

## Presentation of the different boards

 - JVM statistics
 - Percentiles for IMAP / JMAP / SMTP commands
 - Requests counts for IMAP / JMAP / SMTP commands
 - Statistics about Mailet / Matcher execution times
 - Statistics about Mail queues
 - Statistics about DNS calls
 - Some other, diverse information on the James server internals
 - Cassandra driver statistics
 - Tika HTTP client statistics
 - SpamAssassin TCP client statistics
 - Mailbox listeners statistics execution times
 - Mailbox listeners requests rate
 - MailQueue enqueue/dequeue timer & counter statistics
 - BlobStore timer statistics
 - Statistics about pre-deletion hooks execution times
 - MessageFastViewProjection retrieving hits & miss count 
