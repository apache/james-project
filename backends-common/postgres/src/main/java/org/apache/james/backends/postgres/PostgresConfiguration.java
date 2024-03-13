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

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;

import io.r2dbc.postgresql.client.SSLMode;

public class PostgresConfiguration {
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
    public static final String NON_RLS_USERNAME = "database.non-rls.username";
    public static final String NON_RLS_PASSWORD = "database.non-rls.password";
    public static final String RLS_ENABLED = "row.level.security.enabled";
    public static final String SSL_MODE = "ssl.mode";
    public static final String SSL_MODE_DEFAULT_VALUE = "disable";

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
        private Optional<String> nonRLSUser = Optional.empty();
        private Optional<String> nonRLSPassword = Optional.empty();
        private Optional<Boolean> rowLevelSecurityEnabled = Optional.empty();
        private Optional<String> sslMode = Optional.empty();

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

        public Builder nonRLSUser(String nonRLSUser) {
            this.nonRLSUser = Optional.of(nonRLSUser);
            return this;
        }

        public Builder nonRLSUser(Optional<String> nonRLSUser) {
            this.nonRLSUser = nonRLSUser;
            return this;
        }

        public Builder nonRLSPassword(String nonRLSPassword) {
            this.nonRLSPassword = Optional.of(nonRLSPassword);
            return this;
        }

        public Builder nonRLSPassword(Optional<String> nonRLSPassword) {
            this.nonRLSPassword = nonRLSPassword;
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

        public Builder sslMode(Optional<String> sslMode) {
            this.sslMode = sslMode;
            return this;
        }

        public Builder sslMode(String sslMode) {
            this.sslMode = Optional.of(sslMode);
            return this;
        }

        public PostgresConfiguration build() {
            Preconditions.checkArgument(username.isPresent() && !username.get().isBlank(), "You need to specify username");
            Preconditions.checkArgument(password.isPresent() && !password.get().isBlank(), "You need to specify password");

            if (rowLevelSecurityEnabled.isPresent() && rowLevelSecurityEnabled.get()) {
                Preconditions.checkArgument(nonRLSUser.isPresent() && !nonRLSUser.get().isBlank(), "You need to specify nonRLSUser");
                Preconditions.checkArgument(nonRLSPassword.isPresent() && !nonRLSPassword.get().isBlank(), "You need to specify nonRLSPassword");
            }

            return new PostgresConfiguration(host.orElse(HOST_DEFAULT_VALUE),
                port.orElse(PORT_DEFAULT_VALUE),
                databaseName.orElse(DATABASE_NAME_DEFAULT_VALUE),
                databaseSchema.orElse(DATABASE_SCHEMA_DEFAULT_VALUE),
                new Credential(username.get(), password.get()),
                new Credential(nonRLSUser.orElse(username.get()), nonRLSPassword.orElse(password.get())),
                rowLevelSecurityEnabled.orElse(false),
                SSLMode.fromValue(sslMode.orElse(SSL_MODE_DEFAULT_VALUE)));
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
            .nonRLSUser(Optional.ofNullable(propertiesConfiguration.getString(NON_RLS_USERNAME)))
            .nonRLSPassword(Optional.ofNullable(propertiesConfiguration.getString(NON_RLS_PASSWORD)))
            .rowLevelSecurityEnabled(propertiesConfiguration.getBoolean(RLS_ENABLED, false))
            .sslMode(Optional.ofNullable(propertiesConfiguration.getString(SSL_MODE)))
            .build();
    }

    private final String host;
    private final int port;
    private final String databaseName;
    private final String databaseSchema;
    private final Credential credential;
    private final Credential nonRLSCredential;
    private final boolean rowLevelSecurityEnabled;
    private final SSLMode sslMode;

    private PostgresConfiguration(String host, int port, String databaseName, String databaseSchema,
                                  Credential credential, Credential nonRLSCredential, boolean rowLevelSecurityEnabled,
                                  SSLMode sslMode) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.databaseSchema = databaseSchema;
        this.credential = credential;
        this.nonRLSCredential = nonRLSCredential;
        this.rowLevelSecurityEnabled = rowLevelSecurityEnabled;
        this.sslMode = sslMode;
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

    public Credential getCredential() {
        return credential;
    }

    public Credential getNonRLSCredential() {
        return nonRLSCredential;
    }

    public boolean rowLevelSecurityEnabled() {
        return rowLevelSecurityEnabled;
    }

    public SSLMode getSslMode() {
        return sslMode;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(host, port, databaseName, databaseSchema, credential, nonRLSCredential, rowLevelSecurityEnabled, sslMode);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PostgresConfiguration) {
            PostgresConfiguration that = (PostgresConfiguration) o;

            return Objects.equals(this.rowLevelSecurityEnabled, that.rowLevelSecurityEnabled)
                && Objects.equals(this.host, that.host)
                && Objects.equals(this.port, that.port)
                && Objects.equals(this.credential, that.credential)
                && Objects.equals(this.nonRLSCredential, that.nonRLSCredential)
                && Objects.equals(this.databaseName, that.databaseName)
                && Objects.equals(this.databaseSchema, that.databaseSchema)
                && Objects.equals(this.sslMode, that.sslMode);
        }
        return false;
    }
}
