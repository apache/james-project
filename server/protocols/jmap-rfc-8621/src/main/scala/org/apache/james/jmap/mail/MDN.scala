/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.mail

import java.util.Locale

import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mdn.MDNReportParser
import org.apache.james.mdn.`type`.DispositionType
import org.apache.james.mdn.action.mode.DispositionActionMode
import org.apache.james.mdn.fields.{FinalRecipient, ReportingUserAgent, Disposition => JavaDisposition}
import org.apache.james.mdn.sending.mode.DispositionSendingMode

import scala.util.{Failure, Success, Try}

object MDN {
  val DISPOSITION_NOTIFICATION_TO: String = "Disposition-Notification-To"
}

case class MDNDispositionInvalidException(description: String) extends Exception

case class ForEmailIdField(originalMessageId: MessageId) extends AnyVal

case class SubjectField(value: String) extends AnyVal

case class TextBodyField(value: String) extends AnyVal

case class ReportUAField(value: String) extends AnyVal {
  def asJava: Try[ReportingUserAgent] = new MDNReportParser("Reporting-UA: " + value)
    .reportingUaField
    .run()

  def validate: Either[MDNSendRequestInvalidException, ReportUAField] =
    asJava match {
      case Success(_) => scala.Right(this)
      case Failure(_) => Left(MDNSendRequestInvalidException(
        SetError(`type` = SetError.invalidArgumentValue,
          description = SetErrorDescription("ReportUA can't be parse."),
          properties = Some(Properties.toProperties(Set("reportingUA"))))))
    }
}

case class FinalRecipientField(value: String) extends AnyVal {
  def asJava: Try[FinalRecipient] = new MDNReportParser("Final-Recipient: " + value)
    .finalRecipientField
    .run()

  def getMailAddress: Try[MailAddress] =
    for {
      javaFinalRecipient <- asJava
      mailAddress = new MailAddress(javaFinalRecipient.getFinalRecipient.formatted())
    } yield mailAddress

  def validate: Either[MDNSendRequestInvalidException, FinalRecipientField] =
    asJava match {
      case Success(_) => scala.Right(this)
      case Failure(_) => Left(MDNSendRequestInvalidException(
        SetError(`type` = SetError.invalidArgumentValue,
          description = SetErrorDescription("FinalRecipient can't be parse."),
          properties = Some(Properties.toProperties(Set("finalRecipient"))))))
    }
}

case class OriginalRecipientField(value: String) extends AnyVal

case class OriginalMessageIdField(value: String) extends AnyVal

case class ExtensionFieldName(value: String) extends AnyVal

case class ExtensionFieldValue(value: String) extends AnyVal

case class ErrorField(value: String) extends AnyVal

object IncludeOriginalMessageField {
  def default: IncludeOriginalMessageField = IncludeOriginalMessageField(false)
}

case class IncludeOriginalMessageField(value: Boolean) extends AnyVal

case class MDNGatewayField(value: String) extends AnyVal

object MDNDisposition {
  def fromJava(javaDisposition: JavaDisposition): MDNDisposition =
    MDNDisposition(actionMode = javaDisposition.getActionMode.getValue,
      sendingMode = javaDisposition.getSendingMode.getValue.toLowerCase(Locale.US),
      `type` = javaDisposition.getType.getValue)
}

case class MDNDisposition(actionMode: String,
                          sendingMode: String,
                          `type`: String) {
  def asJava: Try[JavaDisposition] =
    Try(JavaDisposition.builder()
      .`type`(DispositionType.fromString(`type`)
        .orElseThrow(() => MDNDispositionInvalidException("Disposition \"Type\" is invalid.")))
      .actionMode(DispositionActionMode.fromString(actionMode)
        .orElseThrow(() => MDNDispositionInvalidException("Disposition \"ActionMode\" is invalid.")))
      .sendingMode(DispositionSendingMode.fromString(sendingMode)
        .orElseThrow(() => MDNDispositionInvalidException("Disposition \"SendingMode\" is invalid.")))
      .build())

  def validate: Either[MDNSendRequestInvalidException, MDNDisposition] =
    asJava match {
      case Success(_) => scala.Right(this)
      case Failure(exception) => exception match {
        case exception: MDNDispositionInvalidException => Left(MDNSendRequestInvalidException(
          SetError(`type` = SetError.invalidArgumentValue,
            description = SetErrorDescription(exception.description),
            properties = Some(Properties.toProperties(Set("disposition"))))))
        case _ => Left(MDNSendRequestInvalidException(
          SetError(`type` = SetError.invalidArgumentValue,
            description = SetErrorDescription(exception.getMessage),
            properties = Some(Properties.toProperties(Set("disposition"))))))
      }
    }
}