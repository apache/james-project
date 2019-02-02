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

import static org.apache.james.jmap.api.filtering.Rule.Condition;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.jmap.api.filtering.Rule;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public interface MailMatcher {

    class HeaderMatcher implements MailMatcher {

        private static final Logger LOGGER = LoggerFactory.getLogger(HeaderMatcher.class);

        private final ContentMatcher contentMatcher;
        private final String ruleValue;
        private final HeaderExtractor headerExtractor;

        private HeaderMatcher(ContentMatcher contentMatcher, String ruleValue,
                              HeaderExtractor headerExtractor) {
            Preconditions.checkNotNull(contentMatcher);
            Preconditions.checkNotNull(headerExtractor);

            this.contentMatcher = contentMatcher;
            this.ruleValue = ruleValue;
            this.headerExtractor = headerExtractor;
        }

        @Override
        public boolean match(Mail mail) {
            try {
                Stream<String> headerLines = headerExtractor.apply(mail);
                return contentMatcher.match(headerLines, ruleValue);
            } catch (Exception e) {
                LOGGER.error("error while extracting mail header", e);
                return false;
            }
        }
    }

    static MailMatcher from(Rule rule) {
        Condition ruleCondition = rule.getCondition();
        Optional<ContentMatcher> maybeContentMatcher = ContentMatcher.asContentMatcher(ruleCondition.getField(), ruleCondition.getComparator());
        Optional<HeaderExtractor> maybeHeaderExtractor = HeaderExtractor.asHeaderExtractor(ruleCondition.getField());

        return new HeaderMatcher(
            maybeContentMatcher.orElseThrow(() -> new RuntimeException("No content matcher associated with field " + ruleCondition.getField())),
            rule.getCondition().getValue(),
            maybeHeaderExtractor.orElseThrow(() -> new RuntimeException("No content matcher associated with comparator " + ruleCondition.getComparator())));
    }

    boolean match(Mail mail);
}
