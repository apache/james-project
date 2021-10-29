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

package org.apache.james.jmap.api.pushsubscription;

import static org.apache.james.jmap.api.model.PushSubscription.EXPIRES_TIME_MAX_DAY;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionExpiredTime;
import org.apache.james.jmap.api.model.PushSubscriptionKeys;

import scala.Option;
import scala.jdk.javaapi.OptionConverters;

public class PushSubscriptionHelpers {
    public static boolean isInThePast(PushSubscriptionExpiredTime expire, Clock clock) {
        return expire.isBefore(ZonedDateTime.now(clock));
    }

    public static boolean isInThePast(Option<PushSubscriptionExpiredTime> expire, Clock clock) {
        return expire.map(value -> isInThePast(value, clock)).getOrElse(() -> false);
    }

    public static PushSubscriptionExpiredTime evaluateExpiresTime(Optional<ZonedDateTime> inputTime, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime maxExpiresTime = now.plusDays(EXPIRES_TIME_MAX_DAY());
        return PushSubscriptionExpiredTime.apply(inputTime.filter(input -> input.isBefore(maxExpiresTime))
            .orElse(maxExpiresTime));
    }

    public static boolean isNotOutdatedSubscription(PushSubscription subscription, Clock clock) {
        return subscription.expires().isAfter(ZonedDateTime.now(clock));
    }

    public static boolean isInvalidPushSubscriptionKey(Option<PushSubscriptionKeys> keysOption) {
        return OptionConverters.toJava(keysOption)
            .map(key -> key.p256dh().isEmpty() || key.auth().isEmpty())
            .orElse(false);
    }
}