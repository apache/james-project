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

package org.apache.james.transport.matchers.dlp;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.util.DurationParser;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

/**
 * Enable evaluation of incoming emails against DLP rules (Data Leak Prevention) attached to the sender domains.
 *
 * Example:
 *
 *  &lt;mailet match="DLP" class="ToRepository"&gt;
 *     &lt;repositoryPath&gt;/var/mail/quarantine&lt;/repositoryPath&gt;
 *  &lt;/mailet&gt;
 *
 *  Rules can be administered via webAdmin, cf: https://james.apache.org/server/manage-webadmin.html#Administrating_DLP_Configuration
 *
 *  Only available on top of Memory and Cassandra storages.
 *
 *  Additionally a cache can be added to reduce queries done to the underlying database.
 *
 *  Example:
 *
 *  &lt;mailet match="DLP=cache:60s" class="ToRepository"&gt;
 *     &lt;repositoryPath&gt;/var/mail/quarantine&lt;/repositoryPath&gt;
 *  &lt;/mailet&gt;
 *
 *  Will query the DLP rules for a given domain only every 60 seconds.
 *
 *  Please note that querying DLP rules on top of Cassandra relies on Event sourcing, involves reading a potentially
 *  large event stream and involves some SERIAL reads (LightWeight transactions) for each processed emails.
 *
 *  Efficiency of the cache can be tracked with the following metrics:
 *
 *   - dlp.cache.hitRate
 *   - dlp.cache.missCount
 *   - dlp.cache.hitCount
 *   - dlp.cache.size
 */
public class Dlp extends GenericMatcher {

    private static final AttributeName DLP_MATCHED_RULE = AttributeName.of("DlpMatchedRule");
    public static final String CACHE_PREFIX = "cache:";

    private final DlpRulesLoader backendRulesLoader;
    private final GaugeRegistry gaugeRegistry;
    private DlpRulesLoader rulesLoader;

    @VisibleForTesting
    Dlp(DlpRulesLoader rulesLoader, GaugeRegistry gaugeRegistry) {
        this.backendRulesLoader = rulesLoader;
        this.gaugeRegistry = gaugeRegistry;
        this.rulesLoader = backendRulesLoader;
    }

    @Inject
    public Dlp(DLPConfigurationStore configurationStore, GaugeRegistry gaugeRegistry) {
        this(new DlpRulesLoader.Impl(configurationStore), gaugeRegistry);
    }

    @Override
    public void init() throws MessagingException {
        if (getCondition() != null && getCondition().startsWith(CACHE_PREFIX)) {
            rulesLoader = new DlpRulesLoader.Caching(backendRulesLoader, gaugeRegistry,
                DurationParser.parse(getCondition().substring(CACHE_PREFIX.length()), ChronoUnit.SECONDS));
        }
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        Optional<DLPConfigurationItem.Id> firstMatchingRuleId = findFirstMatchingRule(mail);

        if (firstMatchingRuleId.isPresent()) {
            DLPConfigurationItem.Id ruleId = firstMatchingRuleId.get();
            setRuleIdAsMailAttribute(mail, ruleId);
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private void setRuleIdAsMailAttribute(Mail mail, DLPConfigurationItem.Id ruleId) {
        mail.setAttribute(new Attribute(DLP_MATCHED_RULE, AttributeValue.of(ruleId.asString())));
    }

    private Optional<DLPConfigurationItem.Id> findFirstMatchingRule(Mail mail) {
        return mail.getMaybeSender()
                .asOptional()
                .flatMap(sender -> matchingRule(sender, mail));
    }

    private Optional<DLPConfigurationItem.Id> matchingRule(MailAddress address, Mail mail) {
        return rulesLoader.load(address.getDomain()).match(mail);
    }

    @Override
    public String getMatcherInfo() {
        return "Data Leak Prevention Matcher";
    }
}
