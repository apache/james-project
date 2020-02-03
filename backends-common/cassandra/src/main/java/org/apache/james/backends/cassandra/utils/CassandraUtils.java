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

package org.apache.james.backends.cassandra.utils;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import reactor.core.publisher.Flux;

public class CassandraUtils {

    public static final CassandraUtils WITH_DEFAULT_CONFIGURATION = new CassandraUtils(CassandraConfiguration.DEFAULT_CONFIGURATION);

    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public CassandraUtils(CassandraConfiguration cassandraConfiguration) {
        this.cassandraConfiguration = cassandraConfiguration;
    }

    public Flux<Row> convertToFlux(ResultSet resultSet) {
        return Flux.fromIterable(resultSet);
    }

    public Stream<Row> convertToStream(ResultSet resultSet) {
        return StreamSupport.stream(resultSet.spliterator(), true)
            .peek(row -> ensureFetchedNextPage(resultSet));
    }

    private void ensureFetchedNextPage(ResultSet resultSet) {
        if (fetchNeeded(resultSet)) {
            resultSet.fetchMoreResults();
        }
    }

    private boolean fetchNeeded(ResultSet resultSet) {
        return resultSet.getAvailableWithoutFetching() == cassandraConfiguration.getFetchNextPageInAdvanceRow()
            && !resultSet.isFullyFetched();
    }

}
