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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
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

    public static class Credential {
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

    public static class Pool {
        public static Pool from(Configuration propertiesConfiguration) {
            Optional<Integer> initialSize = Optional.ofNullable(propertiesConfiguration.getInteger("pool.initialSize", null));
            Optional<Integer> maxSize = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxSize", null));
            Optional<Duration> maxIdleTime = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxIdleTime", null))
                .map(Duration::ofSeconds);
            Optional<Integer> acquireRetry = Optional.ofNullable(propertiesConfiguration.getInteger("pool.acquireRetry", null));
            Optional<Integer> minIdle = Optional.ofNullable(propertiesConfiguration.getInteger("pool.minIdle", null));
            Optional<Duration> maxLifeTime = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxLifeTime", null))
                .map(Duration::ofSeconds);
            Optional<Duration> maxAcquireTime = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxAcquireTime", null))
                .map(Duration::ofSeconds);
            Optional<Duration> maxCreateConnectionTime = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxCreateConnectionTime", null))
                .map(Duration::ofSeconds);
            Optional<Duration> maxValidationTime = Optional.ofNullable(propertiesConfiguration.getInteger("pool.maxValidationTime", null))
                .map(Duration::ofSeconds);
            Optional<String> poolName = Optional.ofNullable(propertiesConfiguration.getString("pool.poolName", null));
            return new Pool(initialSize, maxSize, maxIdleTime, acquireRetry, minIdle, maxLifeTime, maxAcquireTime,
                maxCreateConnectionTime, maxValidationTime, poolName);
        }

        private final Optional<Integer> initialSize;
        private final Optional<Integer> maxSize;
        private final Optional<Duration> maxIdleTime;

        private final Optional<Integer> acquireRetry;
        private final Optional<Integer> minIdle;
        private final Optional<Duration> maxLifeTime;
        private final Optional<Duration> maxAcquireTime;
        private final Optional<Duration> maxCreateConnectionTime;
        private final Optional<Duration> maxValidationTime;
        private final Optional<String> poolName;

        public Pool(Optional<Integer> initialSize, Optional<Integer> maxSize, Optional<Duration> maxIdleTime) {
            this(initialSize, maxSize, maxIdleTime, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        private Pool(Optional<Integer> initialSize, Optional<Integer> maxSize, Optional<Duration> maxIdleTime,
                     Optional<Integer> acquireRetry, Optional<Integer> minIdle, Optional<Duration> maxLifeTime,
                     Optional<Duration> maxAcquireTime, Optional<Duration> maxCreateConnectionTime,
                     Optional<Duration> maxValidationTime, Optional<String> poolName) {
            this.initialSize = initialSize;
            this.maxSize = maxSize;
            this.maxIdleTime = maxIdleTime;
            this.acquireRetry = acquireRetry;
            this.minIdle = minIdle;
            this.maxLifeTime = maxLifeTime;
            this.maxAcquireTime = maxAcquireTime;
            this.maxCreateConnectionTime = maxCreateConnectionTime;
            this.maxValidationTime = maxValidationTime;
            this.poolName = poolName;
        }

        public Optional<Integer> getInitialSize() {
            return initialSize;
        }

        public Optional<Integer> getMaxSize() {
            return maxSize;
        }

        public Optional<Duration> getMaxIdleTime() {
            return maxIdleTime;
        }

        public Optional<Integer> getAcquireRetry() {
            return acquireRetry;
        }

        public Optional<Integer> getMinIdle() {
            return minIdle;
        }

        public Optional<Duration> getMaxLifeTime() {
            return maxLifeTime;
        }

        public Optional<Duration> getMaxAcquireTime() {
            return maxAcquireTime;
        }

        public Optional<Duration> getMaxCreateConnectionTime() {
            return maxCreateConnectionTime;
        }

        public Optional<Duration> getMaxValidationTime() {
            return maxValidationTime;
        }

        public Optional<String> getPoolName() {
            return poolName;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("initialSize", initialSize)
                .add("maxSize", maxSize)
                .add("maxIdleTime", maxIdleTime)
                .add("acquireRetry", acquireRetry)
                .add("minIdle", minIdle)
                .add("maxLifeTime", maxLifeTime)
                .add("maxAcquireTime", maxAcquireTime)
                .add("maxCreateConnectionTime", maxCreateConnectionTime)
                .add("maxValidationTime", maxValidationTime)
                .add("poolName", poolName)
                .toString();
        }
    }

    public static class Builder {
        private Optional<String> url = Optional.empty();
        private Optional<String> databaseName = Optional.empty();
        private Optional<String> databaseSchema = Optional.empty();
        private Optional<Boolean> rowLevelSecurityEnabled = Optional.empty();
        private Optional<Pool> poolConfiguration = Optional.empty();

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

        public Builder rowLevelSecurityEnabled(boolean rlsEnabled) {
            this.rowLevelSecurityEnabled = Optional.of(rlsEnabled);
            return this;
        }

        public Builder rowLevelSecurityEnabled() {
            this.rowLevelSecurityEnabled = Optional.of(true);
            return this;
        }

        public Builder configuration(Pool configuration) {
            this.poolConfiguration = Optional.of(configuration);
            return this;
        }

        public PostgresConfiguration build() {
            Preconditions.checkArgument(url.isPresent() && !url.get().isBlank(), "You need to specify Postgres URI");
            URI postgresURI = asURI(url.get());

            return new PostgresConfiguration(postgresURI,
                parseCredential(postgresURI),
                databaseName.orElse(DATABASE_NAME_DEFAULT_VALUE),
                databaseSchema.orElse(DATABASE_SCHEMA_DEFAULT_VALUE),
                rowLevelSecurityEnabled.orElse(false),
                poolConfiguration);
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
            .rowLevelSecurityEnabled(propertiesConfiguration.getBoolean(RLS_ENABLED, false))
            .configuration(Pool.from(propertiesConfiguration))
            .build();
    }

    private final URI uri;
    private final Credential credential;
    private final String databaseName;
    private final String databaseSchema;
    private final boolean rowLevelSecurityEnabled;
    private final Optional<Pool> pool;

    private PostgresConfiguration(URI uri, Credential credential, String databaseName,
                                  String databaseSchema, boolean rowLevelSecurityEnabled,
                                  Optional<Pool> pool) {
        this.uri = uri;
        this.credential = credential;
        this.databaseName = databaseName;
        this.databaseSchema = databaseSchema;
        this.rowLevelSecurityEnabled = rowLevelSecurityEnabled;
        this.pool = pool;
    }

    public URI getUri() {
        return uri;
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

    public boolean rowLevelSecurityEnabled() {
        return rowLevelSecurityEnabled;
    }

    public Optional<Pool> pool() {
        return pool;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PostgresConfiguration) {
            PostgresConfiguration that = (PostgresConfiguration) o;

            return Objects.equals(this.rowLevelSecurityEnabled, that.rowLevelSecurityEnabled)
                && Objects.equals(this.uri, that.uri)
                && Objects.equals(this.credential, that.credential)
                && Objects.equals(this.databaseName, that.databaseName)
                && Objects.equals(this.databaseSchema, that.databaseSchema);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uri, credential, databaseName, databaseSchema, rowLevelSecurityEnabled);
    }
}
