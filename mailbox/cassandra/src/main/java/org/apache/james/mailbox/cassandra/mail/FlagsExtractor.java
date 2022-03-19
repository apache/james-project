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
package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.setOf;

import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.mailbox.cassandra.table.Flag;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

public class FlagsExtractor {
    public static final TypeCodec<Set<String>> SET_OF_STRINGS_CODEC = CodecRegistry.DEFAULT.codecFor(setOf(TEXT));

    public static Flags getFlags(Row row) {
        return getFlags(row, row.protocolVersion());
    }

    private static Flags getFlags(Row row, ProtocolVersion protocolVersion) {
        Flags flags = new Flags();
        for (CqlIdentifier cqlId : Flag.ALL_LOWERCASE) {
            if (TypeCodecs.BOOLEAN.decodePrimitive(row.getBytesUnsafe(cqlId), protocolVersion)) {
                flags.add(Flag.JAVAX_MAIL_FLAG.get(cqlId));
            }
        }
        row.get(Flag.USER_FLAGS, SET_OF_STRINGS_CODEC)
            .forEach(flags::add);
        return flags;
    }

    public static Flags getApplicableFlags(Row row) {
        Flags flags = new Flags();
        row.get(Flag.USER_FLAGS, SET_OF_STRINGS_CODEC)
            .forEach(flags::add);
        return flags;
    }
}
