#!/bin/bash
set -m

/rule-update.sh &
/spamd.sh &

pids=`jobs -p`

wait
