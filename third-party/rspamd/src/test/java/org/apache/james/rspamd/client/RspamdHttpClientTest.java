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

package org.apache.james.rspamd.client;

import static org.apache.james.rspamd.DockerRspamd.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import java.io.InputStream;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.junit.categories.Unstable;
import org.apache.james.rspamd.DockerRspamdExtension;
import org.apache.james.rspamd.exception.UnauthorizedException;
import org.apache.james.rspamd.model.AnalysisResult;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;

@Tag(Unstable.TAG)
class RspamdHttpClientTest {
    private final static String SPAM_MESSAGE_PATH = "mail/spam/spam8.eml";
    private final static String HAM_MESSAGE_PATH = "mail/ham/ham1.eml";
    private final static String VIRUS_MESSAGE_PATH = "mail/attachment/inlineVirusTextAttachment.eml";
    private final static String NON_VIRUS_MESSAGE_PATH = "mail/attachment/inlineNonVirusTextAttachment.eml";

    @RegisterExtension
    static DockerRspamdExtension rspamdExtension = new DockerRspamdExtension();

    private Mail spamMessage;
    private Mail hamMessage;
    private Mail virusMessage;
    private Mail nonVirusMessage;

    @BeforeEach
    void setup() throws MessagingException {
        spamMessage = FakeMail.builder()
            .name("spam")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(SPAM_MESSAGE_PATH)))
            .build();
        hamMessage = FakeMail.builder()
            .name("ham")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(HAM_MESSAGE_PATH)))
            .build();
        virusMessage = FakeMail.builder()
            .name("virus")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(VIRUS_MESSAGE_PATH)))
            .build();
        nonVirusMessage = FakeMail.builder()
            .name("non virus")
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(NON_VIRUS_MESSAGE_PATH)))
            .build();
    }

    @Test
    void checkMailWithWrongPasswordShouldThrowUnauthorizedExceptionException() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        assertThatThrownBy(() -> client.checkV2(spamMessage).block())
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void learnSpamWithWrongPasswordShouldThrowUnauthorizedExceptionException() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);


        assertThatThrownBy(() -> reportAsSpam(client, spamMessage.getMessage().getInputStream()))
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void learnHamWithWrongPasswordShouldThrowUnauthorizedExceptionException() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), "wrongPassword", Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        assertThatThrownBy(() -> reportAsHam(client, spamMessage.getMessage().getInputStream()))
            .hasMessage("{\"error\":\"Unauthorized\"}")
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void checkSpamMailUsingRspamdClientWithExactPasswordShouldReturnAnalysisResultAsSameAsUsingRawClient() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(spamMessage).block();
        assertThat(analysisResult.getAction()).isEqualTo(AnalysisResult.Action.REJECT);

        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.dockerRspamd().getPort()));
        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream(SPAM_MESSAGE_PATH))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is(analysisResult.getAction().getDescription()))
            .body("required_score", is(analysisResult.getRequiredScore()))
            .body("subject", is(nullValue()));
    }

    @Test
    void checkHamMailUsingRspamdClientWithExactPasswordShouldReturnAnalysisResultAsSameAsUsingRawClient() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(hamMessage).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(analysisResult.getAction()).isEqualTo(AnalysisResult.Action.NO_ACTION);
            softly.assertThat(analysisResult.getRequiredScore()).isEqualTo(14.0F);
            softly.assertThat(analysisResult.getDesiredRewriteSubject()).isEqualTo(Optional.empty());
            softly.assertThat(analysisResult.hasVirus()).isEqualTo(false);
        });

        RequestSpecification rspamdApi = WebAdminUtils.spec(Port.of(rspamdExtension.dockerRspamd().getPort()));
        rspamdApi
            .header(new Header("Password", PASSWORD))
            .body(ClassLoader.getSystemResourceAsStream(HAM_MESSAGE_PATH))
            .post("checkv2")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("action", is(analysisResult.getAction().getDescription()))
            .body("required_score", is(analysisResult.getRequiredScore()))
            .body("subject", is(nullValue()));
    }

    @Test
    void learnSpamMailUsingRspamdClientWithExactPasswordShouldWork() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        assertThatCode(() -> client.reportAsSpam(spamMessage.getMessage().getInputStream()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void learnHamMailUsingRspamdClientWithExactPasswordShouldWork() {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        assertThatCode(() -> client.reportAsHam(hamMessage.getMessage().getInputStream()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void learnHamMShouldBeIdempotent() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        client.reportAsHam(hamMessage.getMessage().getInputStream()).block();
        assertThatCode(() -> client.reportAsHam(hamMessage.getMessage().getInputStream()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void learnSpamMShouldBeIdempotent() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        client.reportAsSpam(spamMessage.getMessage().getInputStream()).block();
        assertThatCode(() -> client.reportAsSpam(spamMessage.getMessage().getInputStream()).block())
            .doesNotThrowAnyException();
    }

    @Test
    void checkVirusMailUsingRspamdClientWithExactPasswordShouldReturnHasVirus() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(virusMessage).block();
        assertThat(analysisResult.hasVirus()).isTrue();
    }

    @Test
    void checkNonVirusMailUsingRspamdClientWithExactPasswordShouldReturnHasNoVirus() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);

        AnalysisResult analysisResult = client.checkV2(nonVirusMessage).block();
        assertThat(analysisResult.hasVirus()).isFalse();
    }

    private void reportAsSpam(RspamdHttpClient client, InputStream inputStream) {
        client.reportAsSpam(inputStream).block();
    }

    private void reportAsHam(RspamdHttpClient client, InputStream inputStream) {
        client.reportAsHam(inputStream).block();
    }

}
