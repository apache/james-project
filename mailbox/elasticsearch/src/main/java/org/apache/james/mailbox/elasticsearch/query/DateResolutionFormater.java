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

import org.apache.james.mailbox.model.SearchQuery;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateResolutionFormater {

    public static DateTimeFormatter DATE_TIME_FOMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    public static ZonedDateTime computeUpperDate(ZonedDateTime date, SearchQuery.DateResolution resolution) {
        return date.truncatedTo(convertDateResolutionField(resolution)).plus(1,convertDateResolutionField(resolution));
    }

    public static ZonedDateTime computeLowerDate(ZonedDateTime date, SearchQuery.DateResolution resolution) {
        return date.truncatedTo(convertDateResolutionField(resolution));
    }

    private static TemporalUnit convertDateResolutionField(SearchQuery.DateResolution resolution) {
        switch(resolution) {
            case Year:
                return ChronoUnit.YEARS;
            case Month:
                return ChronoUnit.MONTHS;
            case Day:
                return ChronoUnit.DAYS;
            case Hour:
                return ChronoUnit.HOURS;
            case Minute:
                return ChronoUnit.MINUTES;
            case Second:
                return ChronoUnit.SECONDS;
            default:
                throw new RuntimeException("Unknown Date resolution used");
        }
    }

    public static ZonedDateTime convertDateToZonedDateTime(Date date) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
    }
}