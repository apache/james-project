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

import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class Dlp extends GenericMatcher {

    public static final String DLP_MATCHED_RULE = "DlpMatchedRule";

    private final DlpRulesLoader rulesLoader;

    @VisibleForTesting
    Dlp(DlpRulesLoader rulesLoader) {
        this.rulesLoader = rulesLoader;
    }

    @Inject
    public Dlp(DLPConfigurationStore configurationStore) {
        this(new DlpRulesLoader.Impl(configurationStore));
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Optional<DLPConfigurationItem.Id> firstMatchingRuleId = findFirstMatchingRule(mail);

        if (firstMatchingRuleId.isPresent()) {
            DLPConfigurationItem.Id ruleId = firstMatchingRuleId.get();
            setRuleIdAsMailAttribute(mail, ruleId);
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private void setRuleIdAsMailAttribute(Mail mail, DLPConfigurationItem.Id ruleId) {
        mail.setAttribute(DLP_MATCHED_RULE, ruleId.asString());
    }

    private Optional<DLPConfigurationItem.Id> findFirstMatchingRule(Mail mail) {
        return Optional
                .ofNullable(mail.getSender())
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
