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

package org.apache.james.mailbox.cassandra.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.cassandra.CassandraClusterSingleton;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.QuotaRootImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraCurrentQuotaManagerTest {

    private static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("value");

    private CassandraClusterSingleton cassandra;
    private CassandraCurrentQuotaManager currentQuotaManager;

    @Before
    public void setUp() {
        cassandra = CassandraClusterSingleton.build();
        cassandra.ensureAllTables();
        currentQuotaManager = new CassandraCurrentQuotaManager(cassandra.getConf());
    }

    @After
    public void cleanUp() {
        cassandra.clearAllTables();
    }

    @Test
    public void getCurrentStorageShouldReturnZeroByDefault() throws Exception {
        assertThat(currentQuotaManager.getCurrentStorage(QUOTA_ROOT)).isEqualTo(0);
    }

    @Test
    public void getCurrentMessageCountShouldReturnZeroByDefault() throws Exception {
        assertThat(currentQuotaManager.getCurrentMessageCount(QUOTA_ROOT)).isEqualTo(0);
    }

    @Test
    public void increaseShouldWork() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, 2, 2000);
        assertThat(currentQuotaManager.getCurrentStorage(QUOTA_ROOT)).isEqualTo(2000);
        assertThat(currentQuotaManager.getCurrentMessageCount(QUOTA_ROOT)).isEqualTo(2);
    }

    @Test
    public void decreaseShouldWork() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, 2, 2000);
        currentQuotaManager.decrease(QUOTA_ROOT, 1, 1000);
        assertThat(currentQuotaManager.getCurrentStorage(QUOTA_ROOT)).isEqualTo(1000);
        assertThat(currentQuotaManager.getCurrentMessageCount(QUOTA_ROOT)).isEqualTo(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void increaseShouldThrowOnZeroCount() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, 0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void increaseShouldThrowOnNegativeCount() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, -1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void increaseShouldThrowOnZeroSize() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, 5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void increaseShouldThrowOnNegativeSize() throws Exception {
        currentQuotaManager.increase(QUOTA_ROOT, 5, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decreaseShouldThrowOnZeroCount() throws Exception {
        currentQuotaManager.decrease(QUOTA_ROOT, 0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decreaseShouldThrowOnNegativeCount() throws Exception {
        currentQuotaManager.decrease(QUOTA_ROOT, -1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decreaseShouldThrowOnZeroSize() throws Exception {
        currentQuotaManager.decrease(QUOTA_ROOT, 5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decreaseShouldThrowOnNegativeSize() throws Exception {
        currentQuotaManager.decrease(QUOTA_ROOT, 5, -1);
    }

}
