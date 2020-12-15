/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.change

import javax.inject.Inject
import org.apache.james.jmap.api.change.{MailboxChange, MailboxChangeRepository}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.events.MailboxListener.{MailboxEvent, ReactiveGroupMailboxListener}
import org.apache.james.mailbox.events.{Event, Group}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

case class MailboxChangeListenerGroup() extends Group {}

case class MailboxChangeListener @Inject() (mailboxChangeRepository: MailboxChangeRepository,
                                            mailboxManager: MailboxManager) extends ReactiveGroupMailboxListener {

  override def reactiveEvent(event: Event): Publisher[Void] = {
    MailboxChange.fromEvent(event, mailboxManager)
      .map(changes => SFlux.fromIterable(changes.asScala)
        .map(change => mailboxChangeRepository.save(change))
        .`then`()
        .`then`(SMono.empty[Void]).asJava)
      .orElse(SMono.empty[Void].asJava)
  }

  override def getDefaultGroup: Group = MailboxChangeListenerGroup()

  override def isHandling(event: Event): Boolean = event.isInstanceOf[MailboxEvent]
}
