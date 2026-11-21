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

package org.apache.james.jmap.method

import java.text.Normalizer

import com.google.common.base.{Ascii, CharMatcher}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import jakarta.mail.internet.{InternetAddress, MimeUtility}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.domainlist.api.DomainList
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mime4j.field.address.DefaultAddressParser
import org.apache.james.server.core.MimeMessageUtil
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.ReactorUtils
import org.apache.mailet.Mail
import reactor.core.scala.publisher.SMono

import scala.util.Try

/**
 * Rejects submissions whose `Sender` header field does not disclose the logged in user.
 *
 * Optional, enabled through `send.extra.validations`. Stricter than the built-in check, which accepts
 * any address the account owner can send from: as `Sender` names whoever actually transmits the
 * message (RFC 5322 section 3.6.2), aliases or the delegating account do not count.
 * Each `Sender` field must hold exactly one mailbox of a local domain resolving, through
 * `UsersRepository.getUsername`, to the logged in user (upon delegation, the user who authenticated).
 * Mails without `Sender` are left alone.
 */
class SenderHeaderEmailSubmissionSetValidation @Inject()(usersRepository: UsersRepository,
                                                         domainList: DomainList) extends EmailSubmissionSetValidation {
  override def validate(mail: Mail, mailboxSession: MailboxSession): SMono[Option[SetError]] = {
    val loggedInUser: Username = mailboxSession.getLoggedInUser.orElse(mailboxSession.getUser)

    SMono.fromCallable(() => {
        val senderFields = Option(mail.getMessage.getHeader("Sender")).toList.flatten
        // Jakarta Mail ends a header line at a bare CR, mime4j (used to display the message) does not: the
        // Sender field checked here could differ from the one shown to recipients, or even be hidden from
        // Jakarta Mail when the bare CRs make it read an empty line
        !MimeMessageUtil.headerBlockHoldsABareCarriageReturn(mail.getMessage) &&
          senderFields.forall(field => disclosesOnly(field, loggedInUser))
      })
      .map {
        case true => None
        case false => Some(SetError(EmailSubmissionSetMethod.forbiddenFrom,
          SetErrorDescription(s"Attempt to send a mail whose MimeMessage Sender field does not match the logged in user ${loggedInUser.asString()}"),
          Some(Properties("sender"))))
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
  }

  // Strict: the lenient parser turns eg 'To: bob@domain.tld;' into a plain address
  private def disclosesOnly(field: String, loggedInUser: Username): Boolean = {
    val unfolded = MimeUtility.unfold(field)
    Try(InternetAddress.parseHeader(unfolded, true)).toOption match {
      case Some(Array(address)) if !address.isGroup && readsTheSameMailbox(unfolded, address) =>
        Try(new MailAddress(address.getAddress)).toOption
          .filter(mailAddress => rawDomainIsCanonical(address.getAddress, mailAddress))
          .filter(mailAddress => domainList.containsDomain(mailAddress.getDomain))
          .exists(mailAddress => resolvesTo(mailAddress, loggedInUser))
      case _ => false
    }
  }

  // The comparison is made on the normalised address: its domain went through IDNA (NFKC), which maps eg a
  // full-width 'ｅ' onto 'e'. Recipients read the raw field: its domain needs to already be in canonical form,
  // ASCII or the U-label Domain gives back, up to NFC (canonical equivalents render the same) and ASCII case.
  private def rawDomainIsCanonical(rawAddress: String, sender: MailAddress): Boolean = {
    val rawDomain = rawAddress.substring(rawAddress.lastIndexOf('@') + 1)
    CharMatcher.ascii().matchesAllOf(rawDomain) ||
      Ascii.equalsIgnoreCase(Normalizer.normalize(rawDomain, Normalizer.Form.NFC), sender.getDomain.asString())
  }

  // InternetAddress.parseHeader ignores what follows the first mailbox, eg '<bob@domain.tld> alice@domain.tld', and
  // stops at comments, eg 'bob@domain.tld(comment).evil' reads as bob@domain.tld: mime4j must read the whole field
  // as the same single mailbox
  private def readsTheSameMailbox(field: String, address: InternetAddress): Boolean =
    Try(DefaultAddressParser.DEFAULT.parseMailbox(field).getAddress).toOption.contains(address.getAddress)

  private def resolvesTo(mailAddress: MailAddress, loggedInUser: Username): Boolean =
    try {
      usersRepository.getUsername(mailAddress).equals(loggedInUser)
    } catch {
      // Eg a quoted local part holding a '@' cannot be turned into a username
      case _: IllegalArgumentException => false
    }
}
