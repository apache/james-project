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

package org.apache.james.jmap.model

import java.time.ZonedDateTime

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

object UTCDateTest {
  private val UTC_DATE: ZonedDateTime = ZonedDateTime.parse("2016-10-09T01:07:06Z[UTC]")
}

class UTCDateTest extends AnyWordSpec with Matchers {
  import UTCDateTest.UTC_DATE

  "asUTC" should {
    "return correct UTC date" in {
      val zonedDate: ZonedDateTime = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]")

      UTCDate(zonedDate).asUTC must be(UTC_DATE)
    }

    "return same date when already UTC date" in {
      UTCDate(UTC_DATE).asUTC must be(UTC_DATE)
    }
  }
}
