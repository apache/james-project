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

import org.apache.james.core.User;
import org.apache.james.eventsourcing.Command;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

public class DetectThresholdCrossing implements Command {

    private final User user;
    private final Quota<QuotaCount> countQuota;
    private final Quota<QuotaSize> sizeQuota;
    private final Instant instant;

    public DetectThresholdCrossing(User user, Quota<QuotaCount> countQuota, Quota<QuotaSize> sizeQuota, Instant instant) {
        this.user = user;
        this.countQuota = countQuota;
        this.sizeQuota = sizeQuota;
        this.instant = instant;
    }

    public User getUser() {
        return user;
    }

    public Quota<QuotaCount> getCountQuota() {
        return countQuota;
    }

    public Quota<QuotaSize> getSizeQuota() {
        return sizeQuota;
    }

    public Instant getInstant() {
        return instant;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DetectThresholdCrossing) {
            DetectThresholdCrossing that = (DetectThresholdCrossing) o;

            return Objects.equals(this.user, that.user)
                && Objects.equals(this.countQuota, that.countQuota)
                && Objects.equals(this.sizeQuota, that.sizeQuota);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(user, countQuota, sizeQuota);
    }
}
