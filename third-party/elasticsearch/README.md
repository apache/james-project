# ElasticSearch metric extension for Apache James

Exports Apache James metrics directly to ElasticSearch that can later be queried 
using [Grafana](https://grafana.com/).

**WARNING**: A separate ElasticSearch server is required, as an ElasticSearch 6.3 is needed, which is not compatible with
 ElasticSearch 7.10 release line, and not compatible with **OpenSearch** currently used by Apache James.
 

To run this metric exporter register it in `extensions.properties`:

```
guice.extension.module=org.apache.james.metrics.es.v7.ESMetricReporterModule
guice.extension.startable=org.apache.james.metrics.es.v7.ESMetricReporter
```


For configuring the metric reporting on ElasticSearch edit `elasticsearch.properties` content:

| Property name | explanation |
|---|---|
| elasticsearch.http.host | Optional. Host to report metrics on. Defaults to master host. Must be specified if metric export to ElasticSearch is enabled. |
| elasticsearch.http.port | Optional. Http port to use for publishing metrics. Must be specified if metric export to ElasticSearch is enabled.|
| elasticsearch.metrics.reports.enabled | Optional. Boolean value. Enables metrics reporting. Defaults to false. |
| elasticsearch.metrics.reports.period | Optional. Seconds between metric reports. Defaults to 60 seconds. |
| elasticsearch.metrics.reports.index | Optional. Index to publish metrics on. Defaults to james-metrics.|

We provide a docker-compose of this set up. 

In order to run it...

 - 1. Compile this project: `mvn clean install -DskipTests --pl org.apache.james:apache-james-elasticsearch --am`
 
 - 2. Then start James:

```
docker-compose up
```