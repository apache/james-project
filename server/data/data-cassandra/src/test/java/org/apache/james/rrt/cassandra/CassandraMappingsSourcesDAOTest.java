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

package org.apache.james.rrt.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMappingsSourcesDAOTest {
    private static final String USER = "test";
    private static final String ADDRESS = "test@domain";
    private static final MappingSource SOURCE = MappingSource.fromUser(USER, Domain.LOCALHOST);
    private static final MappingSource SOURCE_2 = MappingSource.fromUser("bob", Domain.LOCALHOST);
    private static final Mapping MAPPING = Mapping.alias(ADDRESS);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRRTDataDefinition.MODULE);

    private static CassandraMappingsSourcesDAO dao;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraMappingsSourcesDAO(cassandra.getConf());
    }

    @Test
    void retrieveSourcesShouldReturnEmptyByDefault() {
        assertThat(dao.retrieveSources(MAPPING).collectList().block()).isEmpty();
    }

    @Test
    void retrieveSourcesShouldReturnStoredMappingSource() {
        dao.addMapping(MAPPING, SOURCE).block();

        assertThat(dao.retrieveSources(MAPPING).collectList().block()).containsOnly(SOURCE);
    }

    @Test
    void retrieveSourcesShouldNotReturnRemovedMapping() {
        dao.addMapping(MAPPING, SOURCE).block();

        dao.removeMapping(MAPPING, SOURCE).block();

        assertThat(dao.retrieveSources(MAPPING).collectList().block()).isEmpty();
    }

    @Test
    void retrieveSourcesShouldReturnMultipleStoredMappingSourcesForMapping() {
        dao.addMapping(MAPPING, SOURCE).block();
        dao.addMapping(MAPPING, SOURCE_2).block();

        assertThat(dao.retrieveSources(MAPPING).collectList().block()).containsOnly(SOURCE, SOURCE_2);
    }

    @Test
    void retrieveSourcesShouldReturnEmptyAfterTruncateData() {
        dao.addMapping(MAPPING, SOURCE).block();
        dao.addMapping(MAPPING, SOURCE_2).block();

        dao.removeAllData().block();

        assertThat(dao.retrieveSources(MAPPING).collectList().block()).isEmpty();
    }
}
