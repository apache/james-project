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

package org.apache.james.container.spring.mailbox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

public class MaxQuotaConfigurationReader implements Configurable {

    private final MaxQuotaManager maxQuotaManager;
    private final QuotaRootResolver quotaRootResolver;

    public MaxQuotaConfigurationReader(MaxQuotaManager maxQuotaManager, QuotaRootResolver quotaRootResolver) {
        this.maxQuotaManager = maxQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        Long globalMaxMessage = config.configurationAt("maxQuotaManager").getLong("globalMaxMessage", null);
        Long globalMaxStorage = config.configurationAt("maxQuotaManager").getLong("globalMaxStorage", null);
        Map<String, Long> maxMessage = parseMaxMessageConfiguration(config, "maxMessage");
        Map<String, Long> maxStorage = parseMaxMessageConfiguration(config, "maxStorage");
        try {
            configureGlobalValues(globalMaxMessage, globalMaxStorage);
            configureQuotaRootSpecificValues(maxMessage, maxStorage);
        } catch (MailboxException e) {
            throw new ConfigurationException("Exception caught while configuring max quota manager", e);
        }
    }

    private  Map<String, Long> parseMaxMessageConfiguration(HierarchicalConfiguration<ImmutableNode> config, String entry) {
        List<HierarchicalConfiguration<ImmutableNode>> maxMessageConfiguration = config.configurationAt("maxQuotaManager").configurationsAt(entry);
        Map<String, Long> result = new HashMap<>();
        for (HierarchicalConfiguration<ImmutableNode> conf : maxMessageConfiguration) {
            result.put(conf.getString("quotaRoot"), conf.getLong("value"));
        }
        return result;
    }

    private void configureGlobalValues(Long globalMaxMessage, Long globalMaxStorage) throws MailboxException {
        if (globalMaxMessage != null) {
            maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(globalMaxMessage));
        }
        if (globalMaxStorage != null) {
            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(globalMaxStorage));
        }
    }

    private void configureQuotaRootSpecificValues(Map<String, Long> maxMessage, Map<String, Long> maxStorage) throws MailboxException {
        for (Map.Entry<String, Long> entry : maxMessage.entrySet()) {
            maxQuotaManager.setMaxMessage(toQuotaRoot(entry.getKey()), QuotaCountLimit.count(entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : maxStorage.entrySet()) {
            maxQuotaManager.setMaxStorage(toQuotaRoot(entry.getKey()), QuotaSizeLimit.size(entry.getValue()));
        }
    }

    private QuotaRoot toQuotaRoot(String serializedKey) throws MailboxException {
        return quotaRootResolver.fromString(serializedKey);
    }
}
