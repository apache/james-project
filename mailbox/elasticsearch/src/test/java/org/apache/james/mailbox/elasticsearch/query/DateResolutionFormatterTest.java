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

package org.apache.james.mailbox.elasticsearch.query;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.apache.james.mailbox.model.SearchQuery;
import org.junit.jupiter.api.Test;


class DateResolutionFormatterTest {

    final String dateString = "2014-01-02T15:15:15Z";

    @Test
    void calculateUpperDateShouldReturnDateUpToTheNextMinuteUsingMinuteUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Minute)
            )
        ).isEqualTo("2014-01-02T15:16:00Z");
    }

    @Test
    void calculateUpperDateShouldReturnDateUpToTheNextHourUsingHourUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Hour)
            )
        ).isEqualTo("2014-01-02T16:00:00Z");
    }

    @Test
    void calculateUpperDateShouldReturnDateUpToTheNextMonthUsingMonthUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Month)
            )
        ).isEqualTo("2014-02-01T00:00:00Z");
    }

    @Test
    void calculateUpperDateShouldReturnDateUpToTheNextYearUsingYearUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Year)
            )
        ).isEqualTo("2015-01-01T00:00:00Z");
    }

    @Test
    void calculateUpperDateShouldReturnDateUpToTheNextDayUsingDayUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Day)
            )
        ).isEqualTo("2014-01-03T00:00:00Z");
    }

    @Test
    void calculateLowerDateShouldReturnDateUpToThePreviousMinuteUsingMinuteUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeLowerDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Minute)
            )
        ).isEqualTo("2014-01-02T15:15:00Z");
    }

    @Test
    void calculateLowerDateShouldReturnDateUpToThePreviousDayUsingDayUnit() {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormatter.computeLowerDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Day)
            )
        ).isEqualTo("2014-01-02T00:00:00Z");
    }

}
