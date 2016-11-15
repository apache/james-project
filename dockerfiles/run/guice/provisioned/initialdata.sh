#!/bin/bash

java -jar /root/james-cli.jar -h localhost -p 9999 adddomain james.local

java -jar /root/james-cli.jar -h localhost -p 9999 adduser user01@james.local 1234
java -jar /root/james-cli.jar -h localhost -p 9999 adduser user02@james.local 1234
java -jar /root/james-cli.jar -h localhost -p 9999 adduser user03@james.local 1234

