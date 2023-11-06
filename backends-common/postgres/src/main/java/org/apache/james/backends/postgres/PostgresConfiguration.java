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

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class PostgresConfiguration {
    public static final String URL = "url";
    public static final String DATABASE_NAME = "database.name";
    public static final String DATABASE_NAME_DEFAULT_VALUE = "postgres";
    public static final String DATABASE_SCHEMA = "database.schema";
    public static final String DATABASE_SCHEMA_DEFAULT_VALUE = "public";
    public static final String RLS_ENABLED = "row.level.security.enabled";

    static class Credential {
        private final String username;
        private final String password;

        Credential(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    public static class Builder {
        private Optional<String> url = Optional.empty();
        private Optional<String> databaseName = Optional.empty();
        private Optional<String> databaseSchema = Optional.empty();
        private Optional<Boolean> rlsEnabled = Optional.empty();

        public Builder url(String url) {
            this.url = Optional.of(url);
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = Optional.of(databaseName);
            return this;
        }

        public Builder databaseName(Optional<String> databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder databaseSchema(String databaseSchema) {
            this.databaseSchema = Optional.of(databaseSchema);
            return this;
        }

        public Builder databaseSchema(Optional<String> databaseSchema) {
            this.databaseSchema = databaseSchema;
            return this;
        }

        public Builder rlsEnabled(boolean rlsEnabled) {
            this.rlsEnabled = Optional.of(rlsEnabled);
            return this;
        }

        public Builder rlsEnabled() {
            this.rlsEnabled = Optional.of(true);
            return this;
        }

        public PostgresConfiguration build() {
            Preconditions.checkArgument(url.isPresent() && !url.get().isBlank(), "You need to specify Postgres URI");
            URI postgresURI = asURI(url.get());

            return new PostgresConfiguration(postgresURI,
                parseCredential(postgresURI),
                databaseName.orElse(DATABASE_NAME_DEFAULT_VALUE),
                databaseSchema.orElse(DATABASE_SCHEMA_DEFAULT_VALUE),
                rlsEnabled.orElse(false));
        }

        private Credential parseCredential(URI postgresURI) {
            Preconditions.checkArgument(postgresURI.getUserInfo() != null, "Postgres URI need to contains user credential");
            Preconditions.checkArgument(postgresURI.getUserInfo().contains(":"), "User info needs a password part");

            List<String> parts = Splitter.on(':')
                .splitToList(postgresURI.getUserInfo());
            ImmutableList<String> passwordParts = parts.stream()
                .skip(1)
                .collect(ImmutableList.toImmutableList());

            return new Credential(parts.get(0), Joiner.on(':').join(passwordParts));
        }

        private URI asURI(String uri) {
            try {
                return URI.create(uri);
            } catch (Exception e) {
                throw new IllegalArgumentException("You need to specify a valid Postgres URI", e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PostgresConfiguration from(Configuration propertiesConfiguration) {
        return builder()
            .url(propertiesConfiguration.getString(URL, null))
            .databaseName(Optional.ofNullable(propertiesConfiguration.getString(DATABASE_NAME)))
            .databaseSchema(Optional.ofNullable(propertiesConfiguration.getString(DATABASE_SCHEMA)))
            .rlsEnabled(propertiesConfiguration.getBoolean(RLS_ENABLED, false))
            .build();
    }

    private final URI url;
    private final Credential credential;
    private final String databaseName;
    private final String databaseSchema;
    private final boolean rlsEnabled;

    private PostgresConfiguration(URI url, Credential credential, String databaseName, String databaseSchema, boolean rlsEnabled) {
        this.url = url;
        this.credential = credential;
        this.databaseName = databaseName;
        this.databaseSchema = databaseSchema;
        this.rlsEnabled = rlsEnabled;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PostgresConfiguration) {
            PostgresConfiguration that = (PostgresConfiguration) o;

            return Objects.equals(this.rlsEnabled, that.rlsEnabled)
                && Objects.equals(this.url, that.url)
                && Objects.equals(this.credential, that.credential)
                && Objects.equals(this.databaseName, that.databaseName)
                && Objects.equals(this.databaseSchema, that.databaseSchema);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(url, credential, databaseName, databaseSchema, rlsEnabled);
    }

    public URI getUrl() {
        return url;
    }

    public Credential getCredential() {
        return credential;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public boolean rlsEnabled() {
        return rlsEnabled;
    }
}
