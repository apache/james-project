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

import java.util.Optional;

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
        assertThat(new ReportingUserAgent(USER_AGENT_NAME))
            .isEqualTo(new ReportingUserAgent(USER_AGENT_NAME, Optional.empty()));
    }

    @Test
    public void productShouldBePresentWhenSpecified() {
        assertThat(new ReportingUserAgent(USER_AGENT_NAME, USER_AGENT_PRODUCT))
            .isEqualTo(new ReportingUserAgent(USER_AGENT_NAME, Optional.of(USER_AGENT_PRODUCT)));
    }

    @Test
    public void shouldThrowOnNullName() {
        expectedException.expect(NullPointerException.class);

        String userAgentName = null;
        new ReportingUserAgent(userAgentName);
    }

    @Test
    public void shouldThrowOnNullNameWhenSpecifyingProduct() {
        expectedException.expect(NullPointerException.class);

        String userAgentName = null;
        new ReportingUserAgent(userAgentName, USER_AGENT_PRODUCT);
    }

    @Test
    public void shouldThrowOnNullProduct() {
        expectedException.expect(NullPointerException.class);

        String userAgentProduct = null;
        new ReportingUserAgent(USER_AGENT_NAME, userAgentProduct);
    }

    @Test
    public void formattedValueShouldDisplayNameWhenProductMissing() {
        assertThat(new ReportingUserAgent(USER_AGENT_NAME).formattedValue())
            .isEqualTo("Reporting-UA: name; ");
    }

    @Test
    public void formattedValueShouldDisplayProduct() {
        assertThat(new ReportingUserAgent(USER_AGENT_NAME, USER_AGENT_PRODUCT).formattedValue())
            .isEqualTo("Reporting-UA: name; product");
    }
}
