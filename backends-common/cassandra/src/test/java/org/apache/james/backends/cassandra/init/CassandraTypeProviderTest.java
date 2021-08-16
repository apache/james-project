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

package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

class CassandraTypeProviderTest {
    private static final String TYPE_NAME = "typename";
    private static final String PROPERTY = "property";

    public static final CassandraModule.Impl MODULE = CassandraModule.type(TYPE_NAME)
        .statement(statement -> statement.withField(PROPERTY, DataTypes.TEXT))
        .build();
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);
    
    private CassandraCluster cassandra;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        this.cassandra = cassandra;
        cassandra.getTypesProvider();
    }

    @Test
    void getDefinedUserTypeShouldNotReturnNullNorFailWhenTypeIsDefined() {
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

    @Test
    void initializeTypesShouldCreateTheTypes() {
        cassandra.getConf().execute(SchemaBuilder.dropType(TYPE_NAME).build());

        assertThat(new CassandraTypesCreator(MODULE, cassandra.getConf()).initializeTypes())
            .isEqualByComparingTo(CassandraType.InitializationStatus.FULL);

        CassandraTypesProvider cassandraTypesProviderTest = new CassandraTypesProvider(cassandra.getConf());
        assertThat(cassandraTypesProviderTest.getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

    @Test
    void initializeTypesShouldNotFailIfCalledTwice() {
        new CassandraTypesProvider(cassandra.getConf());
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

}
