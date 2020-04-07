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

package org.apache.james.modules.mailbox;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;

import com.google.common.base.Preconditions;

public class KeyspacesConfiguration {
    public static class Builder {
        private Optional<String> keyspace;
        private Optional<String> cacheKeyspace;
        private Optional<Integer> replicationFactor;
        private Optional<Boolean> durableWrites;

        public Builder() {
            keyspace = Optional.empty();
            cacheKeyspace = Optional.empty();
            replicationFactor = Optional.empty();
            durableWrites = Optional.empty();
        }

        public Builder keyspace(Optional<String> keyspace) {
            this.keyspace = keyspace;
            return this;
        }

        public Builder cacheKeyspace(Optional<String> cacheKeyspace) {
            this.cacheKeyspace = cacheKeyspace;
            return this;
        }

        public Builder cacheKeyspace(String cacheKeyspace) {
            return cacheKeyspace(Optional.of(cacheKeyspace));
        }

        public Builder keyspace(String keyspace) {
            return keyspace(Optional.of(keyspace));
        }

        public Builder replicationFactor(Optional<Integer> replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }

        public Builder replicationFactor(int replicationFactor) {
            return replicationFactor(Optional.of(replicationFactor));
        }

        public Builder disableDurableWrites() {
            this.durableWrites = Optional.of(false);

            return this;
        }

        public KeyspacesConfiguration build() {
            String keyspace = this.keyspace.orElse(DEFAULT_KEYSPACE);
            String cacheKeyspace = this.cacheKeyspace.orElse(DEFAULT_CACHE_KEYSPACE);
            Preconditions.checkState(!keyspace.equals(cacheKeyspace),
                "'cassandra.keyspace' and 'cassandra.keyspace.cache' needs to have distinct values");

            return new KeyspacesConfiguration(
                keyspace,
                cacheKeyspace,
                replicationFactor.orElse(DEFAULT_REPLICATION_FACTOR),
                durableWrites.orElse(true));
        }
    }

    public static final String CASSANDRA_KEYSPACE = "cassandra.keyspace";
    public static final String CASSANDRA_CACHE_KEYSPACE = "cassandra.keyspace.cache";
    public static final String REPLICATION_FACTOR = "cassandra.replication.factor";

    private static final String DEFAULT_KEYSPACE = "apache_james";
    private static final String DEFAULT_CACHE_KEYSPACE = "apache_james_cache";
    private static final int DEFAULT_REPLICATION_FACTOR = 1;


    public static Builder builder() {
        return new Builder();
    }

    public static KeyspacesConfiguration from(Configuration configuration) {
        return KeyspacesConfiguration.builder()
            .keyspace(Optional.ofNullable(configuration.getString(CASSANDRA_KEYSPACE, null)))
            .cacheKeyspace(Optional.ofNullable(configuration.getString(CASSANDRA_CACHE_KEYSPACE, null)))
            .replicationFactor(Optional.ofNullable(configuration.getInteger(REPLICATION_FACTOR, null)))
            .build();
    }

    private final String keyspace;
    private final String cacheKeyspace;
    private final int replicationFactor;
    private final boolean durableWrites;

    public KeyspacesConfiguration(String keyspace, String cacheKeyspace, int replicationFactor, boolean durableWrites) {
        this.keyspace = keyspace;
        this.cacheKeyspace = cacheKeyspace;
        this.replicationFactor = replicationFactor;
        this.durableWrites = durableWrites;
    }

    public KeyspaceConfiguration mainKeyspaceConfiguration() {
        return KeyspaceConfiguration.builder()
            .keyspace(keyspace)
            .replicationFactor(replicationFactor)
            .durableWrites(durableWrites);
    }

    public KeyspaceConfiguration cacheKeyspaceConfiguration() {
        return KeyspaceConfiguration.builder()
            .keyspace(cacheKeyspace)
            .replicationFactor(1)
            .durableWrites(durableWrites);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof KeyspacesConfiguration) {
            KeyspacesConfiguration that = (KeyspacesConfiguration) o;

            return Objects.equals(this.replicationFactor, that.replicationFactor)
                && Objects.equals(this.durableWrites, that.durableWrites)
                && Objects.equals(this.keyspace, that.keyspace)
                && Objects.equals(this.cacheKeyspace, that.cacheKeyspace);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(keyspace, cacheKeyspace, replicationFactor, durableWrites);
    }
}
