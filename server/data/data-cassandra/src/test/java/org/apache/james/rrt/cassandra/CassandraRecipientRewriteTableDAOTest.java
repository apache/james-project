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
import org.apache.james.backends.cassandra.utils.CassandraUtils;
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
    private static final MappingSource SOURCE = MappingSource.fromUser(USER, Domain.LOCALHOST);
    private static final Mapping MAPPING = Mapping.alias(ADDRESS);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraRRTModule.MODULE);

    private CassandraRecipientRewriteTableDAO cassandraRecipientRewriteTableDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        cassandraRecipientRewriteTableDAO = new CassandraRecipientRewriteTableDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void retrieveMappingsShouldReturnEmptyByDefault() {
        assertThat(cassandraRecipientRewriteTableDAO.retrieveMappings(SOURCE).blockOptional())
            .isEmpty();
    }

    @Test
    void getAllMappingsShouldReturnEmptyByDefault() {
        assertThat(cassandraRecipientRewriteTableDAO.getAllMappings().block())
            .isEmpty();
    }

    @Test
    void retrieveMappingsShouldReturnStoredMapping() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        assertThat(cassandraRecipientRewriteTableDAO.retrieveMappings(SOURCE).blockOptional())
            .contains(MappingsImpl.fromMappings(MAPPING));
    }

    @Test
    void getAllMappingsShouldReturnStoredMapping() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        assertThat(cassandraRecipientRewriteTableDAO.getAllMappings().block())
            .contains(Pair.of(SOURCE, MappingsImpl.fromMappings(MAPPING)));
    }

    @Test
    void retrieveMappingsShouldNotReturnRemovedMapping() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        cassandraRecipientRewriteTableDAO.removeMapping(SOURCE, MAPPING).block();

        assertThat(cassandraRecipientRewriteTableDAO.retrieveMappings(SOURCE).blockOptional())
            .isEmpty();
    }

    @Test
    void getAllMappingsShouldNotReturnRemovedMapping() {
        cassandraRecipientRewriteTableDAO.addMapping(SOURCE, MAPPING).block();

        cassandraRecipientRewriteTableDAO.removeMapping(SOURCE, MAPPING).block();

        assertThat(cassandraRecipientRewriteTableDAO.getAllMappings().block())
            .isEmpty();
    }

}
