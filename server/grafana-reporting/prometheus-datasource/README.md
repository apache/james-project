# Dashboards for James on top of Prometheus / Grafana

The boards are slightly different than what we used to do with ES reporter, as we 
count now on Prometheus to scrap metrics from the /metrics endpoint exposed from James.

This is just a rough conversion for starters, it will likely need more refining over time.

# Grafana reporting

This is a collection of Grafana dashboards to display James metrics.

## Expose metrics for Prometheus collection

To enable James metrics, add ``extensions.routes`` to [webadmin.properties]( https://github.com/apache/james-project/blob/master/docs/modules/servers/pages/distributed/configure/webadmin.adoc) file:
```
extensions.routes=org.apache.james.webadmin.dropwizard.MetricsRoutes
```

## Configure Prometheus Data source
You need to set up [Prometheus](https://prometheus.io/docs/prometheus/latest/getting_started/) first to scrape James metrics.\
Add Apache James WebAdmin Url or IP address to `prometheus.yaml` configuration file.

```
scrape_configs:
  # The job name is added as a label `job=<job_name>` to any timeseries scraped from this config.
  - job_name: 'WebAdmin url Example'
    scrape_interval: 5s
    metrics_path: /metrics
    static_configs:
      - targets: ['james-webamin-url']
  - job_name: 'WebAdmin IP Example'
    scrape_interval: 5s
    metrics_path: /metrics
    static_configs:
      - targets: ['192.168.100.10:8000']      
```      
## Connect Data source to Grafana

Add Prometheus data source to Grafana.\
You can do this either from [Grafana UI](https://prometheus.io/docs/visualization/grafana/) or from a [configuration file](https://grafana.com/docs/grafana/latest/datasources/prometheus/). 

The following command allow you to run a fresh grafana server :

```
docker run -i -p 3000:3000 grafana/grafana
```