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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Properties;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RemoteDeliveryConfigurationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void isDebugShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    public void isDebugShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isTrue();
    }

    @Test
    public void isDebugShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    public void isDebugShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isDebug()).isFalse();
    }

    @Test
    public void getSmtpTimeoutShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_SMTP_TIMEOUT);
    }

    @Test
    public void getSmtpTimeoutShouldReturnProvidedValue() {
        int value = 150000;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(value);
    }

    @Test
    public void getSmtpTimeoutShouldReturnDefaultIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_SMTP_TIMEOUT);
    }

    @Test
    public void getSmtpTimeoutShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(0);
    }

    @Test
    public void getSmtpTimeoutShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.TIMEOUT, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getSmtpTimeout())
            .isEqualTo(-1);
    }

    @Test
    public void getOutGoingQueueNameShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getOutGoingQueueName())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_OUTGOING_QUEUE_NAME);
    }

    @Test
    public void getOutGoingQueueNameShouldReturnProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.OUTGOING, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getOutGoingQueueName().asString())
            .isEqualTo(value);
    }

    @Test
    public void getConnectionTimeoutShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    public void getConnectionTimeoutShouldReturnProvidedValue() {
        int value = 150000;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(value);
    }

    @Test
    public void getConnectionTimeoutShouldReturnDefaultIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_CONNECTION_TIMEOUT);
    }

    @Test
    public void getConnectionTimeoutShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(0);
    }

    @Test
    public void getConnectionTimeoutShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.CONNECTIONTIMEOUT, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getConnectionTimeout())
            .isEqualTo(-1);
    }

    @Test
    public void isSendPartialShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    public void isSendPartialShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isTrue();
    }

    @Test
    public void isSendPartialShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    public void isSendPartialShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SENDPARTIAL, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSendPartial()).isFalse();
    }

    @Test
    public void getBounceProcessorShouldReturnNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBounceProcessor())
            .isNull();
    }

    @Test
    public void getBounceProcessorShouldReturnProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BOUNCE_PROCESSOR, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBounceProcessor())
            .isEqualTo(value);
    }

    @Test
    public void isStartTLSShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    public void isStartTLSShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isTrue();
    }

    @Test
    public void isStartTLSShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    public void isStartTLSShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isStartTLS()).isFalse();
    }

    @Test
    public void isSSLEnableShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    public void isSSLEnableShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isTrue();
    }

    @Test
    public void isSSLEnableShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    public void isSSLEnableShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.SSL_ENABLE, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isSSLEnable()).isFalse();
    }

    @Test
    public void isBindUsedShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BIND, "127.0.0.1:25")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isBindUsed()).isTrue();
    }

    @Test
    public void getBindAddressShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBindAddress()).isNull();
    }

    @Test
    public void getBindAddressShouldReturnProvidedValue() {
        String value = "127.0.0.1:25";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.BIND, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getBindAddress()).isEqualTo(value);
    }

    @Test
    public void getDnsProblemRetryShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_DNS_RETRY_PROBLEM);
    }

    @Test
    public void getDnsProblemRetryShouldReturnProvidedValue() {
        int value = 4;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(value);
    }

    @Test
    public void constructorShouldThrowOnInvalidDnsRetries() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "invalid")
            .build();

        expectedException.expect(NumberFormatException.class);

        new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class));
    }

    @Test
    public void getDnsProblemRetryShouldReturnProvidedValueWhenZero() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "0")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(0);
    }

    @Test
    public void getDnsProblemRetryShouldReturnProvidedValueWhenEmpty() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(0);
    }

    @Test
    public void getDnsProblemRetryShouldReturnProvidedValueWhenNegativeNumber() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "-1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDnsProblemRetry())
            .isEqualTo(-1);
    }

    @Test
    public void isUsePriorityShouldBeFalseByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    public void isUsePriorityShouldBeTrueIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "true")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isTrue();
    }

    @Test
    public void isUsePriorityShouldBeFalseIfSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "false")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    public void isUsePriorityShouldBeFalseIfParsingException() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.USE_PRIORITY, "invalid")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).isUsePriority()).isFalse();
    }

    @Test
    public void getHeloNameProviderShouldCallDomainListByDefault() throws Exception {
        DomainList domainList = mock(DomainList.class);
        String value = "value";
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(value));
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, domainList).getHeloNameProvider().getHeloName())
            .isEqualTo(value);
    }

    @Test
    public void getHeloNameProviderShouldTakeCareOfProvidedValue() {
        String value = "value";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getHeloNameProvider().getHeloName())
            .isEqualTo(value);
    }

    @Test
    public void getJavaxAdditionalPropertiesShouldBeEmptyByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .isEmpty();
    }

    @Test
    public void getJavaxAdditionalPropertiesShouldTakeOneEntryIntoAccount() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        String value1 = "value1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, value1)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .containsOnly(MapEntry.entry(key1, value1));
    }

    @Test
    public void getJavaxAdditionalPropertiesShouldTakeTwoEntriesIntoAccount() {
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
    public void constructorShouldThrowOnNullValueJavaxProperty() {
        expectedException.expect(NullPointerException.class);

        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, null)
            .build();

        new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class));
    }

    @Test
    public void getJavaxAdditionalPropertiesShouldTakeOneEmptyEntryIntoAccount() {
        String key1 = RemoteDeliveryConfiguration.JAVAX_PREFIX + "property1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(key1, "")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getJavaxAdditionalProperties())
            .containsOnly(MapEntry.entry(key1, ""));
    }

    @Test
    public void getGatewayServerShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer()).isEmpty();
    }

    @Test
    public void getGatewayServerShouldReturnProvidedValue() {
        String value = "127.0.0.1";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, value)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(value);
    }

    @Test
    public void getGatewayServerShouldReturnProvidedValues() {
        String value1 = "127.0.0.1";
        String value2 = "domain";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, value1 + ',' + value2)
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getGatewayServer())
            .containsOnly(value1, value2);
    }

    @Test
    public void getGatewayServerShouldReturnGatewayWithGatewayPort() {
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
    public void getGatewayServerShouldOnlyOverridePortsNotInitiallySet() {
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
    public void getAuthUserShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isNull();
    }

    @Test
    public void getAuthUserShouldBeNullWhenGatewayIsNotSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, "name")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isNull();
    }

    @Test
    public void getAuthUserShouldReturnSpecifiedValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    public void getAuthUserShouldReturnSpecifiedEmptyValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    public void getAuthUserShouldReturnSpecifiedCompatibilityValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME_COMPATIBILITY, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    public void getAuthUserShouldReturnSpecifiedEmptyCompatibilityValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_USERNAME_COMPATIBILITY, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthUser()).isEqualTo(value);
    }

    @Test
    public void getAuthUserShouldReturnSpecifiedValueWhenValueAndCompatibilitySpecified() {
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
    public void getAuthPassShouldBeNullByDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isNull();
    }

    @Test
    public void getAuthPassShouldBeNullWhenGatewayIsNotSpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, "name")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isNull();
    }

    @Test
    public void getAuthPassShouldReturnSpecifiedValueWhenGatewaySpecified() {
        String value = "name";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isEqualTo(value);
    }

    @Test
    public void getAuthPassShouldReturnSpecifiedEmptyValueWhenGatewaySpecified() {
        String value = "";
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.GATEWAY_PASSWORD, value)
            .setProperty(RemoteDeliveryConfiguration.GATEWAY, "127.0.0.1")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getAuthPass()).isEqualTo(value);
    }

    @Test
    public void getMaxRetriesShouldReturnProvidedValue() {
        int value = 36;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(value);
    }

    @Test
    public void getMaxRetriesShouldReturnOneWhenZero() {
        int value = 0;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(1);
    }

    @Test
    public void getMaxRetriesShouldReturnOneWhenNegativeNumber() {
        int value = -1;
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_RETRIES, String.valueOf(value))
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries()).isEqualTo(1);
    }

    @Test
    public void getMaxRetriesShouldReturnDefaultWhenNoySpecified() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getMaxRetries())
            .isEqualTo(RemoteDeliveryConfiguration.DEFAULT_MAX_RETRY);
    }

    @Test
    public void getDelayTimesShouldReturnDefault() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDelayTimes())
            .containsOnly(Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME, Delay.DEFAULT_DELAY_TIME);
    }

    @Test
    public void getDelayTimesShouldWorkWithDefaultConfiguration() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.DELAY_TIME, "5000, 100000, 500000")
            .build();

        assertThat(new RemoteDeliveryConfiguration(mailetConfig, mock(DomainList.class)).getDelayTimes())
            .containsOnly(Duration.ofSeconds(5), Duration.ofSeconds(100), Duration.ofSeconds(500));
    }

    @Test
    public void createFinalJavaxPropertiesShouldProvidePropertiesWithMinimalConfiguration() {
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
    public void createFinalJavaxPropertiesShouldProvidePropertiesWithFullConfigurationWithoutGateway() {
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
    public void createFinalJavaxPropertiesShouldProvidePropertiesWithFullConfigurationWithGateway() {
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
