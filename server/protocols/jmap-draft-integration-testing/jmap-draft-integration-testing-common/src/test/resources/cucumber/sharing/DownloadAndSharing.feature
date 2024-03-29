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
Feature: Download endpoint and shared mailbox
  As a James user
  I want to access to the download endpoint in order to download attachment from mail of mailbox that has been shared to me

  Background:
    Given a domain named "domain.tld"
    And some users "alice@domain.tld, bob@domain.tld, someone@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"
    And "alice@domain.tld" mailbox "shared" contains a message "m1" with an attachment "a1"
    And "alice@domain.tld" shares his mailbox "shared" with "bob@domain.tld" with "lr" rights

  Scenario: Bob should have access to a shared attachment
    Given "bob@domain.tld" is connected
    When "bob@domain.tld" downloads "a1"
    Then the user should be authorized

  @BasicFeature
  Scenario: Bob can download attachment of another user when shared mailbox
    When "bob@domain.tld" downloads "a1"
    Then he can read that blob
    And the blob size is 3071

  Scenario: Someone should not be able to download mail's attachment of mailbox shared to others
    When "someone@domain.tld" downloads "a1"
    Then "someone@domain.tld" should receive a not found response
