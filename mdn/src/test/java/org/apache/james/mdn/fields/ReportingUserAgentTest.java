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

package org.apache.james.mdn.fields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ReportingUserAgentTest {
    static final String USER_AGENT_NAME = "name";
    static final String USER_AGENT_PRODUCT = "product";

    @Test
    void shouldMatchBeanContact() {
        EqualsVerifier.forClass(ReportingUserAgent.class)
            .verify();
    }

    @Test
    void productShouldBeOptional() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .build())
            .isEqualTo(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .build());
    }


    @Test
    void shouldThrowOnNullName() {
        String userAgentName = null;

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullNameWhenSpecifyingProduct() {
        String userAgentName = null;

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .userAgentProduct(USER_AGENT_PRODUCT)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullProduct() {
        String userAgentProduct = null;

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .userAgentProduct(userAgentProduct)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnEmptyName() {
        String userAgentName = "";

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowOnFoldingWhiteSpaceName() {
        String userAgentName = "   ";

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowOnNameWithLineBreak() {
        String userAgentName = "a\nb";

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowOnNameWithLineBreakAtTheEnd() {
        String userAgentName = "a\n";

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowOnNameWithLineBreakAtTheBeginning() {
        String userAgentName = "\nb";

        assertThatThrownBy(() -> ReportingUserAgent.builder()
                .userAgentName(userAgentName)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nameShouldBeTrimmed() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(" name ")
                .build()
                .getUserAgentName())
            .isEqualTo(USER_AGENT_NAME);
    }

    @Test
    void productShouldBeTrimmed() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .userAgentProduct(" product ")
                .build()
                .getUserAgentProduct())
            .contains(USER_AGENT_PRODUCT);
    }

    @Test
    void formattedValueShouldDisplayNameWhenProductMissing() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .build()
                .formattedValue())
            .isEqualTo("Reporting-UA: name");
    }

    @Test
    void emptyProductShouldBeFilteredOut() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .userAgentProduct("")
                .build()
                .getUserAgentProduct())
            .isEmpty();
    }

    @Test
    void foldingWhiteSpaceProductShouldBeFilteredOut() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .userAgentProduct("  ")
                .build()
                .getUserAgentProduct())
            .isEmpty();
    }

    @Test
    void formattedValueShouldDisplayProduct() {
        assertThat(ReportingUserAgent.builder()
                .userAgentName(USER_AGENT_NAME)
                .userAgentProduct(USER_AGENT_PRODUCT)
                .build()
                .formattedValue())
            .isEqualTo("Reporting-UA: name; product");
    }

    @Test
    void fieldValueShouldDontHaveSemiColonWhenAgentProductIsNull() {
        assertThat(ReportingUserAgent.builder()
            .userAgentName(USER_AGENT_NAME)
            .build()
            .fieldValue())
            .isEqualTo("name");
    }

    @Test
    void fieldValueShouldSuccessWithFullProperties() {
        assertThat(ReportingUserAgent.builder()
            .userAgentName(USER_AGENT_NAME)
            .userAgentProduct(USER_AGENT_PRODUCT)
            .build()
            .fieldValue())
            .isEqualTo("name; product");
    }

    @Test
    void fieldValueShouldFailWhenAgentNameIsNull() {
        assertThatThrownBy(() -> ReportingUserAgent.builder()
            .userAgentProduct(USER_AGENT_PRODUCT)
            .build())
            .isInstanceOf(NullPointerException.class);
    }
}
