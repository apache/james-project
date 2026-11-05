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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryImpl;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RRule;

/**
 * Uses built-in rules for standard timezones to avoid exhausting the timezone ID pool.
 * Custom timezones keep the rules supplied in the calendar.
 *
 * Registering a custom timezone makes ical4j materialize all the occurrences of its recurrence rules, so their
 * cost is bounded first. One instance serves one calendar: the limit applies to all its timezones. A rule above
 * the limit throws an exception, which the ical4j parser reports as a ParserException.
 */
public class ICal4JTimeZoneRegistry implements TimeZoneRegistry {
    // Regular rules yield a handful of occurrences per year: this leaves room for hundreds of timezones starting in 1601
    private static final long MAX_OCCURRENCES_PER_CALENDAR = 100_000;
    // ical4j computes all the candidates of a recurrence period (eg a year) at once, even when consuming them lazily
    private static final long MAX_BY_RULE_COMBINATIONS = 1_000;
    // Observance#getLatestOnset expands the rules up to 10 years ahead
    private static final int HORIZON_IN_YEARS = 10;

    private final TimeZoneRegistryImpl delegate = new TimeZoneRegistryImpl();
    private long remainingOccurrences = MAX_OCCURRENCES_PER_CALENDAR;

    @Override
    public void register(TimeZone timezone) {
        register(timezone, false);
    }

    @Override
    public void register(TimeZone timezone, boolean update) {
        try {
            TimeZoneRegistry.getGlobalZoneId(timezone.getID());
        } catch (DateTimeException e) {
            assertRecurrenceCostIsBounded(timezone);
            delegate.register(timezone, update);
        }
    }

    private void assertRecurrenceCostIsBounded(TimeZone timezone) {
        for (Observance observance : timezone.getVTimeZone().getObservances()) {
            List<RRule<OffsetDateTime>> rrules = observance.getProperties(Property.RRULE);
            for (RRule<OffsetDateTime> rrule : rrules) {
                assertRecurrenceCostIsBounded(timezone, rrule.getRecur(), initialOnset(observance));
            }
        }
    }

    private void assertRecurrenceCostIsBounded(TimeZone timezone, Recur<OffsetDateTime> recur, OffsetDateTime seed) {
        if (byRuleCombinationsExceedLimit(recur)) {
            throw new IllegalArgumentException("Too many BY* combinations in a recurrence rule of timezone " + timezone.getID());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime end = (seed.isAfter(now) ? seed : now).plusYears(HORIZON_IN_YEARS);
        // limit() stops the lazy stream as soon as the budget is exceeded
        remainingOccurrences -= recur.getDatesAsStream(seed, seed, end, -1)
            .limit(remainingOccurrences + 1)
            .count();
        if (remainingOccurrences < 0) {
            throw new IllegalArgumentException("Too many recurrence occurrences in timezone " + timezone.getID());
        }
    }

    private boolean byRuleCombinationsExceedLimit(Recur<?> recur) {
        long combinations = 1;
        for (List<?> byRule : List.of(recur.getMonthList(), recur.getWeekNoList(), recur.getYearDayList(), recur.getMonthDayList(),
                recur.getDayList(), recur.getHourList(), recur.getMinuteList(), recur.getSecondList())) {
            combinations *= Math.max(1, byRule.size());
            if (combinations > MAX_BY_RULE_COMBINATIONS) {
                return true;
            }
        }
        return false;
    }

    // Mirrors Observance#getLatestOnset, including its default when DTSTART is missing
    private OffsetDateTime initialOnset(Observance observance) {
        Optional<DtStart<Temporal>> dtStart = observance.getProperty(Property.DTSTART);
        return dtStart.map(DtStart::getDate)
            .map(date -> date.isSupported(ChronoField.HOUR_OF_DAY) ? LocalDateTime.from(date) : LocalDate.from(date).atStartOfDay())
            .orElse(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC))
            .atOffset(ZoneOffset.UTC);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public TimeZone getTimeZone(String id) {
        return delegate.getTimeZone(id);
    }

    @Override
    public Map<String, ZoneRules> getZoneRules() {
        return delegate.getZoneRules();
    }

    @Override
    public ZoneId getZoneId(String tzId) {
        try {
            return delegate.getZoneId(tzId);
        } catch (DateTimeException e) {
            if (e.getMessage().contains("Unknown timezone identifier")) {
                return TimeZoneRegistry.getGlobalZoneId(tzId);
            }
            throw e;
        }
    }

    @Override
    public String getTzId(String zoneId) {
        return delegate.getTzId(zoneId);
    }
}
