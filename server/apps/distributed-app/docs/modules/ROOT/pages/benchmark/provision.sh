#!/bin/bash

export WEBADMIN_BASE_URL="http://localhost:8000"
export SMTP_URL="localhost:25"
export DOMAIN_NAME="domain.org"
export USERS_COUNT=10
export DUMMY_MAILBOXES_COUNT=10
export DUMMY_EMAILS_COUNT=100

# Create domain
curl -X PUT ${WEBADMIN_BASE_URL}/domains/${DOMAIN_NAME}

for i in $(seq 1 $USERS_COUNT)
do
  # Create user
   echo "Creating user $i"
   username=user${i}@$DOMAIN_NAME
   curl -XPUT ${WEBADMIN_BASE_URL}/users/$username \
     -d '{"password":"secret"}' \
     -H "Content-Type: application/json"

  # Create mailboxes for each user
   echo "Creating user $i mailboxes"
   # Create some basic mailboxes
   curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/INBOX
   curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/Outbox
   curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/Sent
   curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/Draft
   curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/Trash

   # Create some other dummy mailboxes
   for j in $(seq 1 $DUMMY_MAILBOXES_COUNT)
   do
     dummyMailbox=MAILBOX${j}
     curl -XPUT ${WEBADMIN_BASE_URL}/users/${username}/mailboxes/$dummyMailbox
   done
done

# Create many dummy randomly messages (DUMMY_EMAILS_COUNT*4 to DUMMY_EMAILS_COUNT*8 messages range)
echo "Creating various dummy emails"
smtp-source -m ${DUMMY_MAILBOXES_COUNT} -f user1@domain.org -t user2@domain.org ${SMTP_URL}
