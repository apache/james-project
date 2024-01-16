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
Feature: Mailbox creation and sharing
  As a James user
  I don't want other user to create mailbox in my mailbox even I shared it to them

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights

  Scenario: A sharee should not be able to update shared mailbox rights
    When "bob@domain.tld" shares "alice@domain.tld" delegated mailbox "shared" with rights "aeilrwt" with "bob@domain.tld"
    Then mailbox "shared" owned by "alice@domain.tld" is not updated

  Scenario: A sharee should not be able to create a shared mailbox child
    Given "bob@domain.tld" creates mailbox "sharedChild" with creationId "c-01" in mailbox "shared" owned by "alice@domain.tld"
    When "alice@domain.tld" lists mailboxes
    Then the mailboxes should contain "shared" in "Personal" namespace

  Scenario: A sharee should receive a not created response when trying to create a shared mailbox child
    When "bob@domain.tld" creates mailbox "sharedChild" with creationId "c-01" in mailbox "shared" owned by "alice@domain.tld"
    Then mailbox with creationId "c-01" is not created
