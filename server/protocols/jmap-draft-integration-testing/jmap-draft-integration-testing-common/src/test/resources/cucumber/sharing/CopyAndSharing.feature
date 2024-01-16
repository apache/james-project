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
Feature: Copy message and sharing
  As a James user
  I want that copying message work correctly with shared folder

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" has a mailbox "INBOX"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "bob@domain.tld" has a mailbox "bobMailbox"
    And "bob@domain.tld" has a mailbox "INBOX"
    And "alice@domain.tld" has a message "m1" in "INBOX" mailbox
    And "bob@domain.tld" has a message "m2" in "bobMailbox" mailbox

  Scenario: Copy message should update the total and the unread counts when asked by sharer
    Given "alice@domain.tld" copies "m1" from mailbox "INBOX" to mailbox "shared"
    When "alice@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 1 unseen message

  @BasicFeature
  Scenario: Copy message should update the total and the unread counts when asked by sharer / sharee view
    Given "alice@domain.tld" copies "m1" from mailbox "INBOX" to mailbox "shared"
    When "bob@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 1 unseen message

  Scenario: Copy message should update the total and the unread counts when asked by sharee
    Given "bob@domain.tld" copies "m2" from mailbox "bobMailbox" of user "bob@domain.tld" to mailbox "shared" of user "alice@domain.tld"
    When "alice@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 1 unseen message

  Scenario: Copy message should update the total and the unread counts when asked by sharee / sharee view
    Given "bob@domain.tld" copies "m2" from mailbox "bobMailbox" of user "bob@domain.tld" to mailbox "shared" of user "alice@domain.tld"
    When "bob@domain.tld" lists mailboxes
    Then the mailbox "shared" has 1 message
    And the mailbox "shared" has 1 unseen message

