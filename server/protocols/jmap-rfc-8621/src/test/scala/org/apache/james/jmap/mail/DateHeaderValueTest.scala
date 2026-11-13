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

import java.time.{ZoneId, ZonedDateTime}

import org.apache.james.jmap.core.UTCDate
import org.apache.james.mime4j.stream.RawFieldParser
import org.apache.james.mime4j.util.ContentUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DateHeaderValueTest {
  private val UTC: ZoneId = ZoneId.of("UTC")

  @Test
  def validDateShouldBeParsed(): Unit = {
    val field = RawFieldParser.DEFAULT.parseField(ContentUtil.encode("Date: 13 Oct 2026 10:00:00 +0000"))

    assertThat(DateHeaderValue.from(field, UTC))
      .isEqualTo(DateHeaderValue(Some(UTCDate(ZonedDateTime.of(2026, 10, 13, 10, 0, 0, 0, UTC)))))
  }

  @Test
  def invalidDateShouldYieldNoDate(): Unit = {
    val field = RawFieldParser.DEFAULT.parseField(ContentUtil.encode("Date: not a date"))

    assertThat(DateHeaderValue.from(field, UTC)).isEqualTo(DateHeaderValue(None))
  }

  @Test
  def oversizedDayNumberShouldYieldNoDate(): Unit = {
    val field = RawFieldParser.DEFAULT.parseField(ContentUtil.encode("Date: 99999999999 Oct 2026 10:00:00 +0000"))

    assertThat(DateHeaderValue.from(field, UTC)).isEqualTo(DateHeaderValue(None))
  }

  @Test
  def oversizedHourNumberShouldYieldNoDate(): Unit = {
    val field = RawFieldParser.DEFAULT.parseField(ContentUtil.encode("Date: 13 Oct 2026 99999999999:00:00 +0000"))

    assertThat(DateHeaderValue.from(field, UTC)).isEqualTo(DateHeaderValue(None))
  }
}
