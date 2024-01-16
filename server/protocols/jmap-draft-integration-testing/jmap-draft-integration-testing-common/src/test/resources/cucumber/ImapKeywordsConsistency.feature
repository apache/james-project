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
Feature: Impact of IMAP on JMAP keywords consistency

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "source"
    And "username@domain.tld" has a mailbox "mailbox"
    And "username@domain.tld" has a mailbox "trash"

  Scenario Outline: GetMessages should union keywords when an inconsistency was created via IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "<mailbox>" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "<mailbox>"
    When the user ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the keywords of the message is <keyword>

    Examples:
      |keyword                 | mailbox |
      |$Flagged                | mailbox |
      |$Flagged                | source  |

  Scenario Outline: GetMessages should intersect Draft when an inconsistency was created via IMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "<mailbox>" selected
    And the user set flags via IMAP to "(\Draft)" for all messages in mailbox "<mailbox>"
    When the user ask for message "m1"
    Then no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the keywords of the message is <keyword>

    Examples:
      |keyword     | mailbox |
      |            | mailbox |
      |            | source  |

  Scenario: GetMessageList should return matching messageId when matching in at least 1 mailbox
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When the user asks for message list with flag "$Flagged"
    Then the message list has size 1
    And the message list contains "m1"

  Scenario: GetMessageList in specific mailbox should return messageId when matching
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" asks for message list in mailbox "mailbox" with flag "$Flagged"
    Then the message list has size 1
    And the message list contains "m1"

  Scenario: GetMessageList in specific mailbox should skip messageId when not matching
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" asks for message list in mailbox "source" with flag "$Flagged"
    Then the message list is empty

  Scenario: SetMessages should succeed to solve Keywords conflict introduced via IMAP upon flags addition (GetMessageList)
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" sets flags "$Flagged" on message "m1"
    Then "username@domain.tld" asks for message list in mailbox "mailbox" with flag "$Flagged"
    And the message list has size 1
    And the message list contains "m1"
    And "username@domain.tld" asks for message list in mailbox "source" with flag "$Flagged"
    And the message list has size 1
    And the message list contains "m1"

  Scenario: SetMessages should succeed to solve Keywords conflict introduced via IMAP upon flags addition with GetMessages
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" sets flags "$Flagged" on message "m1"
    Then the user ask for message "m1"
    And no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the keywords of the message is $Flagged

  Scenario: SetMessages should ignore Keywords conflict introduced via IMAP upon flags deletion with GetMessageList
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" sets flags "$Answered" on message "m1"
    Then "username@domain.tld" asks for message list in mailbox "mailbox" with flag "$Flagged"
    And the message list is empty
    And "username@domain.tld" asks for message list in mailbox "source" with flag "$Flagged"
    And the message list is empty
    Then "username@domain.tld" asks for message list in mailbox "mailbox" with flag "$Answered"
    And the message list has size 1
    And the message list contains "m1"
    And "username@domain.tld" asks for message list in mailbox "source" with flag "$Answered"
    And the message list has size 1
    And the message list contains "m1"

  Scenario: SetMessages should ignore Keywords conflict introduced via IMAP upon flags deletion with GetMessages
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject", content "This is the content"
    And "username@domain.tld" copies "m1" from mailbox "source" to mailbox "mailbox"
    And the user has an open IMAP connection with mailbox "mailbox" selected
    And the user set flags via IMAP to "(\Flagged)" for all messages in mailbox "mailbox"
    When "username@domain.tld" sets flags "$Answered" on message "m1"
    Then the user ask for message "m1"
    And no error is returned
    And the list should contain 1 message
    And the id of the message is "m1"
    And the keywords of the message is $Answered