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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

import io.r2dbc.postgresql.client.SSLMode;

public class PostgresConfiguration {
    public static final String POSTGRES_CONFIGURATION_NAME = "postgres";

    public static final String DATABASE_NAME = "database.name";
    public static final String DATABASE_NAME_DEFAULT_VALUE = "postgres";
    public static final String DATABASE_SCHEMA = "database.schema";
    public static final String DATABASE_SCHEMA_DEFAULT_VALUE = "public";
    public static final String HOST = "database.host";
    public static final String HOST_DEFAULT_VALUE = "localhost";
    public static final String PORT = "database.port";
    public static final int PORT_DEFAULT_VALUE = 5432;
    public static final String USERNAME = "database.username";
    public static final String PASSWORD = "database.password";
    public static final String BY_PASS_RLS_USERNAME = "database.by-pass-rls.username";
    public static final String BY_PASS_RLS_PASSWORD = "database.by-pass-rls.password";
    public static final String RLS_ENABLED = "row.level.security.enabled";
    public static final String POOL_INITIAL_SIZE = "pool.initial.size";
    public static final int POOL_INITIAL_SIZE_DEFAULT_VALUE = 10;
    public static final String POOL_MAX_SIZE = "pool.max.size";
    public static final int POOL_MAX_SIZE_DEFAULT_VALUE = 15;
    public static final String BY_PASS_RLS_POOL_INITIAL_SIZE = "by-pass-rls.pool.initial.size";
    public static final int BY_PASS_RLS_POOL_INITIAL_SIZE_DEFAULT_VALUE = 5;
    public static final String BY_PASS_RLS_POOL_MAX_SIZE = "by-pass-rls.pool.max.size";
    public static final int BY_PASS_RLS_POOL_MAX_SIZE_DEFAULT_VALUE = 10;
    public static final String SSL_MODE = "ssl.mode";
    public static final String SSL_MODE_DEFAULT_VALUE = "allow";
    public static final String JOOQ_REACTIVE_TIMEOUT = "jooq.reactive.timeout";
    public static final Duration JOOQ_REACTIVE_TIMEOUT_DEFAULT_VALUE = Duration.ofSeconds(10);
    public static final String ATTACHMENT_STORAGE_ENABLED = "attachment.storage.enabled";
    public static final boolean ATTACHMENT_STORAGE_ENABLED_DEFAULT_VALUE = true;

    public static class Credential {
        private final String username;
        private final String password;


