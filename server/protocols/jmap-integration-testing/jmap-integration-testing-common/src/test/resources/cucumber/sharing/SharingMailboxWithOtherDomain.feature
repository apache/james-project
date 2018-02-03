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
Feature: Delegation with other domain
  As a James user I can not share a mailbox with a other from an other domain

  Background:
    Given a domain named "domain.tld"
    And a user "alice@domain.tld"
    And "alice@domain.tld" has a mailbox "shared"

  Scenario: alice should not be able to share her mailbox with a user from an other domain
    Given a domain named "otherdomain.tld"
    And a user "bob@otherdomain.tld"
    When "alice@domain.tld" shares its mailbox "shared" with rights "lrw" with "bob@otherdomain.tld"
    Then "alice@domain.tld" receives not updated on mailbox "shared" with kind "invalidArguments" and message "Cannot share a mailbox to another domain"
