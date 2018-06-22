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

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.Before;
import org.junit.Test;

public abstract class GenericMaxQuotaManagerTest {

    public static final Domain DOMAIN = Domain.of("domain");
    public static final Domain DOMAIN_CASE_VARIATION = Domain.of("doMain");
    public static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("benwa@domain", Optional.of(DOMAIN));
    private MaxQuotaManager maxQuotaManager;

    protected abstract MaxQuotaManager provideMaxQuotaManager();

    @Before
    public void setUp() {
        maxQuotaManager = provideMaxQuotaManager();
    }

    @Test
    public void getMaxMessageShouldReturnEmptyWhenNoGlobalValue() throws Exception {
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void getMaxStorageShouldReturnEmptyWhenNoGlobalValue() throws Exception {
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).isEmpty();
    }

    @Test
    public void getMaxMessageShouldReturnDomainWhenNoValue() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(36));
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(23));
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).contains(QuotaCount.count(23));
    }

    @Test
    public void getMaxMessageShouldReturnGlobalWhenNoValue() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(36));
        assertThat(maxQuotaManager.getMaxMessage(QUOTA_ROOT)).contains(QuotaCount.count(36));
    }

    @Test
    public void getMaxStorageShouldReturnGlobalWhenNoValue() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(36));
        assertThat(maxQuotaManager.getMaxStorage(QUOTA_ROOT)).contains(QuotaSize.size(36));
    }

    @Test
    public void getMaxStorageShouldReturnDomainWhenNoValue() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(234));
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
    public void deleteGlobalMaxStorageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(36));
        maxQuotaManager.removeGlobalMaxStorage();
        assertThat(maxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

    @Test
    public void deleteGlobalMaxMessageShouldRemoveCurrentValue() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(36));
        maxQuotaManager.removeGlobalMaxMessage();
        assertThat(maxQuotaManager.getGlobalMaxMessage()).isEmpty();
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
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(123));
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
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(1234));
        maxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCount.count(123));
        assertThat(maxQuotaManager.listMaxMessagesDetails(QUOTA_ROOT))
            .hasSize(2)
            .containsEntry(Quota.Scope.Global, QuotaCount.count(1234))
            .containsEntry(Quota.Scope.User, QuotaCount.count(123));
    }

    @Test
    public void listMaxMessagesDetailsShouldReturnAllValuesWhenDefined() throws Exception {
        maxQuotaManager.setGlobalMaxMessage(QuotaCount.count(1234));
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
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(1111));
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
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(3333));
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(4444));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(2)
            .containsEntry(Quota.Scope.Global, QuotaSize.size(3333))
            .containsEntry(Quota.Scope.User, QuotaSize.size(4444));
    }

    @Test
    public void listMaxStorageDetailsShouldReturnAllValuesWhenDefined() throws Exception {
        maxQuotaManager.setGlobalMaxStorage(QuotaSize.size(3333));
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(2222));
        maxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSize.size(4444));
        assertThat(maxQuotaManager.listMaxStorageDetails(QUOTA_ROOT))
            .hasSize(3)
            .containsEntry(Quota.Scope.Global, QuotaSize.size(3333))
            .containsEntry(Quota.Scope.Domain, QuotaSize.size(2222))
            .containsEntry(Quota.Scope.User, QuotaSize.size(4444));
    }

    @Test
    public void getDomainMaxMessageShouldReturnEmptyWhenNoGlobalValue() {
        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN)).isEmpty();
    }

    @Test
    public void getDomainMaxStorageShouldReturnEmptyWhenNoGlobalValue() {
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

    @Test
    public void deleteDomainMaxMessageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(36));

        maxQuotaManager.removeDomainMaxMessage(DOMAIN_CASE_VARIATION);

        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN)).isEmpty();
    }

    @Test
    public void deleteDomainMaxStorageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(36));

        maxQuotaManager.removeDomainMaxStorage(DOMAIN_CASE_VARIATION);

        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN)).isEmpty();
    }

    @Test
    public void setDomainMaxMessageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN_CASE_VARIATION, QuotaCount.count(36));


        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN))
            .contains(QuotaCount.count(36));
    }

    @Test
    public void setDomainMaxStorageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN_CASE_VARIATION, QuotaSize.size(36));

        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN))
            .contains(QuotaSize.size(36));
    }

    @Test
    public void getDomainMaxMessageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCount.count(36));


        assertThat(maxQuotaManager.getDomainMaxMessage(DOMAIN_CASE_VARIATION))
            .contains(QuotaCount.count(36));
    }

    @Test
    public void getDomainMaxStorageShouldNotBeCaseSensitive() throws Exception {
        maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSize.size(36));

        assertThat(maxQuotaManager.getDomainMaxStorage(DOMAIN_CASE_VARIATION))
            .contains(QuotaSize.size(36));
    }

}
