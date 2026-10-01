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

package org.apache.james.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class PublicKeyReaderTest {

    private static final String PUBLIC_PEM_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh\n" +
            "16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H\n" +
            "lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi\n" +
            "+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+\n" +
            "GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6\n" +
            "U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj\n" +
            "kwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    private static final String X509_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDETCCAfkCFEng6ISWvEq/JkD8u64MA+x2VhS/MA0GCSqGSIb3DQEBCwUAMEUx\n" +
            "CzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRl\n" +
            "cm5ldCBXaWRnaXRzIFB0eSBMdGQwHhcNMjEwODMwMDQwMzIwWhcNMjIwODMwMDQw\n" +
            "MzIwWjBFMQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UE\n" +
            "CgwYSW50ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOC\n" +
            "AQ8AMIIBCgKCAQEAsjjah2w8AKpnGKya4QG/tdRoR9pJkKWfyf17ywcYWBxcpj0Q\n" +
            "+dkn+CXvBafhQ2zlf+bPkWxYhBuXyMB8QNCp/sMlaQ8dGFw/LGojglHk8T4aIu+a\n" +
            "Ffy0hgN9yniuEHmFdjP2XECbA7UbHQPZTO/DU3QJ0FabqKO61pHB4bliNsTWGjzg\n" +
            "seU5kdS1Uup0AK/URO2pSLpnDPV/l0yNmxvGfO/ulPNVJyxiJuT+Rl51LlxpWhu3\n" +
            "G/hFX2mJP0Mn/cX3xNm2HrYIaasglum7bXN/vfiqSFAg46LgT7UJ4pCHoLI99K7Z\n" +
            "OpgBqK/Q4P4UxOggxkawI+JmVLTmCpz1c6JR3QIDAQABMA0GCSqGSIb3DQEBCwUA\n" +
            "A4IBAQAzSrClRytVW1fzL1rXMw7rYVoyoQ6ar3+e/SYiy5p+uSlEda9M/suNSvnV\n" +
            "HAdoZS5Ka6v4AAsWtc6gfwa91jGzxMr5O+mvcx/VCCwahwzQe9KOm17WDhHfObq0\n" +
            "sWDkXSVrrXiZC7gWkB4tczHQJNJKD3aNzYmlKX6GaCKVdBT3eCgIzMkolvIQdW5r\n" +
            "lexckmoeEI+52UzgSyLFYzw+HLphmvszsYNLo6s6LBqgHWLdjVA1KsqSKGrqaNJd\n" +
            "xdjw3S5PHAgOdwxNgip5Vdg1Rq8MjtoVGSC2fn678SyZYOAqxEeKf1CtkYTLySjz\n" +
            "w0ipBFOo1qLHlaT5lHQxOIAmKsK1\n" +
            "-----END CERTIFICATE-----";

    @BeforeAll
    static void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void fromPEMShouldReturnEmptyWhenInvalidPEMKey() {
        assertThat(new PublicKeyReader().fromPEM("blabla")).isEmpty();
    }

    @Test
    void fromPEMShouldReturnRSAPublicKeyWhenValidPEMKey() {
        assertThat(new PublicKeyReader().fromPEM(PUBLIC_PEM_KEY)).isPresent();
    }

    @Test
    void fromPEMShouldReturnRSAPublicKeyWhenValidX509Certificate() {
        // X509_CERTIFICATE is valid between 2021-08-30 and 2022-08-30
        Clock clockDuringValidity = Clock.fixed(Instant.parse("2021-12-01T00:00:00Z"), ZoneOffset.UTC);
        assertThat(new PublicKeyReader(clockDuringValidity).fromPEM(X509_CERTIFICATE)).isPresent();
    }

    @Test
    void fromPEMShouldReturnRSAPublicKeyEvenWhenExpiredX509Certificate() {
        // X509_CERTIFICATE expired on 2022-08-30, key is still extracted with a warning in logs
        Clock clockAfterExpiry = Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertThat(new PublicKeyReader(clockAfterExpiry).fromPEM(X509_CERTIFICATE)).isPresent();
    }

    @Test
    void fromPEMShouldLogWarnWhenExpiredX509Certificate() {
        ListAppender<ILoggingEvent> loggingEvents = getListAppenderForClass(PublicKeyReader.class);
        Clock clockAfterExpiry = Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC);

        new PublicKeyReader(clockAfterExpiry).fromPEM(X509_CERTIFICATE);

        assertThat(loggingEvents.list)
            .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("Certificate is not valid"));
    }

    @Test
    void fromPEMShouldReturnRSAPublicKeyEvenWhenNotYetValidX509Certificate() {
        // X509_CERTIFICATE validity starts on 2021-08-30, key is still extracted with a warning in logs
        Clock clockBeforeValidity = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertThat(new PublicKeyReader(clockBeforeValidity).fromPEM(X509_CERTIFICATE)).isPresent();
    }

    private static ListAppender<ILoggingEvent> getListAppenderForClass(Class<?> clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);
        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();
        logger.addAppender(loggingEventListAppender);
        return loggingEventListAppender;
    }
}
