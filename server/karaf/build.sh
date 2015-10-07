#!/bin/sh

(cd features && mvn clean install)
(cd integration && mvn clean install)
(cd distribution && mvn clean install)