        public Credential(String username, String password) {
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
        private Optional<String> databaseName = Optional.empty();
        private Optional<String> databaseSchema = Optional.empty();
        private Optional<String> host = Optional.empty();
        private Optional<Integer> port = Optional.empty();
        private Optional<String> username = Optional.empty();
        private Optional<String> password = Optional.empty();
        private Optional<String> byPassRLSUser = Optional.empty();
        private Optional<String> byPassRLSPassword = Optional.empty();
        private Optional<Boolean> rowLevelSecurityEnabled = Optional.empty();
        private Optional<Integer> poolInitialSize = Optional.empty();
        private Optional<Integer> poolMaxSize = Optional.empty();
        private Optional<Integer> byPassRLSPoolInitialSize = Optional.empty();
        private Optional<Integer> byPassRLSPoolMaxSize = Optional.empty();
        private Optional<String> sslMode = Optional.empty();
        private Optional<Duration> jooqReactiveTimeout = Optional.empty();
        private Optional<Boolean> attachmentStorageEnabled = Optional.empty();

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

        public Builder host(String host) {
            this.host = Optional.of(host);
            return this;
        }

        public Builder host(Optional<String> host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder port(Optional<Integer> port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = Optional.of(username);
            return this;
        }

        public Builder username(Optional<String> username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = Optional.of(password);
            return this;
        }

        public Builder password(Optional<String> password) {
            this.password = password;
            return this;
        }

        public Builder byPassRLSUser(String byPassRLSUser) {
            this.byPassRLSUser = Optional.of(byPassRLSUser);
            return this;
        }

        public Builder byPassRLSUser(Optional<String> byPassRLSUser) {
            this.byPassRLSUser = byPassRLSUser;
            return this;
        }

        public Builder byPassRLSPassword(String byPassRLSPassword) {
            this.byPassRLSPassword = Optional.of(byPassRLSPassword);
            return this;
        }

        public Builder byPassRLSPassword(Optional<String> byPassRLSPassword) {
            this.byPassRLSPassword = byPassRLSPassword;
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

        public Builder poolInitialSize(Optional<Integer> poolInitialSize) {
            this.poolInitialSize = poolInitialSize;
            return this;
        }

        public Builder poolInitialSize(Integer poolInitialSize) {
            this.poolInitialSize = Optional.of(poolInitialSize);
            return this;
        }

        public Builder poolMaxSize(Optional<Integer> poolMaxSize) {
            this.poolMaxSize = poolMaxSize;
            return this;
        }

        public Builder poolMaxSize(Integer poolMaxSize) {
            this.poolMaxSize = Optional.of(poolMaxSize);
            return this;
        }

        public Builder byPassRLSPoolInitialSize(Optional<Integer> byPassRLSPoolInitialSize) {
            this.byPassRLSPoolInitialSize = byPassRLSPoolInitialSize;
            return this;
        }

        public Builder byPassRLSPoolInitialSize(Integer byPassRLSPoolInitialSize) {
            this.byPassRLSPoolInitialSize = Optional.of(byPassRLSPoolInitialSize);
            return this;
        }

        public Builder byPassRLSPoolMaxSize(Optional<Integer> byPassRLSPoolMaxSize) {
            this.byPassRLSPoolMaxSize = byPassRLSPoolMaxSize;
            return this;
        }

        public Builder byPassRLSPoolMaxSize(Integer byPassRLSPoolMaxSize) {
            this.byPassRLSPoolMaxSize = Optional.of(byPassRLSPoolMaxSize);
            return this;
        }

        public Builder sslMode(Optional<String> sslMode) {
            this.sslMode = sslMode;
            return this;
        }

        public Builder sslMode(String sslMode) {
            this.sslMode = Optional.of(sslMode);
            return this;
        }

        public Builder jooqReactiveTimeout(Optional<Duration> jooqReactiveTimeout) {
            this.jooqReactiveTimeout = jooqReactiveTimeout;
            return this;
        }

        public Builder attachmentStorageEnabled(Optional<Boolean> attachmentStorageEnabled) {
            this.attachmentStorageEnabled = attachmentStorageEnabled;
            return this;
        }

        public Builder attachmentStorageEnabled(Boolean attachmentStorageEnabled) {
            this.attachmentStorageEnabled = Optional.of(attachmentStorageEnabled);
            return this;
        }

        public PostgresConfiguration build() {
            Preconditions.checkArgument(username.isPresent() && !username.get().isBlank(), "You need to specify username");
            Preconditions.checkArgument(password.isPresent() && !password.get().isBlank(), "You need to specify password");

            if (rowLevelSecurityEnabled.isPresent() && rowLevelSecurityEnabled.get()) {
                Preconditions.checkArgument(byPassRLSUser.isPresent() && !byPassRLSUser.get().isBlank(), "You need to specify byPassRLSUser");
                Preconditions.checkArgument(byPassRLSPassword.isPresent() && !byPassRLSPassword.get().isBlank(), "You need to specify byPassRLSPassword");
            }

            return new PostgresConfiguration(host.orElse(HOST_DEFAULT_VALUE),
                port.orElse(PORT_DEFAULT_VALUE),
                databaseName.orElse(DATABASE_NAME_DEFAULT_VALUE),
                databaseSchema.orElse(DATABASE_SCHEMA_DEFAULT_VALUE),
                new Credential(username.get(), password.get()),
                new Credential(byPassRLSUser.orElse(username.get()), byPassRLSPassword.orElse(password.get())),
                    rowLevelSecurityEnabled.filter(rlsEnabled -> rlsEnabled).map(rlsEnabled -> RowLevelSecurity.ENABLED).orElse(RowLevelSecurity.DISABLED),
                poolInitialSize.orElse(POOL_INITIAL_SIZE_DEFAULT_VALUE),
                poolMaxSize.orElse(POOL_MAX_SIZE_DEFAULT_VALUE),
                byPassRLSPoolInitialSize.orElse(BY_PASS_RLS_POOL_INITIAL_SIZE_DEFAULT_VALUE),
                byPassRLSPoolMaxSize.orElse(BY_PASS_RLS_POOL_MAX_SIZE_DEFAULT_VALUE),
                SSLMode.fromValue(sslMode.orElse(SSL_MODE_DEFAULT_VALUE)),
                jooqReactiveTimeout.orElse(JOOQ_REACTIVE_TIMEOUT_DEFAULT_VALUE),
                attachmentStorageEnabled.orElse(ATTACHMENT_STORAGE_ENABLED_DEFAULT_VALUE));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PostgresConfiguration from(Configuration propertiesConfiguration) {
        return builder()
            .databaseName(Optional.ofNullable(propertiesConfiguration.getString(DATABASE_NAME)))
            .databaseSchema(Optional.ofNullable(propertiesConfiguration.getString(DATABASE_SCHEMA)))
            .host(Optional.ofNullable(propertiesConfiguration.getString(HOST)))
            .port(propertiesConfiguration.getInt(PORT, PORT_DEFAULT_VALUE))
            .username(Optional.ofNullable(propertiesConfiguration.getString(USERNAME)))
            .password(Optional.ofNullable(propertiesConfiguration.getString(PASSWORD)))
            .byPassRLSUser(Optional.ofNullable(propertiesConfiguration.getString(BY_PASS_RLS_USERNAME)))
            .byPassRLSPassword(Optional.ofNullable(propertiesConfiguration.getString(BY_PASS_RLS_PASSWORD)))
            .rowLevelSecurityEnabled(propertiesConfiguration.getBoolean(RLS_ENABLED, false))
            .poolInitialSize(Optional.ofNullable(propertiesConfiguration.getInteger(POOL_INITIAL_SIZE, null)))
            .poolMaxSize(Optional.ofNullable(propertiesConfiguration.getInteger(POOL_MAX_SIZE, null)))
            .byPassRLSPoolInitialSize(Optional.ofNullable(propertiesConfiguration.getInteger(BY_PASS_RLS_POOL_INITIAL_SIZE, null)))
            .byPassRLSPoolMaxSize(Optional.ofNullable(propertiesConfiguration.getInteger(BY_PASS_RLS_POOL_MAX_SIZE, null)))
            .sslMode(Optional.ofNullable(propertiesConfiguration.getString(SSL_MODE)))
            .jooqReactiveTimeout(Optional.ofNullable(propertiesConfiguration.getString(JOOQ_REACTIVE_TIMEOUT))
                .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS)))
            .attachmentStorageEnabled(propertiesConfiguration.getBoolean(ATTACHMENT_STORAGE_ENABLED, ATTACHMENT_STORAGE_ENABLED_DEFAULT_VALUE))
            .build();
    }

    private final String host;
    private final int port;
    private final String databaseName;
    private final String databaseSchema;
    private final Credential defaultCredential;
    private final Credential byPassRLSCredential;
    private final RowLevelSecurity rowLevelSecurity;
    private final Integer poolInitialSize;
    private final Integer poolMaxSize;
    private final Integer byPassRLSPoolInitialSize;
    private final Integer byPassRLSPoolMaxSize;
    private final SSLMode sslMode;
    private final Duration jooqReactiveTimeout;
    private final boolean attachmentStorageEnabled;

    private PostgresConfiguration(String host, int port, String databaseName, String databaseSchema,
                                  Credential defaultCredential, Credential byPassRLSCredential, RowLevelSecurity rowLevelSecurity,
                                  Integer poolInitialSize, Integer poolMaxSize,
                                  Integer byPassRLSPoolInitialSize, Integer byPassRLSPoolMaxSize,
                                  SSLMode sslMode, Duration jooqReactiveTimeout, boolean attachmentStorageEnabled) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.databaseSchema = databaseSchema;
        this.defaultCredential = defaultCredential;
        this.byPassRLSCredential = byPassRLSCredential;
        this.rowLevelSecurity = rowLevelSecurity;
        this.poolInitialSize = poolInitialSize;
        this.poolMaxSize = poolMaxSize;
        this.byPassRLSPoolInitialSize = byPassRLSPoolInitialSize;
        this.byPassRLSPoolMaxSize = byPassRLSPoolMaxSize;
        this.sslMode = sslMode;
        this.jooqReactiveTimeout = jooqReactiveTimeout;
        this.attachmentStorageEnabled = attachmentStorageEnabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public Credential getDefaultCredential() {
        return defaultCredential;
    }

    public Credential getByPassRLSCredential() {
        return byPassRLSCredential;
    }

    public RowLevelSecurity getRowLevelSecurity() {
        return rowLevelSecurity;
    }

    public Integer poolInitialSize() {
        return poolInitialSize;
    }

    public Integer poolMaxSize() {
        return poolMaxSize;
    }

    public Integer byPassRLSPoolInitialSize() {
        return byPassRLSPoolInitialSize;
    }

    public Integer byPassRLSPoolMaxSize() {
        return byPassRLSPoolMaxSize;
    }

    public SSLMode getSslMode() {
        return sslMode;
    }

    public Duration getJooqReactiveTimeout() {
        return jooqReactiveTimeout;
    }

    public boolean isAttachmentStorageEnabled() {
        return attachmentStorageEnabled;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(host, port, databaseName, databaseSchema, defaultCredential, byPassRLSCredential, rowLevelSecurity, poolInitialSize, poolMaxSize, sslMode, jooqReactiveTimeout, attachmentStorageEnabled);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PostgresConfiguration) {
            PostgresConfiguration that = (PostgresConfiguration) o;

            return Objects.equals(this.rowLevelSecurity, that.rowLevelSecurity)
                && Objects.equals(this.host, that.host)
                && Objects.equals(this.port, that.port)
                && Objects.equals(this.defaultCredential, that.defaultCredential)
                && Objects.equals(this.byPassRLSCredential, that.byPassRLSCredential)
                && Objects.equals(this.databaseName, that.databaseName)
                && Objects.equals(this.databaseSchema, that.databaseSchema)
                && Objects.equals(this.poolInitialSize, that.poolInitialSize)
                && Objects.equals(this.poolMaxSize, that.poolMaxSize)
                && Objects.equals(this.sslMode, that.sslMode)
                && Objects.equals(this.jooqReactiveTimeout, that.jooqReactiveTimeout)
                && Objects.equals(this.attachmentStorageEnabled, that.attachmentStorageEnabled);
        }
        return false;
    }
}
