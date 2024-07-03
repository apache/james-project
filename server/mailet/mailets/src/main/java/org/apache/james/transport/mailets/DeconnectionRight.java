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

package org.apache.james.transport.mailets;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Mailet to avoid people to receive emails outside working hour.<br/>
 *
 * Working hours are defined by a starting time and an end time, on weekdays from monday to friday.<br/>
 *
 * Emails received outside working hours are to be delayed to next working hours.<br/>
 *
 * Example:<br/>
 *
 * <pre>
 *     <code>
 * &lt;mailet match="all" class="DeconnectionRight"&gt;
 *     &lt;zoneId&gt;Europe/Paris&lt;/zoneId&gt;
 *     &lt;workDayStart&gt;07:00:00&lt;/workDayStart&gt;
 *     &lt;workDayEnd&gt;20:00:00&lt;/metricName&gt;
 * &lt;/mailet&gt;
 *     </code>
 * </pre>
 */
public class DeconnectionRight extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeconnectionRight.class);
    private Clock clock;
    private ZoneId zoneId;
    private LocalTime workDayStart;
    private LocalTime workDayEnd;

    @Inject
    public DeconnectionRight(Clock clock) {
        this.clock = clock;
    }

    @VisibleForTesting
    Optional<Duration> timeToWorkingHour(ZonedDateTime pointInTime) {
        LocalTime localTime = pointInTime.toLocalTime();
        DayOfWeek dayOfWeek = pointInTime.getDayOfWeek();
        LocalDate localDate = pointInTime.toLocalDate();

        if (dayOfWeek == DayOfWeek.SATURDAY) {
            ZonedDateTime deliveryTime = localDate.plusDays(2)
                .atTime(workDayStart)
                .atZone(zoneId);

            return Optional.of(Duration.between(pointInTime, deliveryTime));
        }

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            ZonedDateTime deliveryTime = localDate.plusDays(1)
                .atTime(workDayStart)
                .atZone(zoneId);

            return Optional.of(Duration.between(pointInTime, deliveryTime));
        }
        if (localTime.equals(workDayStart) || localTime.equals(workDayEnd)) {
            return Optional.empty();
        }
        if (localTime.isAfter(workDayStart) && localTime.isBefore(workDayEnd)) {
            return Optional.empty();
        }
        if (localTime.isBefore(workDayStart)) {
            ZonedDateTime deliveryTime = localDate
                .atTime(workDayStart)
                .atZone(zoneId);
            return Optional.of(Duration.between(pointInTime, deliveryTime));
        }
        if (localTime.isAfter(workDayEnd) && dayOfWeek.equals(DayOfWeek.FRIDAY)) {
            ZonedDateTime deliveryTime = localDate.plusDays(3)
                .atTime(workDayStart)
                .atZone(zoneId);
            return Optional.of(Duration.between(pointInTime, deliveryTime));
        }
        if (localTime.isAfter(workDayEnd)) {
            ZonedDateTime deliveryTime = localDate.plusDays(1)
                .atTime(workDayStart)
                .atZone(zoneId);

            return Optional.of(Duration.between(pointInTime, deliveryTime));
        }
        LOGGER.error("Time at which mail was processed ({}) was not handled by {}", pointInTime, DeconnectionRight.class);
        return Optional.empty();
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        ZonedDateTime now = ZonedDateTime.now(clock)
            .withZoneSameInstant(zoneId);

        timeToWorkingHour(now)
            .ifPresent(duration -> {
                try {
                    getMailetContext().sendMail(mail, Mail.DEFAULT, duration.getSeconds(), TimeUnit.SECONDS);
                } catch (MessagingException e) {
                    throw new RuntimeException(e);
                }
                // discard this mail and keep the scheduled copy
                mail.setState(Mail.GHOST);
            });
    }

    @Override
    public void init() throws MessagingException {
        zoneId = ZoneId.of(getInitParameter("zoneId"));
        workDayStart = LocalTime.parse(getInitParameter("workDayStart"));
        workDayEnd = LocalTime.parse(getInitParameter("workDayEnd"));

        Preconditions.checkArgument(workDayEnd.isAfter(workDayStart));
    }
}
