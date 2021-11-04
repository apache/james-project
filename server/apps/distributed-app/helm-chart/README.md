# Helm chart for James


## Introduction

This chart bootstraps the distributed version of Apache James Mail Server on [Kubernetes](http://kubernetes.io) 
cluster using [Helm](https://helm.sh) package manager.

Documentation on how to use it can be found at [Run Kubernetes](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/docs/modules/ROOT/pages/run/run-kubernetes.adoc).

## Notice about mailetcontainer

You will need to add yourself manually the `mailetcontainer.xml` configuration file into
the configs folder proper to your client before installing the helm James package.

You can get the template `mailetcontainer.xml` in our [sample-configuration](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/sample-configuration/) folder.

NB: It should change in a near future as helm should support such an operation.

## Notice about RabbitMQ

James is linked to RabbitMQ instances for mail processing.

You need RabbitMQ to run before deploying James. The username and password for `rabbitmq-account-james` secret must be created first from RabbitMQ.