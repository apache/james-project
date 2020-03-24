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

import java.net.URL

import org.apache.james.core.Username
import CapabilityIdentifier.JMAP_CORE
import CapabilityIdentifier.JMAP_MAIL
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.State.State

object Account {
  def apply(name: Username,
            isPersonal: Boolean,
            isReadOnly: Boolean,
            accountCapabilities: Set[_ <: Capability]): Account = {

    new Account(name, isPersonal, isReadOnly, accountCapabilities)
  }
}

final case class Account private(name: Username,
                                 isPersonal: Boolean,
                                 isReadOnly: Boolean,
                                 accountCapabilities: Set[_ <: Capability])

object State {
  type State = String Refined NonEmpty
}


object Session {
  def apply(capabilities: Set[_ <: Capability],
            accounts: Map[Id, Account],
            primaryAccounts: Map[CapabilityIdentifier, Id],
            username: Username,
            apiUrl: URL,
            downloadUrl: URL,
            uploadUrl: URL,
            eventSourceUrl: URL,
            state: State): Session = {
    require(capabilities.exists(_.isInstanceOf[CoreCapability]),
      s"capabilities should contain ${JMAP_CORE.value.toString} capability")
    require(capabilities.exists(_.isInstanceOf[MailCapability]),
      s"capabilities should contain ${JMAP_MAIL.value.toString} capability")
    require(capabilities.map(_.identifier()).size == capabilities.size,
      "capabilities should not be duplicated")


    new Session(capabilities, accounts, primaryAccounts, username, apiUrl, downloadUrl, uploadUrl, eventSourceUrl, state)
  }
}

final case class Session private(capabilities: Set[_ <: Capability],
                           accounts: Map[Id, Account],
                           primaryAccounts: Map[CapabilityIdentifier, Id],
                           username: Username,
                           apiUrl: URL,
                           downloadUrl: URL,
                           uploadUrl: URL,
                           eventSourceUrl: URL,
                           state: State)
