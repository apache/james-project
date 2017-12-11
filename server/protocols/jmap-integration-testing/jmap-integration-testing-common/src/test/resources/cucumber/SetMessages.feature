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
# specific language governing permissions and limitations      * # under the License.                                           *
# **************************************************************/
Feature: SetMessages method
  As a James user
  I want to be able to modify properties of a mail

  Background:
    Given a domain named "domain.tld"
    And a user "bob@domain.tld"
    And "bob@domain.tld" has a mailbox "mailbox"
    And "bob@domain.tld" has a message "mBob" in "mailbox" mailbox with two attachments in text

# Flags update

  Scenario: A user can update the flags on a message
    Given "bob@domain.tld" sets flags "$Flagged,$Seen" on message "mBob"
    When "bob@domain.tld" sets flags "$Flagged,$Forwarded" on message "mBob"
    Then "bob@domain.tld" should see message "mBob" with keywords "$Flagged,$Forwarded"

# Updating draft

  Scenario: A user can update the flags on a draft
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" sets flags "$Draft,$Seen" on message "mDraft"
    Then "bob@domain.tld" should see message "mDraft" with keywords "$Draft,$Seen"

  Scenario: A user can remove a draft flag on a draft messages
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" sets flags "$Seen" on message "mDraft"
    Then message "mDraft" is updated
    And "bob@domain.tld" should see message "mDraft" with keywords "$Seen"

  Scenario: A user can add a flag on a draft
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" marks the message "mDraft" as flagged
    Then "bob@domain.tld" should see message "mDraft" with keywords "$Draft,$Flagged"

  Scenario: A user can destroy a draft
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" destroys message "mDraft"
    Then "bob@domain.tld" ask for message "mDraft"
    And the notFound list should contain "mDraft"

  Scenario: Draft creation in outbox is allowed
    Given "bob@domain.tld" has a mailbox "Outbox"
    When "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Outbox"
    Then message "mDraft" is created

  Scenario: Draft creation in any mailbox is allowed
    Given "bob@domain.tld" has a mailbox "Outbox"
    When "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "mailbox"
    Then message "mDraft" is created

  Scenario: A user can move draft out of draft mailbox
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" moves "mDraft" to user mailbox "mailbox"
    Then message "mDraft" is updated

  Scenario: A user can move draft out of draft mailbox when removing draft flag
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When the user moves "mDraft" to user mailbox "mailbox" and set flags ""
    Then message "mDraft" is updated

  Scenario: A user can move non-draft messages to draft mailbox when setting $Draft
    Given "bob@domain.tld" has a mailbox "Drafts"
    When the user moves "mBob" to user mailbox "Drafts" and set flags "$Draft"
    Then message "mBob" is updated

  Scenario: A user can copy draft out of draft mailbox
    Given "bob@domain.tld" has a mailbox "Drafts"
    And "bob@domain.tld" tries to create a draft message "mDraft" in mailbox "Drafts"
    When "bob@domain.tld" copies "mDraft" from mailbox "Drafts" to mailbox "mailbox"
    Then message "mDraft" is updated

  Scenario: A user can copy draft out of draft mailbox
    Given "bob@domain.tld" has a mailbox "Drafts"
    When "bob@domain.tld" moves "mBob" to user mailbox "Drafts"
    Then message "mBob" is updated

  Scenario: A user can update $Draft keyword using isDraft property
    Given "bob@domain.tld" has a mailbox "Drafts"
    When "bob@domain.tld" marks the message "mBob" as draft
    Then message "mBob" is updated

  Scenario: A user can copy draft out of draft mailbox
    Given "bob@domain.tld" has a mailbox "Drafts"
    When "bob@domain.tld" copies "mBob" from mailbox "mailbox" to mailbox "Drafts"
    Then message "mBob" is updated