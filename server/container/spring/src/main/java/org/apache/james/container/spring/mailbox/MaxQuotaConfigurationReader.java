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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.exception.MailboxException;
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
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        Long defaultMaxMessage = config.configurationAt("maxQuotaManager").getLong("defaultMaxMessage", null);
        Long defaultMaxStorage = config.configurationAt("maxQuotaManager").getLong("defaultMaxStorage", null);
        Map<String, Long> maxMessage = parseMaxMessageConfiguration(config, "maxMessage");
        Map<String, Long> maxStorage = parseMaxMessageConfiguration(config, "maxStorage");
        try {
            configureDefaultValues(defaultMaxMessage, defaultMaxStorage);
            configureQuotaRootSpecificValues(maxMessage, maxStorage);
        } catch(MailboxException e) {
            throw new ConfigurationException("Exception caught while configuring max quota manager", e);
        }
    }

    private  Map<String, Long> parseMaxMessageConfiguration(HierarchicalConfiguration config, String entry) {
        List<HierarchicalConfiguration> maxMessageConfiguration = config.configurationAt("maxQuotaManager").configurationsAt(entry);
        Map<String, Long> result = new HashMap<>();
        for (HierarchicalConfiguration conf : maxMessageConfiguration) {
            result.put(conf.getString("quotaRoot"), conf.getLong("value"));
        }
        return result;
    }

    private void configureDefaultValues(Long defaultMaxMessage, Long defaultMaxStorage) throws MailboxException {
        if (defaultMaxMessage != null) {
            maxQuotaManager.setDefaultMaxMessage(defaultMaxMessage);
        }
        if (defaultMaxStorage != null) {
            maxQuotaManager.setDefaultMaxStorage(defaultMaxStorage);
        }
    }

    private void configureQuotaRootSpecificValues(Map<String, Long> maxMessage, Map<String, Long> maxStorage) throws MailboxException {
        for (Map.Entry<String, Long> entry : maxMessage.entrySet()) {
            maxQuotaManager.setMaxMessage(quotaRootResolver.createQuotaRoot(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : maxStorage.entrySet()) {
            maxQuotaManager.setMaxStorage(quotaRootResolver.createQuotaRoot(entry.getKey()), entry.getValue());
        }
    }
}
