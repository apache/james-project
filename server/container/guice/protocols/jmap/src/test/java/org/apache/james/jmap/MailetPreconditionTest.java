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

package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.JMAPModule;
import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.modules.server.MailetContainerModule;
import org.apache.james.transport.mailets.Null;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.VacationMailet;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMultimap;

class MailetPreconditionTest {

    private static final MailetContext MAILET_CONTEXT = null;
    private static final String BCC = "bcc";

    @Nested
    class VacationMailetCheck {
        @Test
        void vacationMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(ImmutableMultimap.of()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnWrongMatcher() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(ImmutableMultimap.of(
                "transport", new MatcherMailetPair(new All(), new VacationMailet(null, null, null, null)))))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldThrowOnWrongMailet() {
            assertThatThrownBy(() -> JMAPModule.VACATION_MAILET_CHECK.check(ImmutableMultimap.of(
                "transport", new MatcherMailetPair(new All(), new Null()))))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void vacationMailetCheckShouldNotThrowIfValidPairPresent() {
            assertThatCode(() -> JMAPModule.VACATION_MAILET_CHECK.check(ImmutableMultimap.of(
                "transport", new MatcherMailetPair(new RecipientIsLocal(), new VacationMailet(null, null, null, null)))))
                .doesNotThrowAnyException();
        }

        @Test
        void vacationMailetCheckShouldSupportLocalDeliveryProcessor() {
            assertThatCode(() -> JMAPModule.VACATION_MAILET_CHECK.check(ImmutableMultimap.of(
                "local-delivery", new MatcherMailetPair(new All(), new VacationMailet(null, null, null, null)))))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class FilteringMailetCheck {
        @Test
        void filteringMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(ImmutableMultimap.of()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnWrongMatcher() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("tansport", new MatcherMailetPair(new All(), new JMAPFiltering(null, null, null)));

            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldThrowOnWrongMailet() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("tansport", new MatcherMailetPair(new All(), new Null()));

            assertThatThrownBy(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void filteringMailetCheckShouldNotThrowIfValidPairPresent() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("transport", new MatcherMailetPair(new RecipientIsLocal(), new JMAPFiltering(null, null, null)));

            assertThatCode(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .doesNotThrowAnyException();
        }

        @Test
        void filteringMailetCheckShouldSupportLocalDeliveryProcessor() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("local-delivery", new MatcherMailetPair(new All(), new JMAPFiltering(null, null, null)));

            assertThatCode(() -> JMAPModule.FILTERING_MAILET_CHECK.check(pairs))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class BccCheck {
        @Test
        void bccMailetCheckShouldThrowOnEmptyList() {
            assertThatThrownBy(() -> MailetContainerModule.BCC_Check.check(ImmutableMultimap.of()))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnNullList() {
            assertThatThrownBy(() -> MailetContainerModule.BCC_Check.check(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnWrongMatcher() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("tansport", new MatcherMailetPair(new RecipientIsLocal(), new RemoveMimeHeader()));

            assertThatThrownBy(() -> MailetContainerModule.BCC_Check.check(pairs))
                .isInstanceOf(ConfigurationException.class);
        }

        @Test
        void bccMailetCheckShouldThrowOnWrongMailet() {
            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("tansport", new MatcherMailetPair(new All(), new Null()));

            assertThatThrownBy(() -> MailetContainerModule.BCC_Check.check(pairs))
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

            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("tansport", new MatcherMailetPair(new All(), removeMimeHeader));

            assertThatThrownBy(() -> MailetContainerModule.BCC_Check.check(pairs))
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

            ImmutableMultimap<String, MatcherMailetPair> pairs = ImmutableMultimap.of("transport", new MatcherMailetPair(new All(), removeMimeHeader));
            assertThatCode(() -> MailetContainerModule.BCC_Check.check(pairs))
                .doesNotThrowAnyException();
        }
    }
}
