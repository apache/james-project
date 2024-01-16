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
Feature: Download GET
  As a James user
  I want to retrieve my blobs (attachments and messages)

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And a user "bob@domain.tld"
    And "alice@domain.tld" has a mailbox "INBOX"

  @BasicFeature
  Scenario: Getting an attachment previously stored
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "alice@domain.tld" downloads "2"
    Then she can read that blob
    And the blob size is 3071

  Scenario: Getting an attachment with an unknown blobId
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "alice@domain.tld" downloads "2" with a valid authentication token but a bad blobId
    Then "alice@domain.tld" should receive a not found response

  Scenario: Getting an attachment previously stored with a desired name
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "alice@domain.tld" downloads "2" with "myFileName.txt" name
    Then she can read that blob
    And the attachment is named "myFileName.txt"

  Scenario: Getting an attachment previously stored with a non ASCII name
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "alice@domain.tld" downloads "2" with "ديناصور.odt" name
    Then she can read that blob
    And the attachment is named "ديناصور.odt"

  Scenario: Getting an attachment with a specified Content-Type
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2" having "application/pdf" contentType
    When "alice@domain.tld" downloads "2" with "myFileName.txt" name
    Then she can read that blob
    And the Content-Type is "application/pdf"

  @BasicFeature
  Scenario: Getting a message blob previously stored
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1"
    When "alice@domain.tld" downloads "1"
    Then she can read that blob
    And the blob size is 4963

  Scenario: Content-Length header should be positioned before transfers starts
    Given "alice@domain.tld" mailbox "INBOX" contains a big message "1"
    When "alice@domain.tld" downloads "1"
    Then the blob size is 2621440

  Scenario: Getting a message then getting its blob
    Given "alice@domain.tld" has a message "m1" in "INBOX" mailbox with subject "my test subject", content "testmail"
    And "alice@domain.tld" ask for message "m1"
    When "alice@domain.tld" downloads the message by its blobId
    Then she can read that blob
    And the blob size is 36

  Scenario: Position CORS headers
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1"
    When "alice@domain.tld" downloads "1"
    Then she can read that blob
    And CORS headers are positioned

  Scenario: Deleted message should revoke attachment blob download rights
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    And "alice@domain.tld" delete mailbox "INBOX"
    When "alice@domain.tld" downloads "2"
    Then "alice@domain.tld" should receive a not found response

  Scenario: User cannot download attachment of another user
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "bob@domain.tld" downloads "2"
    Then "alice@domain.tld" should receive a not found response

  Scenario: User cannot download message blob of another user
    Given "alice@domain.tld" mailbox "INBOX" contains a message "1" with an attachment "2"
    When "bob@domain.tld" downloads "1"
    Then "bob@domain.tld" should receive a not found response

