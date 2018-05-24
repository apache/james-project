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

package org.apache.james.mailbox.quota.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;

public interface QuotaThresholdFixture {
    QuotaThreshold _50 = new QuotaThreshold(0.50);
    QuotaThreshold _75 = new QuotaThreshold(0.75);
    QuotaThreshold _759 = new QuotaThreshold(0.759);
    QuotaThreshold _80 = new QuotaThreshold(0.8);
    QuotaThreshold _90 = new QuotaThreshold(0.9);
    QuotaThreshold _95 = new QuotaThreshold(0.95);
    QuotaThreshold _99 = new QuotaThreshold(0.99);

    interface TestConstants {
        Duration GRACE_PERIOD = Duration.ofDays(1);
        QuotaMailingListenerConfiguration DEFAULT_CONFIGURATION = QuotaMailingListenerConfiguration.builder()
            .addThresholds(_50)
            .gracePeriod(GRACE_PERIOD)
            .build();
        String BOB = "bob@domain";
        MockMailboxSession BOB_SESSION = new MockMailboxSession(BOB);
        Instant NOW = Instant.now();
        QuotaRoot QUOTAROOT = QuotaRoot.quotaRoot("any", Optional.empty());
        Instant ONE_HOUR_AGO = NOW.minus(Duration.ofHours(1));
        Instant TWO_HOURS_AGO = NOW.minus(Duration.ofHours(2));
        Instant THREE_HOURS_AGO = NOW.minus(Duration.ofHours(3));
        Instant SIX_HOURS_AGO = NOW.minus(Duration.ofHours(6));
        Instant TWELVE_HOURS_AGO = NOW.minus(Duration.ofHours(12));
        Instant TWO_DAYS_AGO = NOW.minus(Duration.ofDays(2));
        Instant SIX_DAYS_AGO = NOW.minus(Duration.ofDays(6));
        Instant TWELVE_DAYS_AGO = NOW.minus(Duration.ofDays(12));
    }

    static FakeMailContext mailetContext() {
        return FakeMailContext.builder()
            .postmaster(MailAddressFixture.POSTMASTER_AT_JAMES)
            .build();
    }

}
