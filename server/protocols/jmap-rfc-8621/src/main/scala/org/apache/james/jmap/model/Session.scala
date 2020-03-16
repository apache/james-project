/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.model

import org.apache.james.core.Username

import CapabilityIdentifier.JMAP_CORE
import CapabilityIdentifier.JMAP_MAIL

final case class Account(name: Username,
                         isPersonal: Boolean,
                         isReadOnly: Boolean,
                         accountCapabilities: Map[CapabilityIdentifier, Capability]) {
  require(Option(name).isDefined, "name cannot be null")
  require(Option(accountCapabilities).isDefined, "accountCapabilities cannot be null")
}

case class Session(capabilities: Map[CapabilityIdentifier, Capability],
                   accounts: Map[Id, Account],
                   primaryAccounts: Map[CapabilityIdentifier, Id],
                   username: Username,
                   apiUrl: String,
                   downloadUrl: String,
                   uploadUrl: String,
                   eventSourceUrl: String,
                   state: String) {
  require(Option(capabilities).isDefined, "capabilities cannot be null")
  require(capabilities.exists(
    record => record._1.equals(JMAP_CORE) && record._2.isInstanceOf[CoreCapability]),
    s"capabilities should contain ${JMAP_CORE.value.toString} capability")
  require(capabilities.exists(
    record => record._1.equals(JMAP_MAIL) && record._2.isInstanceOf[MailCapability]),
    s"capabilities should contain ${JMAP_MAIL.value.toString} capability")

  require(Option(accounts).isDefined, "accounts cannot be null")
  require(Option(primaryAccounts).isDefined, "primaryAccounts cannot be null")
  require(Option(username).isDefined, "username cannot be null")
  require(Option(apiUrl).isDefined, "apiUrl cannot be null")
  require(Option(downloadUrl).isDefined, "downloadUrl cannot be null")
  require(Option(uploadUrl).isDefined, "uploadUrl cannot be null")
  require(Option(eventSourceUrl).isDefined, "eventSourceUrl cannot be null")
  require(Option(state).isDefined, "state cannot be null")
}
