/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import org.apache.james.mime4j.dom.address.{AddressList, MailboxList, Mailbox => Mime4jMailbox}

import scala.jdk.CollectionConverters._

case class EmailerName(value: String) extends AnyVal
case class Address(value: String) extends AnyVal

object EmailAddress {
  def from(addressList: AddressList): List[EmailAddress] =
    from(addressList.flatten())

  def from(addressList: MailboxList): List[EmailAddress] =
    addressList.asScala
      .toList
      .map(from)

  def from(mailbox: Mime4jMailbox): EmailAddress =
    EmailAddress(
      name = Option(mailbox.getName).map(EmailerName),
      email = Address(mailbox.getAddress))
}

case class EmailAddress(name: Option[EmailerName], email: Address)
