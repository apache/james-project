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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Properties;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

public class RemoteDeliveryConfigurationTest {

    @Test
    void isDebugShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    void isDebugShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isTrue();
    }

    @Test
    void isDebugShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    void isDebugShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    void getSmtpTimeoutShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_SMTP_TIMEOUT);
    }

    @Test
    void getSmtpTimeoutShouldReturnProvidedValue() {
        int value = 150000;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(value);
    }

    @Test
    void getSmtpTimeoutShouldReturnDefaultIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_SMTP_TIMEOUT);
    }

    @Test
    void getSmtpTimeoutShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(0);
    }

    @Test
    void getSmtpTimeoutShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(-1);
    }

    @Test
    void getOutGoingQueueNameShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getOutGoingQueueName())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_OUTGOING_QUEUE_NAME);
    }

    @Test
    void getOutGoingQueueNameShouldReturnProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.OUTGOING, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getOutGoingQueueName().asString())
            .isEqualTo(value);
    }

    @Test
    void getConnectionTimeoutShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    void getConnectionTimeoutShouldReturnProvidedValue() {
        int value = 150000;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(value);
    }

    @Test
    void getConnectionTimeoutShouldReturnDefaultIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    void getConnectionTimeoutShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(0);
    }

    @Test
    void getConnectionTimeoutShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(-1);
    }

    @Test
    void isSendPartialShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    void isSendPartialShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isTrue();
    }

    @Test
    void isSendPartialShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    void isSendPartialShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    void getBounceProcessorShouldReturnNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBounceProcessor())
            .isNull();
    }

    @Test
    void getBounceProcessorShouldReturnProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBounceProcessor())
            .isEqualTo(value);
    }

    @Test
    void isStartTLSShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    void isStartTLSShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isTrue();
    }

    @Test
    void isStartTLSShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    void isStartTLSShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    void isSSLEnableShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    void isSSLEnableShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isTrue();
    }

    @Test
    void isSSLEnableShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    void isSSLEnableShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    void isBindUsedShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BIND, "127.0.0.1:25")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isBindUsed()).isTrue();
    }

    @Test
    void getBindAddressShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBindAddress()).isNull();
    }

    @Test
    void getBindAddressShouldReturnProvidedValue() {
        String value = "127.0.0.1:25";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BIND, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBindAddress()).isEqualTo(value);
    }

    @Test
    void getDnsProblemRetryShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_DNS_RETRY_PROBLEM);
    }

    @Test
    void getDnsProblemRetryShouldReturnProvidedValue() {
        int value = 4;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(value);
    }

    @Test
    void constructorShouldThrowOnInvalidDnsRetries() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "invalid")
            .build();

        assertThatThrownBy(() -> new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void getDnsProblemRetryShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(0);
    }

    @Test
    void getDnsProblemRetryShouldReturnProvidedValueWhenEmpty() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(0);
    }

    @Test
    void getDnsProblemRetryShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(-1);
    }

    @Test
    void isUsePriorityShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    void isUsePriorityShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isTrue();
    }

    @Test
    void isUsePriorityShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    void isUsePriorityShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    void getHeloNameProviderShouldCallDomainListByDefault() throws Exception {
        DomainList domainList = mock(DomainList.class);
        String value = "value";
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(value));
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, domainList).getHeloNameProvider().getHeloName())
            .isEqualTo(value);
    }

    @Test
    void getHeloNameProviderShouldTakeCareOfProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getHeloNameProvider().getHeloName())
            .isEqualTo(value);
    }

    @Test
    void getJavaxAdditionalPropertiesShouldBeEmptyByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .isEmpty();
    }

    @Test
    void getJavaxAdditionalPropertiesShouldTakeOneEntryIntoAccount() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        String value1 = "value1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, value1)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .containsOnly(MapEntry.entry(key1, value1));
    }

    @Test
    void getJavaxAdditionalPropertiesShouldTakeTwoEntriesIntoAccount() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        String value1 = "value1";
        String key2 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property2";
        String value2 = "value2";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, value1)
            .setProperty(key2, value2)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .containsOnly(MapEntry.entry(key1, value1), MapEntry.entry(key2, value2));
    }

    @Test
    void constructorShouldThrowOnNullValueJavaxProperty() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";

        assertThatThrownBy(() -> FakeMailetConfig.builder()
                .setProperty(key1, null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getJavaxAdditionalPropertiesShouldTakeOneEmptyEntryIntoAccount() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, "")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .containsOnly(MapEntry.entry(key1, ""));
    }

    @Test
    void getGatewayServerShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer()).isEmpty();
    }

    @Test
    void getGatewayServerShouldReturnProvidedValue() {
        String value = "127.0.0.1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(value);
    }

    @Test
    void getGatewayServerShouldReturnProvidedValues() {
        String value1 = "127.0.0.1";
        String value2 = "domain";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, value1 + ',' + value2)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(value1, value2);
    }

    @Test
    void getGatewayServerShouldReturnGatewayWithGatewayPort() {
        String server = "127.0.0.1";
        String port = "2525";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, server)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PORT, port)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(server + ':' + port);
    }

    @Test
    void getGatewayServerShouldOnlyOverridePortsNotInitiallySet() {
        String server1 = "127.0.0.1:23432";
        String server2 = "domain";
        String port = "2525";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, server1 + ',' + server2)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PORT, port)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(server1, server2 + ':' + port);
    }

    @Test
    void getAuthUserShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isNull();
    }

    @Test
    void getAuthUserShouldBeNullWhenGatewayIsNotSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, "name")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isNull();
    }

    @Test
    void getAuthUserShouldReturnSpecifiedValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    void getAuthUserShouldReturnSpecifiedEmptyValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    void getAuthUserShouldReturnSpecifiedCompatibilityValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME_COMPATIBILITY, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    void getAuthUserShouldReturnSpecifiedEmptyCompatibilityValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME_COMPATIBILITY, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    void getAuthUserShouldReturnSpecifiedValueWhenValueAndCompatibilitySpecified() {
        String value = "name";
        String compatibilityValue = "compatibilityValue";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME_COMPATIBILITY, compatibilityValue)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    void getAuthPassShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isNull();
    }

    @Test
    void getAuthPassShouldBeNullWhenGatewayIsNotSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, "name")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isNull();
    }

    @Test
    void getAuthPassShouldReturnSpecifiedValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isEqualTo(value);
    }

    @Test
    void getAuthPassShouldReturnSpecifiedEmptyValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isEqualTo(value);
    }

    @Test
    void getMaxRetriesShouldReturnProvidedValue() {
        int value = 36;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(value);
    }

    @Test
    void getMaxRetriesShouldReturnOneWhenZero() {
        int value = 0;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(1);
    }

    @Test
    void getMaxRetriesShouldReturnOneWhenNegativeNumber() {
        int value = -1;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(1);
    }

    @Test
    void getMaxRetriesShouldReturnDefaultWhenNoySpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_MAX_RETRY);
    }

    @Test
    void getDelayTimesShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDelayTimes())
            .containsOnly(Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME);
    }

    @Test
    void getDelayTimesShouldWorkWithDefaultConfiguration() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELAY_TIME, "5000, 100000, 500000")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDelayTimes())
            .containsOnly(Duration.ofSeconds(5), Duration.ofSeconds(100), Duration.ofSeconds(500));
    }

    @Test
    void createFinalJavaxPropertiesShouldProvidePropertiesWithMinimalConfiguration() {
        String helo = "domain.com";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, helo)
            .build();

        Properties properties = new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).createFinalJavaxProperties();


        assertThat(properties)
            .containsOnly(MapEntry.entry("mail.smtp.ssl.enable", "false"),
                MapEntry.entry("mail.smtp.sendpartial", "false"),
                MapEntry.entry("mail.smtp.ehlo", "true"),
                MapEntry.entry("mail.smtp.connectiontimeout", "60000"),
                MapEntry.entry("mail.smtp.localhost", helo),
                MapEntry.entry("mail.smtp.timeout", "180000"),
                MapEntry.entry("mail.debug", "false"),
                MapEntry.entry("mail.smtp.starttls.enable", "false"));
    }

    @Test
    void createFinalJavaxPropertiesShouldProvidePropertiesWithFullConfigurationWithoutGateway() {
        String helo = "domain.com";
        int connectionTimeout = 1856;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "true")
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "true")
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, String.valueOf(connectionTimeout))
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, helo)
            .build();

        Properties properties = new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).createFinalJavaxProperties();


        assertThat(properties)
            .containsOnly(MapEntry.entry("mail.smtp.ssl.enable", "true"),
                MapEntry.entry("mail.smtp.sendpartial", "true"),
                MapEntry.entry("mail.smtp.ehlo", "true"),
                MapEntry.entry("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout)),
                MapEntry.entry("mail.smtp.localhost", helo),
                MapEntry.entry("mail.smtp.timeout", "180000"),
                MapEntry.entry("mail.debug", "false"),
                MapEntry.entry("mail.smtp.starttls.enable", "true"));
    }

    @Test
    void createFinalJavaxPropertiesShouldProvidePropertiesWithFullConfigurationWithGateway() {
        String helo = "domain.com";
        int connectionTimeout = 1856;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "true")
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "true")
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, String.valueOf(connectionTimeout))
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, helo)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "gateway.domain.com")
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, "user")
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, "password")
            .build();

        Properties properties = new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).createFinalJavaxProperties();


        assertThat(properties)
            .containsOnly(MapEntry.entry("mail.smtp.ssl.enable", "true"),
                MapEntry.entry("mail.smtp.sendpartial", "true"),
                MapEntry.entry("mail.smtp.ehlo", "true"),
                MapEntry.entry("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout)),
                MapEntry.entry("mail.smtp.localhost", helo),
                MapEntry.entry("mail.smtp.timeout", "180000"),
                MapEntry.entry("mail.debug", "false"),
                MapEntry.entry("mail.smtp.starttls.enable", "true"),
                MapEntry.entry("mail.smtp.auth", "true"));
    }
}
