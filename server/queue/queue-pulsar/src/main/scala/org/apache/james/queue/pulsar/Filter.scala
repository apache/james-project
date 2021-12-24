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
package org.apache.james.queue.pulsar

import julienrf.json.derived
import com.sksamuel.pulsar4s.SequenceId
import play.api.libs.json.{Json, OFormat}

private[pulsar] sealed trait Filter {
  def lastSequenceId: SequenceId

  def matches(mailMetadata: MailMetadata): Boolean
}

private[pulsar] object Filter {
  implicit val sequenceIdFormat: OFormat[SequenceId] = Json.format[SequenceId]
  implicit val filterOFormat: OFormat[Filter] = derived.oformat()

  case class ByName(name: String, lastSequenceId: SequenceId) extends Filter {
    def matches(mailMetadata: MailMetadata): Boolean = mailMetadata.name == name
  }

  case class BySender(sender: String, lastSequenceId: SequenceId) extends Filter {
    def matches(mailMetadata: MailMetadata): Boolean = mailMetadata.sender.contains(sender)
  }

  case class ByRecipient(recipient: String, lastSequenceId: SequenceId) extends Filter {
    def matches(mailMetadata: MailMetadata): Boolean = mailMetadata.recipients.contains(recipient)
  }
}