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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.junit.Before;
import org.junit.Test;

public abstract class GenericMaxQuotaManagerTest {

    public static final String DOMAIN = "domain";
    public static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("benwa@domain", Optional.of(DOMAIN));
    private MaxQuotaManager maxQuotaManager;

    protected abstract MaxQuotaManager provideMaxQuotaManager();

    @Before
    public void setUp() {
        maxQuotaManager = provideMaxQuotaManager();
    }

    @Test
    public void getMaxMessageShouldReturnEmptyWhenNoDefaultValue() throws Exception {
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void getMaxStorageShouldReturnEmptyWhenNoDefaultValue() throws Exception {
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void getMaxMessageShouldReturnDomainWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(36));
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(23));
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).contains(QuotaCount.count(23));
    }

    @Test
    public void getMaxMessageShouldReturnDefaultWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(36));
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).contains(QuotaCount.count(36));
    }

    @Test
    public void getMaxStorageShouldReturnDefaultWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(36));
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).contains(QuotaSize.size(36));
    }

    @Test
    public void getMaxStorageShouldReturnDomainWhenNoValue() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(234));
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(111));
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).contains(QuotaSize.size(111));
    }

    @Test
    public void getMaxMessageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(36));
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).contains(QuotaCount.count(36));
    }

    @Test
    public void getMaxStorageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(36));
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).contains(QuotaSize.size(36));
    }

    @Test
    public void deleteMaxStorageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(36));
        maxQuotaManager.removeMaxStorage(QUOTA_ROOT);
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void deleteMaxMessageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(36));
        maxQuotaManager.removeMaxMessage(QUOTA_ROOT);
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void deleteDefaultMaxStorageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(36));
        maxQuotaManager.removeDefaultMaxStorage();
        assertThat(maxQuotaManager.getDefaultMaxStorage()).isEmpty();
    }

    @Test
    public void deleteDefaultMaxMessageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(36));
        maxQuotaManager.removeDefaultMaxMessage();
        assertThat(maxQuotaManager.getDefaultMaxMessage()).isEmpty();
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnEmptyWhenNoQuotaDefined() {
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void listMaxStorageDetailsShouldReturnEmptyWhenNoQuotaDefined() {
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnGlobalValueWhenDefined() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.Global, QuotaCount.count(123));
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnDomainValueWhenDefined() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.Domain, QuotaCount.count(123));
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnUserValueWhenDefined() throws Exception {
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.User, QuotaCount.count(123));
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnBothValuesWhenGlobalAndUserDefined() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(1234));
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(2)
            .containsEntry(Quota.Scope.Global, QuotaCount.count(1234))
            .containsEntry(Quota.Scope.User, QuotaCount.count(123));
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnAllValuesWhenDefined() throws Exception {
        maxQuotaManager.setDefaultMaxMessage(QuotaCount.count(1234));
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(333));
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(3)
            .containsEntry(Quota.Scope.Global, QuotaCount.count(1234))
            .containsEntry(Quota.Scope.Domain, QuotaCount.count(333))
            .containsEntry(Quota.Scope.User, QuotaCount.count(123));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnGlobalValueWhenDefined() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(1111));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.Global, QuotaSize.size(1111));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnDomainValueWhenDefined() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(1111));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.Domain, QuotaSize.size(1111));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnUserValueWhenDefined() throws Exception {
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(2222));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(1)
            .containsEntry(Quota.Scope.User, QuotaSize.size(2222));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnBothValuesWhenDefined() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(3333));
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(4444));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(2)
            .containsEntry(Quota.Scope.Global, QuotaSize.size(3333))
            .containsEntry(Quota.Scope.User, QuotaSize.size(4444));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnAllValuesWhenDefined() throws Exception {
        maxQuotaManager.setDefaultMaxStorage(QuotaSize.size(3333));
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(2222));
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(4444));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(3)
            .containsEntry(Quota.Scope.Global, QuotaSize.size(3333))
            .containsEntry(Quota.Scope.Domain, QuotaSize.size(2222))
            .containsEntry(Quota.Scope.User, QuotaSize.size(4444));
    }

    @Test
    public void getDomainMaxMessageShouldReturnEmptyWhenNoDefaultValue() {
        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN)).isEmpty();
    }

    @Test
    public void getDomainMaxStorageShouldReturnEmptyWhenNoDefaultValue() {
        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN)).isEmpty();
    }

    @Test
    public void getDomainMaxMessageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(36));
        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN)).contains(QuotaCount.count(36));
    }

    @Test
    public void getDomainMaxStorageShouldReturnProvidedValue() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(36));
        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN)).contains(QuotaSize.size(36));
    }

    @Test
    public void deleteDomainMaxStorageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(36));
        maxQuotaManager.removeDomainMaxStorage(DOMAIN);
        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN)).isEmpty();
    }

    @Test
    public void deleteDomainMaxMessageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(36));
        maxQuotaManager.removeDomainMaxMessage(DOMAIN);
        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN)).isEmpty();
    }

}
