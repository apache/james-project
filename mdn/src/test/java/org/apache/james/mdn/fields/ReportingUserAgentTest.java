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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ReportingUserAgentTest {
    public static final String USER_AGENT_NAME = "name";
    public static final String USER_AGENT_PRODUCT = "product";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContact() {
        EqualsVerifier.forClass(ReportingUserAgent.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void productShouldBeOptional() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).build())
            .isEqualTo(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).build());
    }


    @Test
    public void shouldThrowOnNullName() {
        expectedException.expect(NullPointerException.class);

        String userAgentName = null;
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void shouldThrowOnNullNameWhenSpecifyingProduct() {
        expectedException.expect(NullPointerException.class);

        String userAgentName = null;
        ReportingUserAgent.builder().userAgentName(userAgentName).userAgentProduct(USER_AGENT_PRODUCT).build();
    }

    @Test
    public void shouldThrowOnNullProduct() {
        expectedException.expect(NullPointerException.class);

        String userAgentProduct = null;
        ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).userAgentProduct(userAgentProduct).build();
    }

    @Test
    public void shouldThrowOnEmptyName() {
        expectedException.expect(IllegalStateException.class);

        String userAgentName = "";
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void shouldThrowOnFoldingWhiteSpaceName() {
        expectedException.expect(IllegalStateException.class);

        String userAgentName = "   ";
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void shouldThrowOnNameWithLineBreak() {
        expectedException.expect(IllegalStateException.class);

        String userAgentName = "a\nb";
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void shouldThrowOnNameWithLineBreakAtTheEnd() {
        expectedException.expect(IllegalStateException.class);

        String userAgentName = "a\n";
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void shouldThrowOnNameWithLineBreakAtTheBeginning() {
        expectedException.expect(IllegalStateException.class);

        String userAgentName = "\nb";
        ReportingUserAgent.builder().userAgentName(userAgentName).build();
    }

    @Test
    public void nameShouldBeTrimmed() {
        assertThat(ReportingUserAgent.builder().userAgentName(" name ").build().getUserAgentName())
            .isEqualTo(USER_AGENT_NAME);
    }

    @Test
    public void productShouldBeTrimmed() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).userAgentProduct(" product ").build().getUserAgentProduct())
            .contains(USER_AGENT_PRODUCT);
    }

    @Test
    public void formattedValueShouldDisplayNameWhenProductMissing() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).build().formattedValue())
            .isEqualTo("Reporting-UA: name; ");
    }

    @Test
    public void emptyProductShouldBeFilteredOut() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).userAgentProduct("").build().getUserAgentProduct())
            .isEmpty();
    }

    @Test
    public void foldingWhiteSpaceProductShouldBeFilteredOut() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).userAgentProduct("  ").build().getUserAgentProduct())
            .isEmpty();
    }

    @Test
    public void formattedValueShouldDisplayProduct() {
        assertThat(ReportingUserAgent.builder().userAgentName(USER_AGENT_NAME).userAgentProduct(USER_AGENT_PRODUCT).build().formattedValue())
            .isEqualTo("Reporting-UA: name; product");
    }
}
