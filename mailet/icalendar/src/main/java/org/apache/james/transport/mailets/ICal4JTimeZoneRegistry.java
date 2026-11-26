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

import java.lang.ref.Cleaner;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.zone.ZoneRules;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;

import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryImpl;
import net.fortuna.ical4j.model.ZoneRulesProviderImpl;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.util.Configurator;

/**
 * Uses built-in rules for standard timezones to avoid exhausting the timezone ID pool.
 * Custom timezones keep the rules supplied in the calendar.
 *
 * ical4j takes an ID out of a JVM wide pool (1500 by default) for each registered custom timezone, and only gives it
 * back once the registry is garbage collected, not when it is cleared: the custom timezones are counted against a
 * budget ({@value #MAX_CUSTOM_TIMEZONES} by default) that registries may share, eg the calendars of a mail. A repeated
 * TZID is registered once, the first definition being kept, update or not. Neither budget is given back by
 * {@link #clear()}.
 *
 * The custom timezones of all the registries of the JVM are counted against a second budget, two thirds of the pool:
 * mails processed concurrently, or whose calendars are not collected yet, can not empty the pool. Over this budget, the
 * calendar is rejected like a calendar with too many custom timezones. The calendars keep using the zone IDs of their
 * registry: this budget is given back once the registry is garbage collected, like the pool IDs, or by
 * {@link #releaseCustomTimezones()} when no calendar uses the registry.
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
    // Invitations carry one timezone per distinct TZID they use, often a single one
    public static final int MAX_CUSTOM_TIMEZONES = 20;
    // Read as ical4j does
    @VisibleForTesting
    static final int ZONE_ID_POOL_SIZE = Configurator.getIntProperty("net.fortuna.ical4j.timezone.id.pool.size").orElse(1500);
    // The third left of the pool is a safety margin for the other ical4j users of the JVM
    @VisibleForTesting
    static final int MAX_JVM_WIDE_CUSTOM_TIMEZONES = ZONE_ID_POOL_SIZE * 2 / 3;
    private static final Semaphore JVM_WIDE_CUSTOM_TIMEZONES = new Semaphore(MAX_JVM_WIDE_CUSTOM_TIMEZONES);
    private static final Cleaner CLEANER = Cleaner.create();

    /**
     * Gives back the JVM wide budget taken by a registry. It must not reference the registry, which it would keep alive.
     */
    private static class HeldCustomTimezones implements Runnable {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void run() {
            JVM_WIDE_CUSTOM_TIMEZONES.release(count.getAndSet(0));
        }
    }

    private final TimeZoneRegistryImpl delegate = new TimeZoneRegistryImpl();
    private final Set<String> customTimezoneIds = new HashSet<>();
    private final AtomicInteger remainingCustomTimezones;
    private final HeldCustomTimezones heldCustomTimezones = new HeldCustomTimezones();
    private final Cleaner.Cleanable cleanable = CLEANER.register(this, heldCustomTimezones);
    private long remainingOccurrences = MAX_OCCURRENCES_PER_CALENDAR;

    public ICal4JTimeZoneRegistry() {
        this(newCustomTimezoneBudget());
    }

    /**
     * @param remainingCustomTimezones the custom timezones this registry may still register, shared with other registries
     */
    public ICal4JTimeZoneRegistry(AtomicInteger remainingCustomTimezones) {
        this.remainingCustomTimezones = remainingCustomTimezones;
    }

    public static AtomicInteger newCustomTimezoneBudget() {
        return new AtomicInteger(MAX_CUSTOM_TIMEZONES);
    }

    @Override
    public void register(TimeZone timezone) {
        register(timezone, false);
    }

    @Override
    public void register(TimeZone timezone, boolean update) {
        try {
            TimeZoneRegistry.getGlobalZoneId(timezone.getID());
        } catch (DateTimeException e) {
            if (customTimezoneIds.contains(timezone.getID())) {
                return;
            }
            if (remainingCustomTimezones.getAndDecrement() <= 0) {
                throw new IllegalArgumentException("Too many custom timezones, " + timezone.getID() + " is not registered");
            }
            assertRecurrenceCostIsBounded(timezone);
            if (!JVM_WIDE_CUSTOM_TIMEZONES.tryAcquire()) {
                throw new IllegalArgumentException("Too many custom timezones in use, " + timezone.getID() + " is not registered");
            }
            registerCustomTimezone(timezone, update);
        }
    }

    private void registerCustomTimezone(TimeZone timezone, boolean update) {
        try {
            delegate.register(timezone, update);
        } catch (RuntimeException e) {
            JVM_WIDE_CUSTOM_TIMEZONES.release();
            throw e;
        }
        heldCustomTimezones.count.incrementAndGet();
        customTimezoneIds.add(timezone.getID());
    }

    /**
     * Gives back the pool IDs and the JVM wide budget of the custom timezones, before garbage collection.
     *
     * Only call it when no calendar built with this registry is used anymore, eg its parsing failed: the rules of its
     * custom timezones are no longer resolved. This registry must not be used afterward.
     */
    public void releaseCustomTimezones() {
        ZoneRulesProviderImpl.getInstance()
            .ifPresent(provider -> delegate.getZoneRules().keySet()
                .forEach(provider.getZoneIdPool()::release));
        cleanable.clean();
    }

    @VisibleForTesting
    static int availableJvmWideCustomTimezones() {
        return JVM_WIDE_CUSTOM_TIMEZONES.availablePermits();
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
            .map(ICal4JTimeZoneRegistry::asUtcOnset)
            .orElse(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC).atOffset(ZoneOffset.UTC));
    }

    // A UTC DTSTART (eg 16010101T000000Z) is read as an Instant
    private static OffsetDateTime asUtcOnset(Temporal date) {
        if (date.isSupported(ChronoField.INSTANT_SECONDS)) {
            return Instant.from(date).atOffset(ZoneOffset.UTC);
        }
        if (date.isSupported(ChronoField.HOUR_OF_DAY)) {
            return LocalDateTime.from(date).atOffset(ZoneOffset.UTC);
        }
        return LocalDate.from(date).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    @Override
    public void clear() {
        delegate.clear();
        customTimezoneIds.clear();
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
