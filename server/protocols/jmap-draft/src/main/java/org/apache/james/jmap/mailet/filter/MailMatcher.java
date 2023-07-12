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

package org.apache.james.jmap.mailet.filter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.jmap.api.filtering.Rule;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

public interface MailMatcher {

    class HeaderMatcher implements MailMatcher {

        private static final Logger LOGGER = LoggerFactory.getLogger(HeaderMatcher.class);

        private List<MailMatchingCondition> mailMatchingConditions;
        private final Rule.ConditionCombiner conditionCombiner;

        private HeaderMatcher(List<MailMatchingCondition> mailMatchingConditions, Rule.ConditionCombiner conditionCombiner) {
            this.mailMatchingConditions = mailMatchingConditions;
            this.conditionCombiner = conditionCombiner;
        }

        @Override
        public boolean match(Mail mail) {
            try {
                Predicate<MailMatchingCondition> predicate = (MailMatchingCondition mailMatchingCondition) -> {
                    Stream<String> headerLines = mailMatchingCondition.getHeaderExtractor().apply(mail);
                    return mailMatchingCondition.getContentMatcher().match(headerLines, mailMatchingCondition.getRuleValue());
                };

                switch (conditionCombiner) {
                    case AND:
                        return mailMatchingConditions.stream().allMatch(predicate);
                    case OR:
                        return mailMatchingConditions.stream().anyMatch(predicate);
                    default:
                        throw new Exception("this conditionCombiner case is not supported");
                }
            } catch (Exception e) {
                LOGGER.error("error while extracting mail header", e);
                return false;
            }
        }
    }

    class MailMatchingCondition {
        private final ContentMatcher contentMatcher;
        private final String ruleValue;
        private final HeaderExtractor headerExtractor;

        private MailMatchingCondition(ContentMatcher contentMatcher, String ruleValue,
                              HeaderExtractor headerExtractor) {
            Preconditions.checkNotNull(contentMatcher);
            Preconditions.checkNotNull(headerExtractor);

            this.contentMatcher = contentMatcher;
            this.ruleValue = ruleValue;
            this.headerExtractor = headerExtractor;
        }

        public ContentMatcher getContentMatcher() {
            return contentMatcher;
        }

        public String getRuleValue() {
            return ruleValue;
        }

        public HeaderExtractor getHeaderExtractor() {
            return headerExtractor;
        }
    }

    static MailMatcher from(Rule rule) {
        return new HeaderMatcher(rule.getConditionGroup().getConditions().stream()
            .map(ruleCondition -> new MailMatchingCondition(
                ContentMatcher.asContentMatcher(ruleCondition.getField(), ruleCondition.getComparator())
                    .orElseThrow(() -> new RuntimeException("No content matcher associated with field " + ruleCondition.getField())),
                ruleCondition.getValue(),
                HeaderExtractor.asHeaderExtractor(ruleCondition.getField())
                    .orElseThrow(() -> new RuntimeException("No content matcher associated with comparator " + ruleCondition.getComparator())))
            ).collect(Collectors.toList()), rule.getConditionGroup().getConditionCombiner());
    }

    boolean match(Mail mail);
}
