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
Feature: Renaming mailbox and sharing
  As a James user
  I want that renaming shared mailbox work properly

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights

  @BasicFeature
  Scenario: A sharee should be able to access a shared mailbox after it has been renamed by the owner
    Given "alice@domain.tld" renames her mailbox "shared" to "mySharedMailbox"
    When "bob@domain.tld" lists mailboxes
    Then the mailboxes should contain "mySharedMailbox" in "Delegated" namespace

  Scenario: A sharee should not be able to rename a shared mailbox
    Given "bob@domain.tld" renames the mailbox, owned by "alice@domain.tld", "shared" to "mySharedMailbox"
    When "alice@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Personal" namespace

  Scenario: A user should not be able to rename an other user mailbox
    Given "alice@domain.tld" has a mailbox "mySecrets"
    And "bob@domain.tld" renames the mailbox, owned by "alice@domain.tld", "mySecrets" to "revealMySecrets"
    When "alice@domain.tld" lists mailboxes
    Then the mailboxes should contain "mySecrets" in "Personal" namespace

  Scenario: A sharee should receive a not updated response when trying to rename a shared mailbox
    Given "bob@domain.tld" renames the mailbox, owned by "alice@domain.tld", "shared" to "mySharedMailbox"
    Then mailbox "shared" owned by "alice@domain.tld" is not updated

