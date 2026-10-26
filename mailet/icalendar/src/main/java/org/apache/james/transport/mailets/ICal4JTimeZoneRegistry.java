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
import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.util.Map;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryImpl;

/**
 * Uses built-in rules for standard timezones to avoid exhausting the timezone ID pool.
 * Custom timezones keep the rules supplied in the calendar.
 */
public class ICal4JTimeZoneRegistry implements TimeZoneRegistry {
    private final TimeZoneRegistryImpl delegate = new TimeZoneRegistryImpl();

    @Override
    public void register(TimeZone timezone) {
        register(timezone, false);
    }

    @Override
    public void register(TimeZone timezone, boolean update) {
        try {
            TimeZoneRegistry.getGlobalZoneId(timezone.getID());
        } catch (DateTimeException e) {
            delegate.register(timezone, update);
        }
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
