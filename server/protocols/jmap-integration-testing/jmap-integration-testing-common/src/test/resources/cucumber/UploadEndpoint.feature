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

Feature: An upload endpoint should be available to upload contents
  As a James user
  I want to upload my attachments

  Background:
    Given a domain named "domain.tld"
    And a user "username@domain.tld"

  Scenario: An authenticated user should initiate the access to the upload endpoint
    When "username@domain.tld" checks for the availability of the upload endpoint
    Then the user should receive an authorized response

  Scenario: An unauthenticated user should initiate the access to the download endpoint
    When someone checks without authentification for the availability of the upload endpoint
    Then the user should receive an authorized response

  Scenario: Uploading a content without being authenticated
    When someone upload a content without authentification
    Then the user should receive a not authorized response

  @Ignore
  Scenario: Uploading a too big content
    When "username@domain.tld" upload a too big content
    Then the user should receive a request entity too large response

  Scenario: Stoping an upload should work
    Given "username@domain.tld" is starting uploading a content
    When the user disconnect
    Then the request should be marked as canceled

  Scenario: Uploading a content being authenticated
    When "username@domain.tld" upload a content
    Then the user should receive a created response

  Scenario: Uploading a content without content type should be denied
    When "username@domain.tld" upload a content without content type
    Then the user should receive bad request response

  Scenario: Uploading a content, the content should be retrievable
    When "username@domain.tld" upload a content
    Then "username@domain.tld" should be able to retrieve the content

  Scenario: Uploading a content, the server should respond specified JSON
    When "username@domain.tld" upload a content
    Then the user should receive a specified JSON content
    