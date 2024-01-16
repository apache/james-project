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
Feature: Set flag and sharing
  As a James user
  I want that set flag work correctly with shared mailbox

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a message "m1" in "shared" mailbox

  Scenario: Set flags by sharer should update unseen count when read by sharer
    When "alice@domain.tld" sets flags "$Seen" on message "m1"
    And "alice@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 0 unseen messages

  @BasicFeature
  Scenario: Set flags by sharer should update unseen count when read by sharee
    When "alice@domain.tld" sets flags "$Seen" on message "m1"
    And "bob@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 0 unseen messages

  Scenario: Set flags by sharee should update unseen count when read by sharer
    When "bob@domain.tld" sets flags "$Seen" on message "m1"
    And "alice@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 0 unseen messages

  Scenario: Set flags by sharee should update unseen count when read by sharee
    When "bob@domain.tld" sets flags "$Seen" on message "m1"
    And "bob@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 0 unseen messages

  Scenario: Set flags by sharee should not update unseen count when no rights and read by sharer
    Given "alice@domain.tld" has a mailbox "shared2"
    And "alice@domain.tld" shares her mailbox "shared2" with "bob@domain.tld" with "lri" rights
    And "alice@domain.tld" has a message "m2" in "shared2" mailbox
    When "bob@domain.tld" sets flags "$Seen" on message "m2"
    And "alice@domain.tld" lists mailboxes
    Then the mailbox "shared2" has 1 message
    And the mailbox "shared2" has 1 unseen message
