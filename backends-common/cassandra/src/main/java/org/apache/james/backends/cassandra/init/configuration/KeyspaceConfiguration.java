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

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class KeyspaceConfiguration {

    public interface Builder {
        @FunctionalInterface
        interface RequireKeyspace {
            RequireReplicationFactor keyspace(String name);
        }

        @FunctionalInterface
        interface RequireReplicationFactor {
            RequireDurableWrites replicationFactor(int replicationFactor);
        }

        @FunctionalInterface
        interface RequireDurableWrites {
            KeyspaceConfiguration durableWrites(boolean durableWrites);

            default KeyspaceConfiguration disableDurableWrites() {
                return durableWrites(false);
            }
        }
    }

    private static final String DEFAULT_KEYSPACE = "apache_james";
    private static final int DEFAULT_REPLICATION_FACTOR = 1;

    private static final boolean DEFAULT_SSL = false;

    public static Builder.RequireKeyspace builder() {
        return name -> replicationFactor -> durableWrites -> new KeyspaceConfiguration(name, replicationFactor, durableWrites);
    }

    private final String keyspace;
    private final int replicationFactor;
    private final boolean durableWrites;

    public KeyspaceConfiguration(String keyspace, int replicationFactor, boolean durableWrites) {
        Preconditions.checkArgument(replicationFactor > 0, "'' needs to be strictly positive");

        this.keyspace = keyspace;
        this.replicationFactor = replicationFactor;
        this.durableWrites = durableWrites;
    }

    public boolean isDurableWrites() {
        return durableWrites;
    }

    public String getKeyspace() {
        return keyspace;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof KeyspaceConfiguration) {
            KeyspaceConfiguration that = (KeyspaceConfiguration) o;

            return Objects.equals(this.keyspace, that.keyspace)
                && Objects.equals(this.replicationFactor, that.replicationFactor)
                && Objects.equals(this.durableWrites, that.durableWrites);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(keyspace, replicationFactor, durableWrites);
    }
}
