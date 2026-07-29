#!/bin/bash

set -eu

export WEBADMIN_BASE_URL="http://localhost:8000"
export DOMAIN_NAME="domain.org"
export USERS_COUNT=1000

call_webadmin() {
  if [ -n "${WEBADMIN_PASSWORD:-}" ]; then
    curl --fail --header "Password: ${WEBADMIN_PASSWORD}" "$@"
  else
    curl --fail "$@"
  fi
}

echo "Start provisioning users."

user_file="./imap-provision-conf/users.csv"

# Remove old users.csv file
if [ -e "$user_file" ]; then
  echo "Removing old users.csv file"
  rm $user_file
fi

# Create domain
call_webadmin -X PUT ${WEBADMIN_BASE_URL}/domains/${DOMAIN_NAME}

for i in $(seq 1 $USERS_COUNT)
do
  # Create user
   echo "Creating user $i"
   username=user${i}@$DOMAIN_NAME
   call_webadmin -XPUT ${WEBADMIN_BASE_URL}/users/$username \
     -d '{"password":"secret"}' \
     -H "Content-Type: application/json"

   # Append user to users.csv
   echo -e "$username,secret" >> $user_file
done

echo "Finished provisioning users."

# Provisioning IMAP mailboxes and messages.
echo "Start provisioning IMAP mailboxes and messages..."
docker run --rm -it --name james-provisioning --network host -v ./imap-provision-conf/provisioning.properties:/conf/provisioning.properties \
-v $user_file:/conf/users.csv linagora/james-provisioning:latest
echo "Finished provisioning IMAP mailboxes and messages."
