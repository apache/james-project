# Grafana reporting

This is a collection of Grafana dashboards to display James metrics.

## Run Grafana 

The following command allow you to run a fresh grafana server :

```
docker run -i -p 3000:3000 grafana/grafana
```

## Configure a data source
Once running, you need to set up an [ElasticSearch data-source](https://grafana.com/docs/grafana/latest/datasources/elasticsearch/).
You can do this either from UI or from a configuration file.

## Setting up via UI
 - name it DS_JAMES_ES
 - select proxy mode
 - Select version 6.x of ElasticSearch
 - make the URL point your ES node
 - Specify the index name. By default, it should be :

```
[james-metrics-]YYYY-MM
```

## Setting up using a configuration file

Look up file grafana-datasource.yaml from Grafana and add following data source into it:

```
apiVersion: 1

datasources:
  - name: DS_JAMES_ES
    type: elasticsearch
    access: proxy
    database: "[james-metrics-]YYYY-MM"
    url: http://elasticsearch:9200
    version: 6
    editable: true
    jsonData:
      interval: Daily
      timeField: "@timestamp"
```

## Getting dashboards

Import the different dashboard JSON files in this directory to Grafana via UI
or paste the files into Grafana dashboards folder (/var/lib/grafana/dashboards by default) 

## Enable reporting from James configuration

You then need to enable James to report its stats into ElasticSearch.
Modify your James ElasticSearch configuration file accordingly.
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
