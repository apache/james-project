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
Feature: Impact of IMAP on JMAP consistency

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "source"
    And "username@domain.tld" has a mailbox "trash"

  @BasicFeature
  Scenario: Two messages copied in one step via IMAP should be seen via JMAP
    Given the user has a message "m1" in "source" mailbox with subject "My awesome subject 1", content "This is the content 1"
    And the user has a message "m2" in "source" mailbox with subject "My awesome subject 2", content "This is the content 2"
    And the user has an open IMAP connection with mailbox "source" selected
    And the user copies via IMAP all messages from mailbox "source" to mailbox "trash"
    When "username@domain.tld" asks for message list in mailbox "trash"
    Then the message list has size 2
    And the message list contains "m1"
    And the message list contains "m2"