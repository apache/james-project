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

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.mail.Flags;

import org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageIds;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;
import org.apache.james.mailbox.cassandra.table.Flag;
import org.apache.james.mailbox.store.StoreMessageManager;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

public class FlagsExtractor {
    public static final TypeCodec<Set<String>> SET_OF_STRINGS_CODEC = CodecRegistry.DEFAULT.codecFor(setOf(TEXT));

    /**
     * Immutable row reader whose column indices are pre-computed once from the
     * {@link ColumnDefinitions} of the first row in a ResultSet.  All subsequent
     * rows of the same ResultSet reuse those indices, eliminating the per-row
     * {@code ColumnDefinitions.firstIndexOf} scan which takes 50% of the Cassandra
     * CPU post treatment time where in use.
     */
    public static class Optimized {
        private final int[] flagIndices;
        private final int userFlagsIndex;
        private final int messageIdIndex;
        private final int mailboxIdIndex;
        private final int imapUidIndex;
        private final int modSeqIndex;
        private final int threadIdIndex;
        private final int internalDateIndex;

        private final ProtocolVersion protocolVersion;

        public static Optimized of(Row row) {
            return new Optimized(row.getColumnDefinitions(), row.protocolVersion());
        }

        private Optimized(ColumnDefinitions defs, ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;

            this.flagIndices = new int[Flag.ALL_LOWERCASE.length];
            for (int i = 0; i < Flag.ALL_LOWERCASE.length; i++) {
                this.flagIndices[i] = defs.firstIndexOf(Flag.ALL_LOWERCASE[i]);
            }
            this.userFlagsIndex = defs.firstIndexOf(Flag.USER_FLAGS);

            this.messageIdIndex = defs.firstIndexOf(CassandraMessageIds.MESSAGE_ID);
            this.mailboxIdIndex = defs.firstIndexOf(CassandraMessageIds.MAILBOX_ID);
            this.imapUidIndex = defs.firstIndexOf(CassandraMessageIds.IMAP_UID);
            this.modSeqIndex = defs.firstIndexOf(CassandraMessageIdTable.MOD_SEQ);
            this.threadIdIndex = defs.firstIndexOf(CassandraMessageIdTable.THREAD_ID);
            this.internalDateIndex = defs.firstIndexOf(CassandraMessageV3Table.INTERNAL_DATE);
        }

        public Flags getFlags(Row row) {
            Flags flags = new Flags();
            for (int i = 0; i < Flag.ALL_LOWERCASE.length; i++) {
                CqlIdentifier cqlId = Flag.ALL_LOWERCASE[i];
                if (!StoreMessageManager.HANDLE_RECENT && cqlId.equals(Flag.RECENT)) {
                    continue;
                }
                if (TypeCodecs.BOOLEAN.decodePrimitive(row.getBytesUnsafe(flagIndices[i]), protocolVersion)) {
                    flags.add(Flag.JAVAX_MAIL_FLAG.get(cqlId));
                }
            }
            row.get(userFlagsIndex, SET_OF_STRINGS_CODEC).forEach(flags::add);
            return flags;
        }

        public UUID getMessageId(Row row) {
            return TypeCodecs.TIMEUUID.decode(row.getBytesUnsafe(messageIdIndex), protocolVersion);
        }

        public UUID getMailboxId(Row row) {
            return TypeCodecs.TIMEUUID.decode(row.getBytesUnsafe(mailboxIdIndex), protocolVersion);
        }

        public long getImapUid(Row row) {
            return TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(imapUidIndex), protocolVersion);
        }

        public long getModSeq(Row row) {
            return TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(modSeqIndex), protocolVersion);
        }

        public UUID getThreadId(Row row) {
            return TypeCodecs.TIMEUUID.decode(row.getBytesUnsafe(threadIdIndex), protocolVersion);
        }

        public Instant getInternalDate(Row row) {
            return TypeCodecs.TIMESTAMP.decode(row.getBytesUnsafe(internalDateIndex), protocolVersion);
        }
    }

    public static Flags getFlags(Row row) {
        return getFlags(row, row.protocolVersion());
    }

    private static Flags getFlags(Row row, ProtocolVersion protocolVersion) {
        Flags flags = new Flags();
        for (CqlIdentifier cqlId : Flag.ALL_LOWERCASE) {
            if (!StoreMessageManager.HANDLE_RECENT && cqlId.equals(Flag.RECENT)) {
                continue;
            }
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
