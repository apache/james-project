/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Objects;
import java.util.function.Function;

import com.datastax.driver.core.ConsistencyLevel;
import com.google.common.base.MoreObjects;

public class CassandraConsistenciesConfiguration {
    public enum ConsistencyChoice {
        WEAK(CassandraConsistenciesConfiguration::getRegular),
        STRONG(CassandraConsistenciesConfiguration::getLightweightTransaction);

        private final Function<CassandraConsistenciesConfiguration, ConsistencyLevel> choice;

        ConsistencyChoice(Function<CassandraConsistenciesConfiguration, ConsistencyLevel> choice) {
            this.choice = choice;
        }

        public ConsistencyLevel choose(CassandraConsistenciesConfiguration configuration) {
            return choice.apply(configuration);
        }
    }

    public static final CassandraConsistenciesConfiguration DEFAULT = new CassandraConsistenciesConfiguration(ConsistencyLevel.QUORUM, ConsistencyLevel.SERIAL);

    public static ConsistencyLevel fromString(String value) {
        switch (value) {
            case "QUORUM":
                return ConsistencyLevel.QUORUM;
            case "LOCAL_QUORUM":
                return ConsistencyLevel.LOCAL_QUORUM;
            case "EACH_QUORUM":
                return ConsistencyLevel.EACH_QUORUM;
            case "SERIAL":
                return ConsistencyLevel.SERIAL;
            case "LOCAL_SERIAL":
                return ConsistencyLevel.LOCAL_SERIAL;
        }
        throw new IllegalArgumentException("'" + value + "' is not a value ConsistencyLevel");
    }

    public static CassandraConsistenciesConfiguration fromConfiguration(CassandraConfiguration configuration) {
        return new CassandraConsistenciesConfiguration(
            fromString(configuration.getConsistencyLevelRegular()),
            fromString(configuration.getConsistencyLevelLightweightTransaction()));
    }

    private final ConsistencyLevel regular;
    private final ConsistencyLevel lightweightTransaction;

    private CassandraConsistenciesConfiguration(ConsistencyLevel regular,
                                                ConsistencyLevel lightweightTransaction) {
        this.regular = regular;
        this.lightweightTransaction = lightweightTransaction;
    }

    public ConsistencyLevel getRegular() {
        return regular;
    }

    public ConsistencyLevel getLightweightTransaction() {
        return lightweightTransaction;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraConsistenciesConfiguration) {
            CassandraConsistenciesConfiguration that = (CassandraConsistenciesConfiguration) o;

            return Objects.equals(this.regular, that.regular)
                && Objects.equals(this.lightweightTransaction, that.lightweightTransaction);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(regular, lightweightTransaction);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("consistencyLevelRegular", regular)
            .add("consistencyLevelLightweightTransaction", lightweightTransaction)
            .toString();
    }
}
