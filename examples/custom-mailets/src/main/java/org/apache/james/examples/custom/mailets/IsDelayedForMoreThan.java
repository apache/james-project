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

package org.apache.james.examples.custom.mailets;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.util.DurationParser;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

public class IsDelayedForMoreThan extends GenericMatcher {

    public static final ChronoUnit DEFAULT_UNIT = ChronoUnit.HOURS;
    private final Clock clock;
    private Duration maxDelay;

    public IsDelayedForMoreThan(Clock clock) {
        this.clock = clock;
    }

    public IsDelayedForMoreThan() {
        this(Clock.systemDefaultZone());
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Date sentDate = mail.getMessage().getSentDate();

        if (clock.instant().isAfter(sentDate.toInstant().plusMillis(maxDelay.toMillis()))) {
            return ImmutableList.copyOf(mail.getRecipients());
        }
        return ImmutableList.of();
    }

    @Override
    public void init() {
        String condition = getCondition();
        maxDelay = DurationParser.parse(condition, DEFAULT_UNIT);
    }

    @Override
    public String getMatcherName() {
        return "IsDelayedForMoreThan";
    }
}
