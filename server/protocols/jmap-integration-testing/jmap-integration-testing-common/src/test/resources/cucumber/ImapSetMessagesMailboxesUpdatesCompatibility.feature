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

Feature: IMAP compatibility of JMAP setMessages method used to update mailboxes
  As a James user
  I want to be able to access by IMAP messages moved and copied by JMAP

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "source"
    And "username@domain.tld" has a mailbox "mailbox"
    And "username@domain.tld" has a mailbox "trash"

  Scenario: A message moved by JMAP is seen as moved by IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user move "m1" to mailbox "mailbox"
    Then the user has a IMAP message in mailbox "mailbox"
    And the user does not have a IMAP message in mailbox "source"

  Scenario: A message copied by JMAP is seen as copied by IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user copy "m1" from mailbox "source" to mailbox "mailbox"
    Then the user has a IMAP message in mailbox "mailbox"
    And the user has a IMAP message in mailbox "source"

  Scenario: If a message is moved by JMAP, IMAP client will be notified when selecting mailbox
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    When the user move "m1" to mailbox "mailbox"
    Then the user has a IMAP notification about 1 new message when selecting mailbox "mailbox"

  Scenario: If a message is moved by JMAP, IMAP client that have selected the destination mailbox will be notified
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    Given the user has an open IMAP connection with mailbox "mailbox" selected
    When the user move "m1" to mailbox "mailbox"
    Then mailbox "mailbox" contains 1 messages
    Then the user has a IMAP RECENT and a notification about 1 new messages on connection for mailbox "mailbox"

  Scenario: If a message is copied by IMAP, JMAP should see the message and IMAP client that have selected the destination mailbox will be notified
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    Given the user has an open IMAP connection with mailbox "mailbox" selected
    When the user copy by IMAP first message of "source" to mailbox "mailbox"
    Then mailbox "mailbox" contains 1 messages
    Then the user has a IMAP RECENT and a notification about 1 new messages on connection for mailbox "mailbox"


  Scenario: If a message is moved by JMAP, IMAP client that have selected the source mailbox will not be notified
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    Given the user has an open IMAP connection with mailbox "source" selected
    When the user move "m1" to mailbox "mailbox"
    Then the user has IMAP EXPUNGE and a notification for 1 message sequence number on connection for mailbox "source"