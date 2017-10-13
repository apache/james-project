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

Feature: Mailbox modification
  As a James user
  I want a mailbox to be modified when I modify it
  I want my mails to be kept when modifying a mailbox

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"

  Scenario: Renaming a mailbox should keep messages
    Given mailbox "A" with 2 messages
    When renaming mailbox "A" to "B"
    Then mailbox "B" contains 2 messages

  Scenario: Moving a mailbox should keep messages
    Given mailbox "A" with 2 messages
    And mailbox "A.B" with 3 messages
    And mailbox "B" with 4 messages
    When moving mailbox "A.B" to "B"
    Then mailbox "B.B" contains 3 messages
