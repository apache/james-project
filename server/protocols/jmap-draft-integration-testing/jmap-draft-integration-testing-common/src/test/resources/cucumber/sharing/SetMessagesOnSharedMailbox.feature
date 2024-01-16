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
Feature: SetMessages method on shared folders
  As a James user
  I want to be able to modify properties of a shared mail

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld"
    And "bob@domain.tld" has a mailbox "shared"
    And "bob@domain.tld" has a mailbox "Outbox"
    And "alice@domain.tld" has a mailbox "INBOX"
    And "bob@domain.tld" has a message "mBob" in "shared" mailbox with two attachments in text
    And "alice@domain.tld" has a message "mAlice" in "INBOX" mailbox with two attachments in text

  @BasicFeature
  Scenario: A delegated user can copy messages from shared mailbox when having "read" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lr" rights
    And "alice@domain.tld" copies "mBob" from mailbox "shared" of user "bob@domain.tld" to mailbox "INBOX" of user "alice@domain.tld"
    Then "alice@domain.tld" should see message "mBob" in mailboxes:
        |alice@domain.tld |INBOX  |
        |bob@domain.tld   |shared |

  @BasicFeature
  Scenario: A delegated user can move messages out of shared mailbox when having "read" and "delete" rights
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lrte" rights
    And "alice@domain.tld" moves "mBob" to mailbox "INBOX" of user "alice@domain.tld"
    Then "alice@domain.tld" should see message "mBob" in mailboxes:
        |alice@domain.tld |INBOX  |

  @BasicFeature
  Scenario: A delegated user can add messages to a shared mailbox when having "insert" rights
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lri" rights
    And "alice@domain.tld" copies "mAlice" from mailbox "INBOX" of user "alice@domain.tld" to mailbox "shared" of user "bob@domain.tld"
    Then "alice@domain.tld" should see message "mAlice" in mailboxes:
        |alice@domain.tld |INBOX  |
        |bob@domain.tld   |shared |

  Scenario: A delegated user can add messages with keywords to a shared mailbox when having "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lriws" rights
    And "alice@domain.tld" sets flags "$Flagged" on message "mAlice"
    And "alice@domain.tld" moves "mAlice" to mailbox "shared" of user "bob@domain.tld"
    Then "alice@domain.tld" should see message "mAlice" with keywords "$Flagged"

  Scenario: A delegated user can add sanitized messages to a shared mailbox when missing "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lri" rights
    And "alice@domain.tld" sets flags "$Flagged" on message "mAlice"
    And "alice@domain.tld" moves "mAlice" to mailbox "shared" of user "bob@domain.tld"
    Then "alice@domain.tld" should see message "mAlice" without keywords

  Scenario: A delegated user can not copy messages from shared mailbox when missing "read" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "litewsa" rights
    When "alice@domain.tld" copies "mBob" from mailbox "shared" of user "bob@domain.tld" to mailbox "INBOX" of user "alice@domain.tld"
    Then message "mBob" is not updated

  Scenario: A delegated user can not copy messages to shared mailbox when missing "insert" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lrtewsa" rights
    When "alice@domain.tld" copies "mAlice" from mailbox "INBOX" of user "alice@domain.tld" to mailbox "shared" of user "bob@domain.tld"
    Then message "mAlice" is not updated

  Scenario: A delegated user can not move messages out of shared mailbox when missing "delete" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lriwsa" rights
    When "alice@domain.tld" moves "mBob" to mailbox "INBOX" of user "alice@domain.tld"
    Then message "mBob" is not updated

  @BasicFeature
  Scenario: A delegated user add keywords on a delegated message when having "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lrw" rights
    When "alice@domain.tld" sets flags "$Flagged" on message "mBob"
    Then "alice@domain.tld" should see message "mBob" with keywords "$Flagged"

  Scenario: A delegated user can not add keywords on a delegated message when missing "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "latires" rights
    When "alice@domain.tld" sets flags "$Flagged" on message "mBob"
    Then message "mBob" is not updated

  @BasicFeature
  Scenario: A delegated user remove keywords on a delegated message when having "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "lrw" rights
    And "bob@domain.tld" sets flags "$Flagged" on message "mBob"
    When "alice@domain.tld" sets flags "" on message "mBob"
    Then "alice@domain.tld" should see message "mBob" without keywords

  Scenario: A delegated user can not remove keywords on a delegated message when missing "write" right
    Given "bob@domain.tld" shares his mailbox "shared" with "alice@domain.tld" with "latires" rights
    And "bob@domain.tld" sets flags "$Flagged" on message "mBob"
    When "alice@domain.tld" sets flags "" on message "mBob"
    Then message "mBob" is not updated

  Scenario: A delegated user can not move draft from draft mailbox to outbox
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "alice@domain.tld" moves "mDraft" to mailbox "Outbox" of user "bob@domain.tld"
    Then message "mDraft" is not updated
    And message "mBob" has flags $Draft in mailbox "Drafts" of user "bob@domain.tld"

