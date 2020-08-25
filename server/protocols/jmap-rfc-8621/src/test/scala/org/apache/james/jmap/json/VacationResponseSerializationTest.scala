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

package org.apache.james.jmap.json

import java.time.ZonedDateTime

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.jmap.json.VacationResponseSerializationTest.VACATION_RESPONSE
import org.apache.james.jmap.mail.{FromDate, HtmlBody, IsEnabled, Subject, TextBody, ToDate, VacationResponse, VacationResponseId}
import org.apache.james.mailbox.model.TestId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

object VacationResponseSerializationTest {
  private val DATE_TIME_2016 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]")
  private val DATE_TIME_2017 = ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]")

  private val VACATION_RESPONSE_ID: VacationResponseId = VacationResponseId()
  private val IS_ENABLED: IsEnabled = IsEnabled(true)
  private val FROM_DATE: Option[FromDate] = Some(FromDate(DATE_TIME_2016))
  private val TO_DATE: Option[ToDate] = Some(ToDate(DATE_TIME_2017))
  private val SUBJECT: Option[Subject] = Some(Subject("Hello world"))
  private val TEXT_BODY: Option[TextBody] = Some(TextBody("text is required when enabled"))
  private val HTML_BODY: Option[HtmlBody] = Some(HtmlBody("<b>HTML body</b>"))

  val VACATION_RESPONSE: VacationResponse = VacationResponse(
    id = VACATION_RESPONSE_ID,
    isEnabled = IS_ENABLED,
    fromDate = FROM_DATE,
    toDate = TO_DATE,
    subject = SUBJECT,
    textBody = TEXT_BODY,
    htmlBody = HTML_BODY
  )
}

class VacationResponseSerializationTest extends AnyWordSpec with Matchers {
  "Serialize VacationResponse" should {
    "succeed" in {
      val expectedJson: String =
        """{
          | "id":"singleton",
          | "isEnabled":true,
          | "fromDate":"2016-10-09T08:07:06+07:00[Asia/Vientiane]",
          | "toDate":"2017-10-09T08:07:06+07:00[Asia/Vientiane]",
          | "subject":"Hello world",
          | "textBody":"text is required when enabled",
          | "htmlBody":"<b>HTML body</b>"
          |}""".stripMargin

      val serializer = new Serializer(new TestId.Factory)
      assertThatJson(Json.stringify(serializer.serialize(VACATION_RESPONSE)(serializer.vacationResponseWrites))).isEqualTo(expectedJson)
    }
  }
}
