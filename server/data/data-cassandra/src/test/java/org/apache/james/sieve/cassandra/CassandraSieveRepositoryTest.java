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

package org.apache.james.sieve.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.sieve.cassandra.tables.CassandraSieveTable;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.lib.AbstractSieveRepositoryTest;
import org.joda.time.DateTime;
import org.junit.Test;

public class CassandraSieveRepositoryTest extends AbstractSieveRepositoryTest {
    public static final int DATE_TIMESTAMP = 123456141;
    private CassandraCluster cassandra;

    public CassandraSieveRepositoryTest() {
        cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule());
    }

    @Override
    protected SieveRepository createSieveRepository() throws Exception {
        return new CassandraSieveRepository(new CassandraSieveDAO(cassandra.getConf()));
    }

    @Override
    protected void cleanUp() throws Exception {
        cassandra.clearAllTables();
    }

    @Test
    public void getActivationDateForActiveScriptShouldWork() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraSieveTable.TABLE_NAME)
                .value(CassandraSieveTable.USER_NAME, USER)
                .value(CassandraSieveTable.SCRIPT_NAME, SCRIPT_NAME)
                .value(CassandraSieveTable.SCRIPT_CONTENT, SCRIPT_CONTENT)
                .value(CassandraSieveTable.IS_ACTIVE, true)
                .value(CassandraSieveTable.SIZE, SCRIPT_CONTENT.length())
                .value(CassandraSieveTable.DATE, new Date(DATE_TIMESTAMP))
        );
        assertThat(sieveRepository.getActivationDateForActiveScript(USER)).isEqualTo(new DateTime(DATE_TIMESTAMP));
    }


    @Test(expected = ScriptNotFoundException.class)
    public void getActivationDateForActiveScriptShouldThrowOnMissingActiveScript() throws Exception {
        cassandra.getConf().execute(
            insertInto(CassandraSieveTable.TABLE_NAME)
                .value(CassandraSieveTable.USER_NAME, USER)
                .value(CassandraSieveTable.SCRIPT_NAME, SCRIPT_NAME)
                .value(CassandraSieveTable.SCRIPT_CONTENT, SCRIPT_CONTENT)
                .value(CassandraSieveTable.IS_ACTIVE, false)
                .value(CassandraSieveTable.SIZE, SCRIPT_CONTENT.length())
                .value(CassandraSieveTable.DATE, DATE_TIMESTAMP)
        );
        sieveRepository.getActivationDateForActiveScript(USER);
    }
}