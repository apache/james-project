/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.mail

import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxPath

import javax.inject.Inject

trait NamespaceFactory {
  def from(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace
}

class DefaultNamespaceFactory extends NamespaceFactory {

  def from(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace =
    mailboxPath.belongsTo(mailboxSession) match {
      case true => PersonalNamespace()
      case false => DelegatedNamespace(mailboxPath.getUser)
    }
}

object MailboxNamespace {
  def delegated(owner: Username): DelegatedNamespace = DelegatedNamespace(owner)

  def personal(): PersonalNamespace = PersonalNamespace()
}

trait MailboxNamespace {
  def serialize(): String
}

case class PersonalNamespace() extends MailboxNamespace {
  override def serialize(): String = "Personal"
}

case class DelegatedNamespace(owner: Username) extends MailboxNamespace {
  override def serialize(): String = s"Delegated[${owner.asString}]"
}