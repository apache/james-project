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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.MappingsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraRecipientRewriteTableDAOTest {
    private static final String USER = "test";
    private static final String ADDRESS = "test@domain";
    private static final String ADDRESS_2 = "test2@domain";
    private static final MappingSource SOURCE = MappingSource.fromUser(USER, Domain.LOCALHOST);
    private static final Mapping MAPPING = Mapping.alias(ADDRESS);
    private static final Mapping MAPPING_2 = Mapping.alias(ADDRESS_2);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRRTDataDefinition.MODULE);

    private CassandraRecipientRewriteTableDAO dao;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        dao = new CassandraRecipientRewriteTableDAO(cassandra.getConf());
    }

    @Test
    void retrieveMappingsShouldReturnEmptyByDefault() {
        assertThat(dao.retrieveMappings(SOURCE).blockOptional()).isEmpty();
    }

    @Test
    void getAllMappingsShouldReturnEmptyByDefault() {
        assertThat(dao.getAllMappings().collectList().block()).isEmpty();
    }

    @Test
    void retrieveMappingsShouldReturnStoredMapping() {
        dao.addMapping(SOURCE, MAPPING).block();

        assertThat(dao.retrieveMappings(SOURCE).blockOptional()).contains(MappingsImpl.fromMappings(MAPPING));
    }

    @Test
    void getAllMappingsShouldReturnStoredMapping() {
        dao.addMapping(SOURCE, MAPPING).block();

        assertThat(dao.getAllMappings().collectList().block()).contains(Pair.of(SOURCE, MAPPING));
    }

    @Test
    void retrieveMappingsShouldNotReturnRemovedMapping() {
        dao.addMapping(SOURCE, MAPPING).block();

        dao.removeMapping(SOURCE, MAPPING).block();

        assertThat(dao.retrieveMappings(SOURCE).blockOptional()).isEmpty();
    }

    @Test
    void getAllMappingsShouldNotReturnRemovedMapping() {
        dao.addMapping(SOURCE, MAPPING).block();

        dao.removeMapping(SOURCE, MAPPING).block();

        assertThat(dao.getAllMappings().collectList().block()).isEmpty();
    }

    @Test
    void retrieveMappingsShouldReturnMultipleStoredMappingsOfSource() {
        dao.addMapping(SOURCE, MAPPING).block();
        dao.addMapping(SOURCE, MAPPING_2).block();

        assertThat(dao.retrieveMappings(SOURCE).blockOptional())
            .contains(MappingsImpl.fromMappings(MAPPING, MAPPING_2));
    }

    @Test
    void getAllMappingsShouldReturnMultipleStoredMappings() {
        dao.addMapping(SOURCE, MAPPING).block();
        dao.addMapping(SOURCE, MAPPING_2).block();

        assertThat(dao.getAllMappings().collectList().block())
            .contains(Pair.of(SOURCE, MAPPING), Pair.of(SOURCE, MAPPING_2));
    }
}
