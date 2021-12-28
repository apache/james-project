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

import org.apache.commons.configuration2.Configuration;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

public class VaultConfiguration {
    public static final VaultConfiguration DEFAULT =
        new VaultConfiguration(ChronoUnit.YEARS.getDuration(), DefaultMailboxes.RESTORED_MESSAGES);

    public static VaultConfiguration from(Configuration propertiesConfiguration) {
        Duration retentionPeriod = Optional.ofNullable(propertiesConfiguration.getString("retentionPeriod"))
            .map(string -> DurationParser.parse(string, ChronoUnit.DAYS))
            .orElse(DEFAULT.getRetentionPeriod());
        String restoreLocation = Optional.ofNullable(propertiesConfiguration.getString("restoreLocation"))
            .orElse(DEFAULT.getRestoreLocation());
        return new VaultConfiguration(retentionPeriod, restoreLocation);
    }

    private final Duration retentionPeriod;
    private final String restoreLocation;

    VaultConfiguration(Duration retentionPeriod, String restoreLocation) {
        Preconditions.checkNotNull(retentionPeriod);
        Preconditions.checkNotNull(restoreLocation);

        this.retentionPeriod = retentionPeriod;
        this.restoreLocation = restoreLocation;
    }

    public Duration getRetentionPeriod() {
        return retentionPeriod;
    }

    public String getRestoreLocation() {
        return restoreLocation;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof VaultConfiguration) {
            VaultConfiguration that = (VaultConfiguration) o;

            return Objects.equals(this.retentionPeriod, that.retentionPeriod)
                && Objects.equals(this.restoreLocation, that.restoreLocation);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(retentionPeriod, restoreLocation);
    }
}
