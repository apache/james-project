#***************************************************************
# Licensed to the Apache Software Foundation (ASF) under one   *
# or more contributor license agreements.  See the NOTICE file *
# distributed with this work for additional information        *
# regarding copyright ownership.  The ASF licenses this file   *
# to you under the Apache License, Version 2.0 (the            *
# "License"); you may not use this file except in compliance   *
# with the License.  You may obtain a copy of the License at   *
#                                                              *
#   http://www.apache.org/licenses/LICENSE-2.0                 *
#                                                              *
# Unless required by applicable law or agreed to in writing,   *
# software distributed under the License is distributed on an  *
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
# KIND, either express or implied.  See the License for the    *
# specific language governing permissions and limitations      *
# under the License.                                           *
# **************************************************************/

Feature: Download GET
  As a James user
  I want to retrieve my blobs (attachments and messages)

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "INBOX"
    And "username@domain.tld" has a mailbox "sharedMailbox"

  Scenario: Getting an attachment previously stored
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2"
    Then the user should receive that blob
    And the blob size is 3071

  Scenario: Getting an attachment with an unknown blobId
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with a valid authentication token but a bad blobId
    Then the user should receive a not found response

  Scenario: Getting an attachment previously stored with a desired name
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with "myFileName.txt" name
    Then the user should receive that blob
    And the attachment is named "myFileName.txt"

  Scenario: Getting an attachment previously stored with a non ASCII name
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" downloads "2" with "ديناصور.odt" name
    Then the user should receive that blob
    And the attachment is named "ديناصور.odt"

  Scenario: Getting a message blob previously stored
    Given "username@domain.tld" mailbox "INBOX" contains a message "1"
    When "username@domain.tld" downloads "1"
    Then the user should receive that blob
    And the blob size is 4963

  Scenario: Getting a message then getting its blob
    Given the user has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    And the user ask for messages "m1"
    When "username@domain.tld" downloads the message by its blobId
    Then the user should receive that blob
    And the blob size is 36

  Scenario: Deleted message should revoke attachment blob download rights
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    And "username@domain.tld" delete mailbox "INBOX"
    When "username@domain.tld" downloads "2"
    Then the user should receive a not found response

  Scenario: User cannot download attachment of another user
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    And a connected user "username1@domain.tld"
    And "username1@domain.tld" has a mailbox "INBOX"
    When "username1@domain.tld" downloads "2"
    Then the user should receive a not found response

  Scenario: User cannot download message blob of another user
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    And a connected user "username1@domain.tld"
    And "username1@domain.tld" has a mailbox "INBOX"
    When "username1@domain.tld" downloads "1"
    Then the user should receive a not found response

  Scenario: User can download attachment of another user when shared mailbox
    Given "username@domain.tld" mailbox "sharedMailbox" contains a message "1" with an attachment "2"
    And "username@domain.tld" shares its mailbox "sharedMailbox" with "username1@domain.tld"
    And a connected user "username1@domain.tld"
    And "username1@domain.tld" has a mailbox "sharedMailbox"
    When "username1@domain.tld" downloads "2"
    Then the user should receive that blob
    And the blob size is 3071

  Scenario: User can download message blob of another user when shared mailbox
    Given "username@domain.tld" mailbox "sharedMailbox" contains a message "1" with an attachment "2"
    And "username@domain.tld" shares its mailbox "sharedMailbox" with "username1@domain.tld"
    And a connected user "username1@domain.tld"
    And "username1@domain.tld" has a mailbox "sharedMailbox"
    When "username1@domain.tld" downloads "1"
    Then the user should receive that blob
    And the blob size is 4963