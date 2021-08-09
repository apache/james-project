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

package org.apache.james.jmap.cassandra.upload;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

import javax.inject.Inject;

public class BucketNameGenerator {
    private final Clock clock;

    @Inject
    public BucketNameGenerator(Clock clock) {
        this.clock = clock;
    }

    public UploadBucketName current() {
        int weekCount = currentWeekCount();
        return new UploadBucketName(weekCount);
    }

    public Predicate<UploadBucketName> evictionPredicate() {
        final int currentWeekCount = currentWeekCount();

        return uploadBucketName -> uploadBucketName.getWeekNumber() < currentWeekCount - 1;
    }

    private int currentWeekCount() {
        return Math.toIntExact(ChronoUnit.WEEKS.between(
            LocalDate.ofEpochDay(0),
            LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }
}
