---
layout: post
title:  "Apache James `latest` Docker images changes"
date:   2019-08-30  09:09:47 +0100
categories: james update
---

We have decided to change the `latest` Docker images behaviour.

Until nowadays, such images were built on the `master` branch for each products (`linagora/james-memory`, `linagora/james-cassandra-rabbitmq-ldap-project`, ...).  
This is not the way `latest` docker image should be, this [blog post](https://blog.container-solutions.com/docker-latest-confusion) is explaining this kind of misunderstood.  

So we decided to follow the global Docker users behaviour which is to clone the _latest stable release_ as the Docker `latest` images.

For those of you who are willing to use a Docker image on top of the master branch, you can use the newly created `branch-master` tag,  
which is published on each merge on the master branch. 

NB: you should not use the `latest` image in a production deployment, otherwise you are at risk of jumping from one major release to another unexpectedly.

Thanks for reading,
Antoine