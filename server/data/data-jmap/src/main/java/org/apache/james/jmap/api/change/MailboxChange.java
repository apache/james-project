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

package org.apache.james.jmap.api.change;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.MailboxId;

public class MailboxChange {

    public static class State {

        public static State of(UUID value) {
            return new State(value);
        }

        private final UUID value;

        private State(UUID value) {
            this.value = value;
        }

        public UUID getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof State) {
                State state = (State) o;

                return Objects.equals(this.value, state.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }
    }

    public static class Limit {

        public static Limit of(int value) {
            return new Limit(value);
        }

        private final int value;

        private Limit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static MailboxChange of(AccountId accountId, State state,  ZonedDateTime date, List<MailboxId> created, List<MailboxId> updated, List<MailboxId> destroyed) {
        return new MailboxChange(accountId, state, date, created, updated, destroyed);
    }

    private final AccountId accountId;
    private final State state;
    private final ZonedDateTime date;
    private final List<MailboxId> created;
    private final List<MailboxId> updated;
    private final List<MailboxId> destroyed;

    private MailboxChange(AccountId accountId, State state, ZonedDateTime date, List<MailboxId> created, List<MailboxId> updated, List<MailboxId> destroyed) {
        this.accountId = accountId;
        this.state = state;
        this.date = date;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public State getState() {
        return state;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public List<MailboxId> getCreated() {
        return created;
    }

    public List<MailboxId> getUpdated() {
        return updated;
    }

    public List<MailboxId> getDestroyed() {
        return destroyed;
    }
}
