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

import java.util.Arrays;
import java.util.List;

import static com.datastax.driver.core.DataType.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;
import org.apache.james.backends.cassandra.CassandraClusterSingleton;
import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraTypeProviderTest {

    private static final String TYPE_NAME = "typename";
    private static final String PROPERTY = "property";
    
    private CassandraClusterSingleton cassandra;
    private CassandraModule module;

    @Before
    public void setUp() {
        module = new CassandraModule() {
            @Override public List<CassandraTable> moduleTables() {
                return ImmutableList.of();
            }

            @Override public List<CassandraIndex> moduleIndex() {
                return ImmutableList.of();
            }

            @Override public List<CassandraType> moduleTypes() {
                return ImmutableList.copyOf(
                    Arrays.asList(new CassandraType(TYPE_NAME, SchemaBuilder.createType(TYPE_NAME)
                        .ifNotExists()
                        .addColumn(PROPERTY, text()))));
            }
        };
        cassandra = CassandraClusterSingleton.create(module);
        cassandra.getTypesProvider();
        cassandra.ensureAllTables();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void getDefinedUserTypeShouldNotReturnNullNorFailWhenTypeIsDefined() {
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

    @Test
    public void initializeTypesShouldCreateTheTypes() {
        deleteMailboxBaseType();
        new CassandraTypesCreator(Arrays.asList(module), cassandra.getConf()).initializeTypes();
        CassandraTypesProvider cassandraTypesProviderTest = new CassandraTypesProvider(Arrays.asList(module), cassandra.getConf());
        assertThat(cassandraTypesProviderTest.getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

    @Test
    public void initializeTypesShouldNotFailIfCalledTwice() {
        new CassandraTypesProvider(Arrays.asList(module), cassandra.getConf());
        assertThat(cassandra.getTypesProvider().getDefinedUserType(TYPE_NAME))
            .isNotNull();
    }

    private void deleteMailboxBaseType() {
        try {
            cassandra.getConf().execute(SchemaBuilder.dropType(TYPE_NAME));
        } catch (Exception exception) {
            exception.printStackTrace();
            fail("Exception is thrown on Type deletion");
        }
    }



}
