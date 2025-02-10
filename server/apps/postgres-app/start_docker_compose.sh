#!/bin/bash

# To add this script as a cronjob,run 'crontab -e' and add the following line with the appropriate paths
# @reboot /path/to/directory/start_docker_compose.sh /path/to/directory >> /path/to/save/log/cronlog.log 2>&1

if [ $# -ne 1 ]; then
  echo "Usage: $0 <directory>"
  exit 1
fi

TARGET_DIR="$1"

cd "$TARGET_DIR" || exit

while ! systemctl is-active --quiet docker; do
  sleep 1
done

/usr/bin/docker compose -f docker-compose-distributed.yml down
/usr/bin/docker compose -f docker-compose-distributed.yml up -d

check_health() {
  while true; do
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/healthcheck)
    if [[ "$response" -eq 200 ]]; then
      echo "Health check passed: Received 200 response."
      break
    else
      echo "Health check failed: Received $response response. Retrying..."
      sleep 5
    fi
  done
}

check_health

/usr/bin/docker exec james james-cli AddDomain cloud.appscode.com
/usr/bin/docker exec james james-cli AddUser recipient.acc@cloud.appscode.com password
