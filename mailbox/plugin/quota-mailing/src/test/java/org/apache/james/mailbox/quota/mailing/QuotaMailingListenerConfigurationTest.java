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
import java.util.Optional;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration.RenderingInformation;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaMailingListenerConfigurationTest {

    private static final String SUBJECT_TEMPLATE = "sbj.mustache";
    private static final String BODY_TEMPLATE = "body.mustache";
    private static final String OTHER_SUBJECT_TEMPLATE = "other_sbj.mustache";
    private static final String OTHER_BODY_TEMPLATE = "other_body.mustache";
    private static final String YET_ANOTHER_SUBJECT_TEMPLATE = "yet_another_sbj.mustache";
    private static final String YET_ANOTHER_BODY_TEMPLATE = "yet_another_body.mustache";

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
                "    <threshold>" +
                "      <value>0.85</value>" +
                "      <subjectTemplate>" + SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "      <bodyTemplate>" + BODY_TEMPLATE + "</bodyTemplate>\n" +
                "    </threshold>\n" +
                "    <threshold>\n" +
                "      <value>0.98</value>\n" +
                "      <subjectTemplate>" + OTHER_SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "      <bodyTemplate>" + OTHER_BODY_TEMPLATE + "</bodyTemplate>\n" +
                "    </threshold>\n" +
                "  </thresholds>\n" +
                "  <subjectTemplate>" + YET_ANOTHER_SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "  <bodyTemplate>" + YET_ANOTHER_BODY_TEMPLATE + "</bodyTemplate>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "  <name>listener-name</name>\n" +
                "</configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .addThreshold(new QuotaThreshold(0.85),
                    RenderingInformation.from(BODY_TEMPLATE, SUBJECT_TEMPLATE))
                .addThreshold(new QuotaThreshold(0.98),
                    RenderingInformation.from(OTHER_BODY_TEMPLATE, OTHER_SUBJECT_TEMPLATE))
                .gracePeriod(Duration.ofDays(3))
                .subjectTemplate(YET_ANOTHER_SUBJECT_TEMPLATE)
                .bodyTemplate(YET_ANOTHER_BODY_TEMPLATE)
                .name("listener-name")
                .build());
    }

    @Test
    public void fromShouldReadXMLConfigurationWhenRenderingInformationPartiallyOmited() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds>\n" +
                "    <threshold>" +
                "      <value>0.85</value>" +
                "      <bodyTemplate>" + BODY_TEMPLATE + "</bodyTemplate>\n" +
                "    </threshold>\n" +
                "    <threshold>\n" +
                "      <value>0.98</value>\n" +
                "      <subjectTemplate>" + OTHER_SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "    </threshold>\n" +
                "    <threshold>\n" +
                "      <value>0.99</value>\n" +
                "    </threshold>\n" +
                "  </thresholds>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "  <name>listener-name</name>\n" +
                "</configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .addThreshold(new QuotaThreshold(0.85),
                    RenderingInformation.from(Optional.of(BODY_TEMPLATE), Optional.empty()))
                .addThreshold(new QuotaThreshold(0.98),
                    RenderingInformation.from(Optional.empty(), Optional.of(OTHER_SUBJECT_TEMPLATE)))
                .addThreshold(new QuotaThreshold(0.99),
                    RenderingInformation.from(Optional.empty(), Optional.empty()))
                .gracePeriod(Duration.ofDays(3))
                .name("listener-name")
                .build());
    }

    @Test
    public void fromShouldAcceptEmptyThreshold() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();
        xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds></thresholds>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "</configuration>"));

        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.from(xmlConfiguration);

        assertThat(result)
            .isEqualTo(QuotaMailingListenerConfiguration.builder()
                .gracePeriod(Duration.ofDays(3))
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
    public void defaultConfigurationShouldUseDefaultAsListenerName() throws Exception {
        QuotaMailingListenerConfiguration result = QuotaMailingListenerConfiguration.defaultConfiguration();

        assertThat(result.getName()).isEqualTo("default");
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
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds>\n" +
                "    <threshold>" +
                "      <value>0.85</value>" +
                "      <subjectTemplate></subjectTemplate>\n" +
                "      <bodyTemplate>" + BODY_TEMPLATE + "</bodyTemplate>\n" +
                "    </threshold>\n" +
                "  </thresholds>\n" +
                "  <gracePeriod>3 days</gracePeriod>\n" +
                "  <name>listener-name</name>\n" +
                "</configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void fromShouldThrowOnEmptyBodyTemplate() throws Exception {
        DefaultConfigurationBuilder xmlConfiguration = new DefaultConfigurationBuilder();xmlConfiguration.load(toStream(
            "<configuration>\n" +
                "  <thresholds>\n" +
                "    <threshold>" +
                "      <value>0.85</value>" +
                "      <subjectTemplate>" + SUBJECT_TEMPLATE + "</subjectTemplate>\n" +
                "      <bodyTemplate></bodyTemplate>\n" +
                "    </threshold>\n" +
                "  </thresholds>\n" +
                "  <name>listener-name</name>\n" +
                "</configuration>"));

        assertThatThrownBy(() -> QuotaMailingListenerConfiguration.from(xmlConfiguration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private InputStream toStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

}