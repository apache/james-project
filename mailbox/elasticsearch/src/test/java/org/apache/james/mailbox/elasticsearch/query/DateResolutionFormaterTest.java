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

import static org.assertj.core.api.Assertions.assertThat;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import org.apache.james.mailbox.model.SearchQuery;
import org.junit.Test;

import java.text.ParseException;
import java.time.ZonedDateTime;


public class DateResolutionFormaterTest {

    private final String dateString = "2014-01-02T15:15:15Z";

    @Test
    public void calculateUpperDateShouldReturnDateUpToTheNextMinuteUsingMinuteUnit() throws ParseException {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormater.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Minute)
            )
        ).isEqualTo("2014-01-02T15:16:00Z");
    }

    @Test
    public void calculateUpperDateShouldReturnDateUpToTheNextDayUsingDayUnit() throws ParseException {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormater.computeUpperDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Day)
            )
        ).isEqualTo("2014-01-03T00:00:00Z");
    }

    @Test
    public void calculateLowerDateShouldReturnDateUpToThePreviousMinuteUsingMinuteUnit() throws ParseException {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormater.computeLowerDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Minute)
            )
        ).isEqualTo("2014-01-02T15:15:00Z");
    }

    @Test
    public void calculateLowerDateShouldReturnDateUpToThePreviousDayUsingDayUnit() throws ParseException {
        assertThat(
            ISO_OFFSET_DATE_TIME.format(
                DateResolutionFormater.computeLowerDate(ZonedDateTime.parse(dateString, ISO_OFFSET_DATE_TIME), SearchQuery.DateResolution.Day)
            )
        ).isEqualTo("2014-01-02T00:00:00Z");
    }

}
