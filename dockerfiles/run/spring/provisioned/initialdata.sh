#!/bin/bash

./james-cli.sh -h localhost -p 9999 adddomain james.local
./james-cli.sh -h localhost -p 9999 adduser user01@james.local 1234
./james-cli.sh -h localhost -p 9999 adduser user02@james.local 1234
./james-cli.sh -h localhost -p 9999 adduser user03@james.local 1234




