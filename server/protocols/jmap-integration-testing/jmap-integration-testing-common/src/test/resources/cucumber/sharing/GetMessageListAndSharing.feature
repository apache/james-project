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
Feature: Listing message from shared mailbox
  As a James user
  I want to be able to see message from mailbox that has been shared to me or from mailbox I shared

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And a user "bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a mailbox "shared2"

  Scenario: Sharer can read the total and unread counts on a shared folder
    Given "alice@domain.tld" has a message "m1" in "shared" mailbox
    And "alice@domain.tld" has a message "m2" in "shared" mailbox with subject "my test subject 2", content "testmail 2"
    When "alice@domain.tld" lists mailboxes
    Then the mailbox "shared" has 2 messages
    And the mailbox "shared" has 2 unseen messages

  @BasicFeature
  Scenario: Sharee can read the total and unread counts on a shared folder
    Given "alice@domain.tld" has a message "m1" in "shared" mailbox
    And "alice@domain.tld" has a message "m2" in "shared" mailbox with subject "my test subject 2", content "testmail 2"
    When "bob@domain.tld" lists mailboxes
    Then the mailbox "shared" has 2 messages
    And the mailbox "shared" has 2 unseen messages

  Scenario: As sharee read a message it should not update unseen count when no rights and read by sharee
    Given "alice@domain.tld" shares her mailbox "shared2" with "bob@domain.tld" with "lri" rights
    And "alice@domain.tld" has a message "m1" in "shared2" mailbox
    When "bob@domain.tld" sets flags "$Seen" on message "m1"
    When "bob@domain.tld" lists mailboxes
    Then the mailbox "shared2" has 1 message
    And the mailbox "shared2" has 1 unseen message

  Scenario: Lookup right should not be enough to read message and unseen counts
    Given "alice@domain.tld" shares her mailbox "shared2" with "bob@domain.tld" with "l" rights
    And "alice@domain.tld" has a message "m1" in "shared2" mailbox
    When "bob@domain.tld" lists mailboxes
    Then the mailbox "shared2" has 0 messages
    And the mailbox "shared2" has 0 unseen messages

