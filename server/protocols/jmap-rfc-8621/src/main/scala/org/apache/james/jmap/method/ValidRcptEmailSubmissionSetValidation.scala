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

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.method.ValidRcptEmailSubmissionSetValidation.LOGGER
import org.apache.james.rrt.api.RecipientValidator
import org.apache.james.util.ReactorUtils
import org.apache.mailet.Mail
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object ValidRcptEmailSubmissionSetValidation {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[ValidRcptEmailSubmissionSetValidation])
}

/**
 * Rejects submissions carrying recipients that James knows it cannot deliver to: recipients of a
 * local domain that neither have a mailbox nor are covered by a RecipientRewriteTable entry.
 *
 * The JMAP counterpart of the SMTP `ValidRcptHandler`, sharing its logic through
 * [[org.apache.james.rrt.api.RecipientValidator]]. Recipients of remote domains are left alone as
 * their validity cannot be assessed locally.
 */
class ValidRcptEmailSubmissionSetValidation(recipientValidator: RecipientValidator,
                                            policy: RecipientValidator.Policy) extends EmailSubmissionSetValidation {
  @Inject
  def this(recipientValidator: RecipientValidator) = this(recipientValidator, RecipientValidator.Policy.DEFAULT)

  override def validate(mail: Mail): SMono[Option[SetError]] =
    SFlux.fromIterable(mail.getRecipients.asScala.toSeq)
      .filterWhen(recipient => isValid(recipient).map(!_), ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map {
        case Seq() => None
        case invalidRecipients => Some(SetError.invalidRecipients(
          SetErrorDescription(s"Invalid recipients: ${invalidRecipients.map(_.asString()).mkString(", ")}"),
          Some(Properties("envelope.rcptTo"))))
      }

  private def isValid(recipient: MailAddress): SMono[Boolean] =
    SMono.fromCallable(() => Try(recipientValidator.isValidRecipient(recipient, policy)) match {
        case Success(valid) => valid
        // Eg the recipient cannot be turned into a username: such a recipient can never be delivered to.
        case Failure(e: IllegalArgumentException) =>
          LOGGER.info("Encountered an error upon recipient validation ({}), rejecting it", recipient.asString(), e)
          false
        // Storage failures are left to bubble up: they translate into a serverFail, the JMAP
        // counterpart of the SMTP deny-soft, rather than into a definitive rejection.
        case Failure(e) => throw e
      })
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
}
