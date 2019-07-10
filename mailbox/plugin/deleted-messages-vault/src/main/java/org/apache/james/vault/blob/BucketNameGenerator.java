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

package org.apache.james.vault.blob;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.blob.api.BucketName;

public class BucketNameGenerator {
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("deleted-messages-([0-9]{4})-([0-9]{2})-(01)");
    private static final String BUCKET_NAME_GENRATING_FORMAT = "deleted-messages-%d-%02d-01";
    private static final int FIRST_DAY_OF_MONTH = 1;
    private static final int ZERO_HOUR = 0;
    private static final int ZERO_MINUTE = 0;
    private static final int ZERO_SECOND = 0;
    private static final int ZERO_NANOSECOND = 0;

    private final Clock clock;

    public BucketNameGenerator(Clock clock) {
        this.clock = clock;
    }

    BucketName currentBucket() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonth().get(ChronoField.MONTH_OF_YEAR);
        int year = now.getYear();
        return BucketName.of(String.format(BUCKET_NAME_GENRATING_FORMAT, year, month));
    }

    Optional<ZonedDateTime> bucketEndTime(BucketName bucketName) {
        return Optional.of(BUCKET_NAME_PATTERN.matcher(bucketName.asString()))
            .filter(Matcher::matches)
            .map(matcher -> {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                ZonedDateTime firstDayOfMonth = ZonedDateTime.of(year, month, FIRST_DAY_OF_MONTH, ZERO_HOUR, ZERO_MINUTE,
                    ZERO_SECOND, ZERO_NANOSECOND, clock.getZone());
                ZonedDateTime lastInstantOfMonth = firstDayOfMonth.plusMonths(1).minusNanos(1);
                return lastInstantOfMonth;
            });
    }
}
