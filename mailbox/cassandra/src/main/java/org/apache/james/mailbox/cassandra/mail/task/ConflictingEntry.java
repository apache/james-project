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

package org.apache.james.mailbox.cassandra.mail.task;

import java.util.Objects;

import org.apache.james.mailbox.cassandra.mail.CassandraIdAndPath;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class ConflictingEntry {
    public static class DaoEntry {
        private final String mailboxPath;
        private final String mailboxId;

        public DaoEntry(MailboxPath mailboxPath,
                        MailboxId mailboxId) {
            this(mailboxPath.asString(), mailboxId.serialize());
        }

        private DaoEntry(@JsonProperty("mailboxPath") String mailboxPath,
                         @JsonProperty("mailboxId") String mailboxId) {
            this.mailboxPath = mailboxPath;
            this.mailboxId = mailboxId;
        }

        public String getMailboxPath() {
            return mailboxPath;
        }

        public String getMailboxId() {
            return mailboxId;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DaoEntry) {
                DaoEntry daoEntry = (DaoEntry) o;

                return Objects.equals(this.mailboxPath, daoEntry.mailboxPath)
                    && Objects.equals(this.mailboxId, daoEntry.mailboxId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxPath, mailboxId);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("mailboxPath", mailboxPath)
                .add("mailboxId", mailboxId)
                .toString();
        }
    }

    public interface Builder {
        @FunctionalInterface
        interface RequireMailboxDaoEntry {
            RequireMailboxPathDaoEntry mailboxDaoEntry(DaoEntry daoEntry);

            default RequireMailboxPathDaoEntry mailboxDaoEntry(Mailbox mailbox) {
                return mailboxDaoEntry(mailbox.generateAssociatedPath(), mailbox.getMailboxId());
            }

            default RequireMailboxPathDaoEntry mailboxDaoEntry(MailboxPath path, MailboxId id) {
                return mailboxDaoEntry(new DaoEntry(path, id));
            }
        }

        @FunctionalInterface
        interface RequireMailboxPathDaoEntry {
            ConflictingEntry mailboxPathDaoEntry(DaoEntry daoEntry);

            default ConflictingEntry mailboxPathDaoEntry(CassandraIdAndPath mailbox) {
                return mailboxPathDaoEntry(mailbox.getMailboxPath(), mailbox.getCassandraId());
            }

            default ConflictingEntry mailboxPathDaoEntry(MailboxPath path, MailboxId id) {
                return mailboxPathDaoEntry(new DaoEntry(path, id));
            }
        }
    }

    public static Builder.RequireMailboxDaoEntry builder() {
        return mailboxDaoEntry -> mailboxPathDaoEntry -> new ConflictingEntry(mailboxDaoEntry, mailboxPathDaoEntry);
    }

    private final DaoEntry mailboxDaoEntry;
    private final DaoEntry mailboxPathDaoEntry;

    private ConflictingEntry(@JsonProperty("mailboxDaoEntry") DaoEntry mailboxDaoEntry,
                             @JsonProperty("mailboxPathDaoEntry") DaoEntry mailboxPathDaoEntry) {
        this.mailboxDaoEntry = mailboxDaoEntry;
        this.mailboxPathDaoEntry = mailboxPathDaoEntry;
    }

    public DaoEntry getMailboxDaoEntry() {
        return mailboxDaoEntry;
    }

    public DaoEntry getMailboxPathDaoEntry() {
        return mailboxPathDaoEntry;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ConflictingEntry) {
            ConflictingEntry that = (ConflictingEntry) o;

            return Objects.equals(this.mailboxDaoEntry, that.mailboxDaoEntry)
                && Objects.equals(this.mailboxPathDaoEntry, that.mailboxPathDaoEntry);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxDaoEntry, mailboxPathDaoEntry);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxDaoEntry", mailboxDaoEntry)
            .add("mailboxPathDaoEntry", mailboxPathDaoEntry)
            .toString();
    }
}
