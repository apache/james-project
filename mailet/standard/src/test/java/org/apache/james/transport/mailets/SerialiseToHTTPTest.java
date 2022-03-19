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

package org.apache.james.transport.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.mail.MessagingException;

import org.apache.http.ExceptionLogger;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.apache.http.util.EntityUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SerialiseToHTTPTest {

    private static HttpServer server;
    private static UriHttpRequestHandlerMapper mapper;
    private Mail mail;

    private String urlTestPattern;

    @BeforeAll
    static void setupServer() throws MessagingException, IOException {
        mapper = new UriHttpRequestHandlerMapper();

        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(50000).build();
        server = ServerBootstrap.bootstrap().setListenerPort(0).setSocketConfig(socketConfig)
                .setExceptionLogger(ExceptionLogger.NO_OP).setHandlerMapper(mapper).create();

        server.start();

    }

    @AfterAll
    static void shutdown() {
        server.shutdown(5L, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setup() throws MessagingException, IOException {
        mail = MailUtil.createMockMail2Recipients(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("mime/sendToRemoteHttp.mime")));
    }

    @AfterEach
    void cleanMapper() throws MessagingException, IOException {
        mapper.unregister(urlTestPattern);
    }

    @Test
    void shouldBeFailedWhenServiceResponseIsNotOk() throws Exception {

        urlTestPattern = "/path/to/service/internal/error";

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey")
                .setProperty("url", "http://" + server.getInetAddress().getHostAddress() + ":"
                        + server.getLocalPort() + urlTestPattern)
                .build();

        mapper.register(urlTestPattern, (request, response, context) -> {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        });
        Mailet mailet = new SerialiseToHTTP();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("X-toHTTP")).hasSize(1)
                .allSatisfy((header) -> assertThat(header).isEqualTo("Failed"));
        assertThat(mail.getMessage().getHeader("X-toHTTPFailure")).hasSize(1).allSatisfy(
                (header) -> assertThat(header).isEqualTo("HTTP/1.1 500 Internal Server Error"));
    }

    @Test
    void shouldBeFailedWhenServiceNotExists() throws Exception {

        urlTestPattern = "/path/to/service/failed";

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey")
                .setProperty("url", "ftp://" + server.getInetAddress().getHostAddress() + ":"
                        + server.getLocalPort() + urlTestPattern)
                .build();

        Mailet mailet = new SerialiseToHTTP();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("X-toHTTP")).hasSize(1)
                .allSatisfy((header) -> assertThat(header).isEqualTo("Failed"));
        assertThat(mail.getMessage().getHeader("X-toHTTPFailure")).hasSize(1)
                .allSatisfy((header) -> assertThat(header).isNotBlank());
    }

    @Test
    void shouldBeSucceededWhenServiceResponseIsOk() throws Exception {

        urlTestPattern = "/path/to/service/succeeded";

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey")
                .setProperty("url", "http://" + server.getInetAddress().getHostAddress() + ":"
                        + server.getLocalPort() + urlTestPattern)
                .build();

        mapper.register(urlTestPattern, (request, response, context) -> {
            response.setStatusCode(HttpStatus.SC_OK);
        });

        Mailet mailet = new SerialiseToHTTP();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("X-toHTTP")).hasSize(1)
                .allSatisfy((header) -> assertThat(header).isEqualTo("Succeeded"));

    }

    @Test
    void serviceShouldNotModifyMessageContent() throws Exception {

        urlTestPattern = "/path/to/service/succeeded";

        final String originalMessage = MimeMessageUtil.asString(mail.getMessage());

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey")
                .setProperty("url", "http://" + server.getInetAddress().getHostAddress() + ":"
                        + server.getLocalPort() + urlTestPattern)
                .build();

        mapper.register(urlTestPattern, (request, response, context) -> {
            assertThat(request.getRequestLine().getMethod()).isEqualTo("POST");

            BasicHttpEntityEnclosingRequest basicRequest = (BasicHttpEntityEnclosingRequest) request;
            BasicHttpEntity entity = (BasicHttpEntity) basicRequest.getEntity();

            try {
                List<NameValuePair> params = URLEncodedUtils.parse(entity);
                assertThat(params).hasSize(2).anySatisfy((param) -> {
                    assertThat(param.getName()).isEqualTo("pKey");
                    assertThat(param.getValue()).isEqualTo("pValue");
                }).anySatisfy((param) -> {
                    assertThat(param.getName()).isEqualTo("message");
                    assertThat(param.getValue()).isEqualTo(originalMessage);
                });
            } finally {
                EntityUtils.consume(basicRequest.getEntity());
            }
            response.setStatusCode(HttpStatus.SC_OK);
        });

        Mailet mailet = new SerialiseToHTTP();
        mailet.init(mailetConfig);

        mailet.service(mail);

    }

    @Test
    void shouldSetTheMailStateWhenPassThroughIsFalse() throws Exception {

        urlTestPattern = "/path/to/service/PassThroughIsFalse";

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey")
                .setProperty("url",
                        "http://" + server.getInetAddress().getHostAddress() + ":"
                                + server.getLocalPort() + urlTestPattern)
                .setProperty("passThrough", "false").build();

        mapper.register(urlTestPattern, (request, response, context) -> {
            response.setStatusCode(HttpStatus.SC_OK);
        });

        Mailet mailet = new SerialiseToHTTP();
        mailet.init(mailetConfig);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("X-toHTTP")).isNull();

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    void shouldThrowMessagingExceptionWhenInvalidUrl() throws Exception {

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey").setProperty("url", "qwerty://invalid.url")
                .build();

        assertThatThrownBy(() -> {
            new SerialiseToHTTP().init(mailetConfig);
        }).isExactlyInstanceOf(MessagingException.class)
                .hasMessageContaining("Unable to contruct URL object from url");

    }

    @Test
    void shouldThrowMessagingExceptionWhenUrlIsNull() throws Exception {

        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .setProperty("parameterKey", "pKey").setProperty("parameterValue", "pValue")
                .setProperty("messageKey", "mKey").build();

        assertThatThrownBy(() -> {
            new SerialiseToHTTP().init(mailetConfig);
        }).isExactlyInstanceOf(MessagingException.class)
                .hasMessageContaining("Please configure a targetUrl (\"url\")");

    }

}