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

package org.apache.james.mailbox.quota.mailing.commands;

import java.time.Instant;
import java.util.Objects;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.eventsourcing.Command;
import org.apache.james.mailbox.model.Quota;

public class DetectThresholdCrossing implements Command {

    private final Username username;
    private final Quota<QuotaCountLimit, QuotaCountUsage> countQuota;
    private final Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota;
    private final Instant instant;

    public DetectThresholdCrossing(Username username, Quota<QuotaCountLimit, QuotaCountUsage> countQuota, Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota, Instant instant) {
        this.username = username;
        this.countQuota = countQuota;
        this.sizeQuota = sizeQuota;
        this.instant = instant;
    }

    public Username getUsername() {
        return username;
    }

    public Quota<QuotaCountLimit, QuotaCountUsage> getCountQuota() {
        return countQuota;
    }

    public Quota<QuotaSizeLimit, QuotaSizeUsage> getSizeQuota() {
        return sizeQuota;
    }

    public Instant getInstant() {
        return instant;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DetectThresholdCrossing) {
            DetectThresholdCrossing that = (DetectThresholdCrossing) o;

            return Objects.equals(this.username, that.username)
                && Objects.equals(this.countQuota, that.countQuota)
                && Objects.equals(this.sizeQuota, that.sizeQuota);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(username, countQuota, sizeQuota);
    }
}
