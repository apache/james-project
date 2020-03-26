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

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.core.Username
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.State.State

case class IsPersonal(value: Boolean)
case class IsReadOnly(value: Boolean)

object Account {
  def apply(name: Username,
            isPersonal: IsPersonal,
            isReadOnly: IsReadOnly,
            accountCapabilities: Set[_ <: Capability]): Account = {

    new Account(name, isPersonal, isReadOnly, accountCapabilities)
  }
}

final case class Account private(name: Username,
                                 isPersonal: IsPersonal,
                                 isReadOnly: IsReadOnly,
                                 accountCapabilities: Set[_ <: Capability])

object State {
  type State = String Refined NonEmpty
}

case class Capabilities(coreCapability: CoreCapability, mailCapability: MailCapability)

final case class Session(capabilities: Capabilities,
                         accounts: Map[Id, Account],
                         primaryAccounts: Map[CapabilityIdentifier, Id],
                         username: Username,
                         apiUrl: URL,
                         downloadUrl: URL,
                         uploadUrl: URL,
                         eventSourceUrl: URL,
                         state: State)
