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
Feature: GetMessages method on shared mailbox
  As a James user
  I want to be able to retrieve messages contained by mailbox that has been shared to me

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld, someone@domain.tld"
    And "alice@domain.tld" has a mailbox "INBOX"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "lr" rights
    And "alice@domain.tld" has a message "m1" in "shared" mailbox with subject "my test subject", content "testmail"

  @BasicFeature
  Scenario: Retrieving a message in a mailbox delegated to me
    When "bob@domain.tld" ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"

  Scenario: Retrieving a message in a mailbox delegated to someone else
    When "someone@domain.tld" ask for message "m1"
    Then no error is returned
    And the list of messages is empty

  Scenario: Retrieving a message in a mailbox not delegated to me
    Given "alice@domain.tld" has a mailbox "notShared"
    And "alice@domain.tld" has a message "m2" in "notShared" mailbox with subject "my test subject", content "testmail"
    When "bob@domain.tld" ask for message "m2"
    Then no error is returned
    And the list should contain 0 message

