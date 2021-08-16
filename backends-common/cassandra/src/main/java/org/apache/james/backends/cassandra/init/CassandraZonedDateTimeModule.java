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

package org.apache.james.backends.cassandra.init;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.ZonedDateTimeRepresentation;

import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;

public interface CassandraZonedDateTimeModule {
    String ZONED_DATE_TIME = "zonedDateTime";
    String DATE = "date";
    String TIME_ZONE = "timeZone";

    CassandraModule MODULE = CassandraModule.type(ZONED_DATE_TIME)
        .statement(statement -> statement
            .withField(DATE, DataTypes.TIMESTAMP)
            .withField(TIME_ZONE, DataTypes.TEXT))
        .build();

    static UdtValue toUDT(UserDefinedType zonedDateTimeUserType, ZonedDateTime zonedDateTime) {
        ZonedDateTimeRepresentation representation = ZonedDateTimeRepresentation.fromZonedDateTime(zonedDateTime);
        return zonedDateTimeUserType.newValue()
            .setInstant(CassandraZonedDateTimeModule.DATE, representation.getDate().toInstant())
            .setString(CassandraZonedDateTimeModule.TIME_ZONE, representation.getSerializedZoneId());
    }

    static Optional<UdtValue> toUDT(UserDefinedType zonedDateTimeUserType, Optional<ZonedDateTime> zonedDateTimeOptional) {
        return zonedDateTimeOptional.map(zonedDateTime -> toUDT(zonedDateTimeUserType, zonedDateTime));
    }

    static Optional<ZonedDateTime> fromUDTOptional(UdtValue value) {
        return Optional.ofNullable(value).map(CassandraZonedDateTimeModule::fromUDT);
    }

    static ZonedDateTime fromUDT(UdtValue udtValue) {
        return ZonedDateTimeRepresentation.fromDate(
                udtValue.getInstant(CassandraZonedDateTimeModule.DATE),
                udtValue.getString(CassandraZonedDateTimeModule.TIME_ZONE))
            .getZonedDateTime();
    }
}
