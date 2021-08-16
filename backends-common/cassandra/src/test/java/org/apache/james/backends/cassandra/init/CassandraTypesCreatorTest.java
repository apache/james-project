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
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

class CassandraTypesCreatorTest {
    private static final String TYPE_NAME_1 = "typename1";
    private static final String PROPERTY_1 = "property1";
    private static final String TYPE_NAME_2 = "typename2";
    private static final String PROPERTY_2 = "property2";

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraModule.type(TYPE_NAME_1)
                .statement(statement -> statement.withField(PROPERTY_1, DataTypes.TEXT))
                .build(),
            CassandraModule.type(TYPE_NAME_2)
                .statement(statement -> statement.withField(PROPERTY_2, DataTypes.TEXT))
                .build());

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
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME_1))
                .isNotNull();
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME_2))
                .isNotNull();
    }

    @Test
    void initializeTypesShouldCreateTheAllTypes() {
        cassandra.getConf().execute(SchemaBuilder.dropType(TYPE_NAME_1).build());
        cassandra.getConf().execute(SchemaBuilder.dropType(TYPE_NAME_2).build());

        assertThat(new CassandraTypesCreator(MODULE, cassandra.getConf()).initializeTypes())
                .isEqualByComparingTo(CassandraType.InitializationStatus.FULL);

        CassandraTypesProvider cassandraTypesProviderTest = new CassandraTypesProvider(cassandra.getConf());
        assertThat(cassandraTypesProviderTest.getDefinedUserType(TYPE_NAME_1))
                .isNotNull();
        assertThat(cassandraTypesProviderTest.getDefinedUserType(TYPE_NAME_2))
                .isNotNull();
    }

    @Test
    void initializeTypesShouldCreateTheMissingType() {
        cassandra.getConf().execute(SchemaBuilder.dropType(TYPE_NAME_1).build());

        assertThat(new CassandraTypesCreator(MODULE, cassandra.getConf()).initializeTypes())
                .isEqualByComparingTo(CassandraType.InitializationStatus.PARTIAL);

        CassandraTypesProvider cassandraTypesProviderTest = new CassandraTypesProvider(cassandra.getConf());
        assertThat(cassandraTypesProviderTest.getDefinedUserType(TYPE_NAME_1))
                .isNotNull();
    }

    @Test
    void initializeTypesShouldNotPerformIfCalledASecondTime() {
        assertThat(new CassandraTypesCreator(MODULE, cassandra.getConf()).initializeTypes())
                .isEqualByComparingTo(CassandraType.InitializationStatus.ALREADY_DONE);
    }

    @Test
    void initializeTypesShouldNotFailIfCalledASecondTime() {
        new CassandraTypesCreator(MODULE, cassandra.getConf()).initializeTypes();

        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME_1))
                .isNotNull();
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME_2))
                .isNotNull();
    }
}