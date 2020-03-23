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

Feature: Alternative authentication mechanism for getting attachment via a POST request returning a specific authentication token
  As a James user
  I want to retrieve my attachments without an alternative authentication mechanism

  Background:
    Given a domain named "domain.tld"
    And a connected user "username@domain.tld"
    And "username@domain.tld" has a mailbox "INBOX"

  Scenario: Asking for an attachment access token with an unknown blobId
    When "username@domain.tld" asks for a token for attachment "123"
    Then the user should receive a not found response

  @BasicFeature
  Scenario: Asking for an attachment access token with a previously stored blobId
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" asks for a token for attachment "2"
    Then the user should receive an attachment access token

  Scenario: Position CORS headers
    Given "username@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "username@domain.tld" asks for a token for attachment "2"
    Then the user should receive an attachment access token
    And CORS headers are positioned