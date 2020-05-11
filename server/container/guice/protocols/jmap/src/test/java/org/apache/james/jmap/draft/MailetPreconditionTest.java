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

package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.modules.server.CamelMailetContainerModule;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

class MailetPreconditionTest {

    private static final MailetContext MAILET_CONTEXT = null;
    private static final String BCC = "bcc";

    @Nested
    class VacationMailetCheck {
        @Test
        void vacationMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(Lists.newArrayList()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnWrongMatcher() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), new VacationMailet(null, null, null, null, null)));

            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnWrongMailet() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new Null()));

            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldNotThrowIfValidPairPresent() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new VacationMailet(null, null, null, null, null)));

            assertThatCode(() -> JMAPModule.VACATION_MAILET_CHECK.check(pairs))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class FilteringMailetCheck {
        @Test
        void filteringMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(Lists.newArrayList()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnWrongMatcher() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), new JMAPFiltering(null, null, null)));

            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnWrongMailet() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new Null()));

            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldNotThrowIfValidPairPresent() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new JMAPFiltering(null, null, null)));

            assertThatCode(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class BccCheck {
        @Test
        void bccMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> CamelMailetContainerModule.BCC_Check.check(Lists.newArrayList()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> CamelMailetContainerModule.BCC_Check.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnWrongMatcher() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new RecipientIsLocal(), new RemoveMimeHeader()));

            assertThatThrownBy(() -> CamelMailetContainerModule.BCC_Check.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnWrongMailet() {
            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), new Null()));

            assertThatThrownBy(() -> CamelMailetContainerModule.BCC_Check.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnWrongFieldName() throws Exception {
            RemoveMimeHeader removeMimeHeader = new RemoveMimeHeader();
            removeMimeHeader.init(FakeMailetConfig.builder()
                .mailetName(BCC)
                .mailetContext(MAILET_CONTEXT)
                .setProperty("name", "bad")
                .build());

            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), removeMimeHeader));

            assertThatThrownBy(() -> CamelMailetContainerModule.BCC_Check.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldNotThrowOnValidPair() throws Exception {
            RemoveMimeHeader removeMimeHeader = new RemoveMimeHeader();
            removeMimeHeader.init(FakeMailetConfig.builder()
                .mailetName(BCC)
                .mailetContext(MAILET_CONTEXT)
                .setProperty("name", BCC)
                .build());

            List<MatcherMailetPair> pairs = Lists.newArrayList(new MatcherMailetPair(new All(), removeMimeHeader));
            assertThatCode(() -> CamelMailetContainerModule.BCC_Check.check(pairs))
                .doesNotThrowAnyException();
        }
    }
}
