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

package org.apache.james.backends.postgres;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.bindings.HstoreBinding;
import org.jooq.postgres.extensions.types.Hstore;

public class PostgresCommons {

    public interface DataTypes {

        // hstore
        DataType<Hstore> HSTORE = DefaultDataType.getDefaultDataType("hstore").asConvertedDataType(new HstoreBinding());

        // timestamp(6)
        DataType<LocalDateTime> TIMESTAMP = SQLDataType.LOCALDATETIME(6);

        // text[]
        DataType<String[]> STRING_ARRAY = SQLDataType.CLOB.getArrayDataType();
    }


    public static <T> Field<T> tableField(Table<Record> table, Field<T> field) {
        return DSL.field(table.getName() + "." + field.getName(), field.getDataType());
    }

    public static final Function<Date, LocalDateTime> DATE_TO_LOCAL_DATE_TIME = date -> Optional.ofNullable(date)
        .map(value -> LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC))
        .orElse(null);
    public static final Function<LocalDateTime, Date> LOCAL_DATE_TIME_DATE_FUNCTION = localDateTime -> Optional.ofNullable(localDateTime)
        .map(value -> value.toInstant(ZoneOffset.UTC))
        .map(Date::from)
        .orElse(null);

    public static final Function<Field<?>, Field<?>> UNNEST_FIELD = field -> DSL.function("unnest", field.getType().getComponentType(), field);

    public static final int IN_CLAUSE_MAX_SIZE = 32;

    public static final Field<String[]> EMPTY_STRING_ARRAY_FIELD =  DSL.array("");

}
