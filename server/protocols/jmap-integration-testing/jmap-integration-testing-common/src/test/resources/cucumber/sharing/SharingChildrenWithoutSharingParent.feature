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
Feature: Sharing children mailbox
  As a James user
  I want to be able to share a children mailbox without sharing it's parent one

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And a user "bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a mailbox "mailbox1"
    And "alice@domain.tld" has a mailbox "mailbox1.shared"

  Scenario: User can share sub-mailbox without sharing its parent
    Given "alice@domain.tld" shares her mailbox "mailbox1.shared" with "bob@domain.tld" with "aeirwt" rights
    When "bob@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Delegated" namespace
    And the mailboxes should not contain "mailbox1"

  Scenario: User can share sub-mailbox without sharing its parent and then sharee can see the parent mailbox
    Given "alice@domain.tld" shares her mailbox "mailbox1.shared" with "bob@domain.tld" with "l" rights
    When "bob@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Delegated" namespace
    And the mailboxes should contain "mailbox1" in "Delegated" namespace
