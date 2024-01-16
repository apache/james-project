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
Feature: Share parent mailbox without sharing submailbox
  As a James user
  I want to be able to retrieve my mailboxes

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And a user "bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" has a mailbox "shared.secret"
    And "alice@domain.tld" has a mailbox "shared.notsecret"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" shares her mailbox "shared.notsecret" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a message "m1" in "shared" mailbox
    And "alice@domain.tld" has a message "m2" in "shared.secret" mailbox
    And "alice@domain.tld" has a message "m3" in "shared.notsecret" mailbox

  Scenario: Bob can see shared mailbox and explicitly shared children mailbox
    When "bob@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Delegated" namespace
    And the mailboxes should contain "notsecret" in "Delegated" namespace, with parent mailbox "shared" of user "alice@domain.tld"
    And the mailbox "shared" has 1 messages
    And the mailbox "notsecret" has 1 messages

  Scenario: Bob can not see not explicitly shared children mailbox
    When "bob@domain.tld" lists mailboxes
    Then the mailboxes should not contain "shared.secret" in "Delegated" namespace
    And the mailboxes should not contain "secret" in "Personal" namespace
    And the mailboxes should not contain "secret" in "Delegated" namespace, with parent mailbox "shared" of user "alice@domain.tld"

  Scenario: Alice can see all her shared mailbox
    When "alice@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Personal" namespace
    And the mailboxes should contain "notsecret" in "Personal" namespace, with parent mailbox "shared" of user "alice@domain.tld"
    And the mailbox "shared" has 1 messages
    And the mailbox "notsecret" has 1 messages

  Scenario: Alice can see all her not shared mailbox
    When "alice@domain.tld" lists mailboxes
    Then the mailboxes should contain "secret" in "Personal" namespace, with parent mailbox "shared" of user "alice@domain.tld"
    And the mailbox "secret" has 1 messages

  Scenario: Alice can get message from her root shared mailbox
    When "alice@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message

  Scenario: Alice can get message from her children shared mailbox
    When "alice@domain.tld" ask for message "m3"
    Then no error is returned
    And the list should contain 1 message

  Scenario: Alice can get message from her not shared mailbox
    When "alice@domain.tld" ask for message "m2"
    Then no error is returned
    And the list should contain 1 message

  Scenario: Bob can get message from root shared mailbox
    When "bob@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message

  Scenario: Bob can get message from children shared mailbox
    When "bob@domain.tld" ask for message "m3"
    Then no error is returned
    And the list should contain 1 message

  Scenario: Bob can not get message from mailbox not shared with him
    When "bob@domain.tld" ask for message "m2"
    Then the list should contain 0 message

  Scenario: Alice can list message from her shared root mailbox
    When "alice@domain.tld" asks for message list in mailbox "shared"
    Then the message list has size 1

  Scenario: Alice can list message from her shared children mailbox
    When "alice@domain.tld" asks for message list in mailbox "shared.notsecret"
    Then the message list has size 1

  Scenario: Alice can list message from all her mailbox she is not sharing
    When "alice@domain.tld" asks for message list in mailbox "shared.secret"
    Then the message list has size 1

  Scenario: Bob can list message from root mailbox that are shared to him
    When "bob@domain.tld" asks for message list in delegated mailbox "shared" from "alice@domain.tld"
    Then the message list has size 1

  Scenario: Bob can list message from children mailbox that are shared to him
    When "bob@domain.tld" asks for message list in delegated mailbox "shared.notsecret" from "alice@domain.tld"
    Then the message list has size 1

  Scenario: Bob can not list message from mailbox that are not shared to him
    When "bob@domain.tld" asks for message list in delegated mailbox "shared.secret" from "alice@domain.tld"
    Then the message list has size 0
