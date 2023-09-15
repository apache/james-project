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

package org.apache.james.jmap.cassandra.change;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

public class CassandraChangesConfiguration {
    public static final Duration DEFAULT_TTL = Duration.ofDays(60);
    public static final CassandraChangesConfiguration DEFAULT = builder().build();

    public static class Builder {
        private Optional<Duration> emailChangeTtl = Optional.empty();
        private Optional<Duration> mailboxChangeTtl = Optional.empty();

        public Builder emailChangeTtl(Duration emailChangeTtl) {
            this.emailChangeTtl = Optional.of(emailChangeTtl);
            return this;
        }

        public Builder emailChangeTtl(Optional<Duration> emailChangeTtl) {
            emailChangeTtl.ifPresent(this::emailChangeTtl);
            return this;
        }

        public Builder mailboxChangeTtl(Duration mailboxChangeTtl) {
            this.mailboxChangeTtl = Optional.of(mailboxChangeTtl);
            return this;
        }

        public Builder mailboxChangeTtl(Optional<Duration> mailboxChangeTtl) {
            mailboxChangeTtl.ifPresent(this::mailboxChangeTtl);
            return this;
        }

        public CassandraChangesConfiguration build() {
            return new CassandraChangesConfiguration(emailChangeTtl.orElse(DEFAULT_TTL),
                mailboxChangeTtl.orElse(DEFAULT_TTL));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CassandraChangesConfiguration from(Configuration configuration) {
        Optional<Duration> emailChangeTtl = Optional.ofNullable(configuration.getString("email.change.ttl", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS));

        Optional<Duration> mailboxChangeTtl = Optional.ofNullable(configuration.getString("mailbox.change.ttl", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS));

        return new Builder()
            .emailChangeTtl(emailChangeTtl)
            .mailboxChangeTtl(mailboxChangeTtl)
            .build();
    }

    private final Duration emailChangeTtl;
    private final Duration mailboxChangeTtl;

    private CassandraChangesConfiguration(Duration emailChangeTtl, Duration mailboxChangeTtl) {
        Preconditions.checkArgument(emailChangeTtl.getSeconds() > 0, "'TTL' needs to be positive");
        Preconditions.checkArgument(emailChangeTtl.getSeconds() < Integer.MAX_VALUE,
            "'TTL' must not greater than %s sec", Integer.MAX_VALUE);

        Preconditions.checkArgument(mailboxChangeTtl.getSeconds() > 0, "'TTL' needs to be positive");
        Preconditions.checkArgument(mailboxChangeTtl.getSeconds() < Integer.MAX_VALUE,
            "'TTL' must not greater than %s sec", Integer.MAX_VALUE);

        this.emailChangeTtl = emailChangeTtl;
        this.mailboxChangeTtl = mailboxChangeTtl;
    }

    public Duration getEmailChangeTtl() {
        return emailChangeTtl;
    }

    public Duration getMailboxChangeTtl() {
        return mailboxChangeTtl;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraChangesConfiguration) {
            CassandraChangesConfiguration that = (CassandraChangesConfiguration) o;

            return Objects.equals(this.emailChangeTtl, that.emailChangeTtl)
                && Objects.equals(this.mailboxChangeTtl, that.mailboxChangeTtl);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(emailChangeTtl, mailboxChangeTtl);
    }
}
