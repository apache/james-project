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

package org.apache.james.vault;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

public class RetentionConfiguration {
    public static final RetentionConfiguration DEFAULT = new RetentionConfiguration(ChronoUnit.YEARS.getDuration());

    public static RetentionConfiguration from(Configuration propertiesConfiguration) {
        return Optional.ofNullable(propertiesConfiguration.getString("retentionPeriod"))
            .map(string -> DurationParser.parse(string, ChronoUnit.DAYS))
            .map(RetentionConfiguration::new)
            .orElse(DEFAULT);
    }

    private final Duration retentionPeriod;

    RetentionConfiguration(Duration retentionPeriod) {
        Preconditions.checkNotNull(retentionPeriod);

        this.retentionPeriod = retentionPeriod;
    }

    public Duration getRetentionPeriod() {
        return retentionPeriod;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RetentionConfiguration) {
            RetentionConfiguration that = (RetentionConfiguration) o;

            return Objects.equals(this.retentionPeriod, that.retentionPeriod);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(retentionPeriod);
    }
}
