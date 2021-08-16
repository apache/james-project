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

package org.apache.james.backends.cassandra.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class ZonedDateTimeRepresentation {

    public static ZonedDateTimeRepresentation fromZonedDateTime(ZonedDateTime zonedDateTime) {
        return new ZonedDateTimeRepresentation(zonedDateTime);
    }

    public static ZonedDateTimeRepresentation fromDate(Instant date, String serializedZoneId) {
        return new ZonedDateTimeRepresentation(ZonedDateTime.ofInstant(date, ZoneId.of(serializedZoneId)));
    }

    private final ZonedDateTime zonedDateTime;


    public ZonedDateTimeRepresentation(ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    public Date getDate() {
        return new Date(zonedDateTime.toInstant().toEpochMilli());
    }

    public String getSerializedZoneId() {
        return zonedDateTime.getZone().getId();
    }

    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }
}
