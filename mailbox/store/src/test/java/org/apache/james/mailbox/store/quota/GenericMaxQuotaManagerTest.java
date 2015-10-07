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

package org.apache.james.mailbox.store.quota;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class GenericMaxQuotaManagerTest {

    private QuotaRoot quotaRoot;
    private MaxQuotaManager maxQuotaManager;

    protected abstract MaxQuotaManager provideMaxQuotaManager();

    @Before
    public void setUp() {
        maxQuotaManager = provideMaxQuotaManager();
        quotaRoot = QuotaRootImpl.quotaRoot("benwa");
    }

    @Test
    public void getMaxMessageShouldReturnUnlimitedWhenNoDefaultValue() throws Exception {
        assertThat(maxQuotaManager.getMaxMessage(quotaRoot)).isEqualTo(Quota.UNLIMITED);
    }

    @Test
    public void getMaxStorageShouldReturnUnlimitedWhenNoDefaultValue() throws Exception {
        assertThat(maxQuotaManager.getMaxStorage(quotaRoot)).isEqualTo(Quota.UNLIMITED);
    }

    @Test
    public void getMaxMessageShouldReturnDefaultWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(36);
        assertThat(maxQuotaManager.getMaxMessage(quotaRoot)).isEqualTo(36);
    }

    @Test
    public void getMaxStorageShouldReturnDefaultWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(36);
        assertThat(maxQuotaManager.getMaxStorage(quotaRoot)).isEqualTo(36);
    }

    @Test
    public void getMaxMessageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setMaxMessage(quotaRoot, 36);
        assertThat(maxQuotaManager.getMaxMessage(quotaRoot)).isEqualTo(36);
    }

    @Test
    public void getMaxStorageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setMaxStorage(quotaRoot, 36);
        assertThat(maxQuotaManager.getMaxStorage(quotaRoot)).isEqualTo(36);
    }

}
