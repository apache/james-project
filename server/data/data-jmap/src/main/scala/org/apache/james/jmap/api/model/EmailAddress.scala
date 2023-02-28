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
 ****************************************************************/

package org.apache.james.jmap.api.model

import org.apache.james.core.MailAddress
import org.apache.james.mime4j.dom.address.{AddressList, MailboxList, Mailbox => Mime4jMailbox}

import java.util.Optional
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

object EmailerName {
  def from(value: String): EmailerName = EmailerName(value.strip())
}

case class EmailerName(value: String) extends AnyVal

object EmailAddress {
  def from(addressList: AddressList): List[EmailAddress] = Option(addressList)
    .map(addressList => from(addressList.flatten()))
    .getOrElse(List())

  def from(addressList: MailboxList): List[EmailAddress] =
    addressList.asScala
      .toList
      .flatMap(mailbox => from(mailbox).toOption)

  def from(mailbox: Mime4jMailbox): Try[EmailAddress] =
    Try(new MailAddress(mailbox.getAddress))
        .map(email => EmailAddress(
          name = Option(mailbox.getName).map(EmailerName.from),
          email = email))

  def from(name: Optional[String], email: MailAddress): EmailAddress =
    EmailAddress(name.toScala.map(EmailerName(_)), email)
}

case class EmailAddress(name: Option[EmailerName], email: MailAddress) {
  val asMime4JMailbox: Mime4jMailbox = new Mime4jMailbox(
    name.map(_.value).orNull,
    email.getLocalPart,
    email.getDomain.asString)

  val nameAsString: String = name.map(_.value).orNull
}
