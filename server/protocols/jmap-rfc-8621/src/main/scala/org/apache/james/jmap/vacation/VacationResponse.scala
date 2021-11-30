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

package org.apache.james.jmap.vacation

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{Id, Properties, UTCDate}
import org.apache.james.jmap.mail.Subject
import org.apache.james.vacation.api.Vacation

import scala.compat.java8.OptionConverters._

case class VacationResponseId()

case class IsEnabled(value: Boolean) extends AnyVal
case class FromDate(value: UTCDate)
case class ToDate(value: UTCDate)
case class TextBody(value: String) extends AnyVal
case class HtmlBody(value: String) extends AnyVal

object VacationResponse {
  val VACATION_RESPONSE_ID: Id = Id.validate("singleton").toOption.get
  val UNPARSED_SINGLETON: UnparsedVacationResponseId = UnparsedVacationResponseId(VACATION_RESPONSE_ID)

  def asRfc8621(vacation: Vacation) = VacationResponse(
    id = VacationResponseId(),
    isEnabled = IsEnabled(vacation.isEnabled),
    fromDate = vacation.getFromDate.asScala.map(date => FromDate(UTCDate(date))),
    toDate = vacation.getToDate.asScala.map(date => ToDate(UTCDate(date))),
    subject = vacation.getSubject.asScala.map(Subject),
    textBody = vacation.getTextBody.asScala.map(TextBody),
    htmlBody = vacation.getHtmlBody.asScala.map(HtmlBody)
  )

  val allProperties: Properties = Properties("id", "isEnabled", "fromDate", "toDate", "subject", "textBody", "htmlBody")
  val idProperty: Properties = Properties("id")

  def propertiesFiltered(requestedProperties: Properties) : Properties = idProperty ++ requestedProperties
}

case class UnparsedVacationResponseId(id: Id)

case class VacationResponse(id: VacationResponseId,
                           isEnabled: IsEnabled,
                           fromDate: Option[FromDate],
                           toDate: Option[ToDate],
                           subject: Option[Subject],
                           textBody: Option[TextBody],
                           htmlBody: Option[HtmlBody])
