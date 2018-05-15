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

package org.apache.james.mailbox.quota.mailing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaMailingListenerConfigurationTest {

    private static final String SUBJECT_TEMPLATE = "sbj.mustache";
    private static final String BODY_TEMPLATE = "body.mustache";

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaMailingListenerConfiguration.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void fromShouldReadXMLConfiguration() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds>\n" +
                "    <threshold>0.85</threshold>\n" +
                "    <threshold>0.98</threshold>\n" +
                "  </thresholds>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "  <subjectTemplate>" + SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "  <bodyTemplate>" + BODY_TEMPLATE + "</bodyTemplate>\n" +
                "</configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .addThresholds(new QuotaThreshold(0.85),
                    new QuotaThreshold(0.98))
                .gracePeriod(Duration.ofDays(3))
                .subjectTemplate(SUBJECT_TEMPLATE)
                .bodyTemplate(BODY_TEMPLATE)
                .build());
    }

    @Test
    public void fromShouldAcceptEmptyThreshold() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds></thresholds>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "  <subjectTemplate>" + SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "  <bodyTemplate>" + BODY_TEMPLATE + "</bodyTemplate>\n" +
                "</configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .gracePeriod(Duration.ofDays(3))
                .subjectTemplate(SUBJECT_TEMPLATE)
                .bodyTemplate(BODY_TEMPLATE)
                .build());
    }

    @Test
    public void fromShouldReturnDefaultWhenEmptyConfiguration() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration></configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .build());
    }

    @Test
    public void fromShouldThrowOnNonParsableGracePeriod() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><gracePeriod>nonParsable</gracePeriod></configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void fromShouldThrowOnNegativeGracePeriod() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><gracePeriod>-12 ms</gracePeriod></configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    public void fromShouldLoadGracePeriodInMs() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><gracePeriod>12 ms</gracePeriod></configuration>"));

        assertThat(QuotaMailingListenerConfiguration.from(xmlConfiguration).getGracePeriod())
            .isEqualTo(Duration.ofMillis(12));
    }

    @Test
    public void defaultUnitShouldBeMilliseconds() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><gracePeriod>12</gracePeriod></configuration>"));

        assertThat(QuotaMailingListenerConfiguration.from(xmlConfiguration).getGracePeriod())
            .isEqualTo(Duration.ofDays(12));
    }

    @Test
    public void fromShouldThrowOnEmptySubjectTemplate() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><subjectTemplate></subjectTemplate></configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnEmptyBodyTemplate() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration><bodyTemplate></bodyTemplate></configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private InputStream toStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

}