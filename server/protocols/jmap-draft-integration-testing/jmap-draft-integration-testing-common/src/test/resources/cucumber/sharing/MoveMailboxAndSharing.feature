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
Feature: Moving mailbox and sharing
  As a James user
  I want that moving shared mailbox work correctly

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" shares her mailbox "shared" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a mailbox "shared.sharedChild"
    And "alice@domain.tld" shares her mailbox "shared.sharedChild" with "bob@domain.tld" with "aeilrwt" rights
    And "alice@domain.tld" has a mailbox "otherShared"
    And "alice@domain.tld" shares her mailbox "otherShared" with "bob@domain.tld" with "aeilrwt" rights
    And "bob@domain.tld" has a mailbox "bobMailbox"

  Scenario: A sharer can not move its mailboxes to someone else delegated mailboxes
    And "bob@domain.tld" shares her mailbox "bobMailbox" with "alice@domain.tld" with "aeilrwt" rights
    When "alice@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "bobMailbox" owned by "bob@domain.tld"
    Then mailbox "shared.sharedChild" owned by "alice@domain.tld" is not updated

  Scenario: A sharee moving a delegated mailbox as top level should be rejected
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld" as top level mailbox
    Then mailbox "shared.sharedChild" owned by "alice@domain.tld" is not updated

  Scenario: A sharee moving a delegated mailbox as top level should not move mailbox
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld" as top level mailbox
    Then "alice@domain.tld" lists mailboxes
    And the mailboxes should contain "sharedChild" in "Personal" namespace, with parent mailbox "shared" of user "alice@domain.tld"

  Scenario: A sharee moving a delegated mailbox into sharer mailboxes should be rejected
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "otherShared" owned by "alice@domain.tld"
    Then mailbox "shared.sharedChild" owned by "alice@domain.tld" is not updated

  Scenario: A sharee moving a delegated mailbox into another delegated mailbox should not move mailbox
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "otherShared" owned by "alice@domain.tld"
    Then "alice@domain.tld" lists mailboxes
    And the mailboxes should contain "sharedChild" in "Personal" namespace, with parent mailbox "shared" of user "alice@domain.tld"

  Scenario: A sharee moving a delegated mailbox to his mailboxes should be rejected
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "bobMailbox" owned by "bob@domain.tld"
    Then mailbox "shared.sharedChild" owned by "alice@domain.tld" is not updated

  Scenario: A sharee moving a delegated mailbox into his mailbox should not move mailbox
    When "bob@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "bobMailbox" owned by "bob@domain.tld"
    Then "alice@domain.tld" lists mailboxes
    And the mailboxes should contain "sharedChild" in "Personal" namespace, with parent mailbox "shared" of user "alice@domain.tld"

  Scenario: A sharee should be able to retrieve a mailbox after sharer moved it as a top level mailbox
    When "alice@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld" as top level mailbox
    Then "bob@domain.tld" lists mailboxes
    And the mailboxes should contain "sharedChild" in "Delegated" namespace, with no parent mailbox

  @BasicFeature
  Scenario: A sharee should be able to retrieve a mailbox after sharer moved it into another mailbox
    Given "alice@domain.tld" has a mailbox "notShared"
    When "alice@domain.tld" moves the mailbox "shared.sharedChild" owned by "alice@domain.tld", into mailbox "notShared" owned by "alice@domain.tld"
    Then "bob@domain.tld" lists mailboxes
    And the mailboxes should contain "sharedChild" in "Delegated" namespace, with parent mailbox "notShared" of user "alice@domain.tld"

