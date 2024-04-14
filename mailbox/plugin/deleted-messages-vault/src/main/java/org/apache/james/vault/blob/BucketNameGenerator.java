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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BucketName;

public class BucketNameGenerator {
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("deleted-messages-([0-9]{4})-([0-9]{2})-(01)");
    private static final String BUCKET_NAME_GENERATING_FORMAT = "deleted-messages-%d-%02d-01";

    private final Clock clock;

    @Inject
    public BucketNameGenerator(Clock clock) {
        this.clock = clock;
    }

    BucketName currentBucket() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();
        return BucketName.of(String.format(BUCKET_NAME_GENERATING_FORMAT, year, month));
    }

    Optional<ZonedDateTime> bucketEndTime(BucketName bucketName) {
        return Optional.of(BUCKET_NAME_PATTERN.matcher(bucketName.asString()))
            .filter(Matcher::matches)
            .map(matcher -> {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                return firstDayOfNextMonth(year, month);
            });
    }

    private ZonedDateTime firstDayOfNextMonth(int year, int month) {
        return LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(clock.getZone());
    }
}
