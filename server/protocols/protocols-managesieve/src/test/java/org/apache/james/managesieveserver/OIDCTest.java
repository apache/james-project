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

package org.apache.james.managesieveserver;

import java.nio.charset.StandardCharsets;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.util.ClassLoaderUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class OIDCTest {
    private static final String DISCOVERY_URI_PATH = "/oidc/.well-known/openid-configuration";
    private static final String JWKS_URI_PATH = "/oidc/jwks";
    private static final String INTROSPECTION_URI_PATH = "/oidc/introspect";
    private static final String SCOPE = "scope";
    private static final String USERINFO_URI_PATH = "/oidc/userinfo";
    public static final String VALID_XOAUTH2_INITIAL_CLIENT_RESPONSE = OIDCSASLHelper.generateEncodedXOauth2InitialClientResponse(
        OidcTokenFixture.USER_EMAIL_ADDRESS,
        OidcTokenFixture.VALID_TOKEN
    );
    public static final String VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE = OIDCSASLHelper.generateEncodedXOauth2InitialClientResponse(
        OidcTokenFixture.USER_EMAIL_ADDRESS,
        OidcTokenFixture.VALID_TOKEN
    );
    public static final String INVALID_XOAUTH2_INITIAL_CLIENT_RESPONSE = OIDCSASLHelper.generateEncodedXOauth2InitialClientResponse(
        OidcTokenFixture.USER_EMAIL_ADDRESS,
        OidcTokenFixture.INVALID_TOKEN
    );
    public static final String INVALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE = OIDCSASLHelper.generateEncodedXOauth2InitialClientResponse(
        OidcTokenFixture.USER_EMAIL_ADDRESS,
        OidcTokenFixture.INVALID_TOKEN
    );

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class LocalValidation {
        private ClientAndServer authServer;
        private ManageSieveClient client;
        private final ManageSieveServerTestSystem testSystem;
        private final HierarchicalConfiguration<ImmutableNode> configuration;

        public LocalValidation() throws Exception {
            this.testSystem = new ManageSieveServerTestSystem();
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            this.configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            this.configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            this.configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            this.configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            this.configuration.addProperty("oidc.scope", SCOPE);
        }

        @BeforeEach
        void setUp() throws Exception {
            this.testSystem.setUp(this.configuration);
            this.client = new ManageSieveClient();
            this.client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            this.client.readResponse();
        }

        @AfterEach
        void tearDown() {
            this.testSystem.manageSieveServer.destroy();
        }

        @AfterAll
        void finalTearDown() {
            this.authServer.stop();
        }

        @Test
        void oauthbearerLoginWithValidTokenShouldSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void oauthbearerLoginWithValidTokenAndContinuationShouldSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"OAUTHBEARER\"");
            ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
            Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
            Assertions.assertThat(continuationResponse.responseLines()).containsExactly("\"\"");

            this.client.sendCommand("\"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void oauthbearerLoginWithValidTokenAndContinuationCanBeAborted() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"OAUTHBEARER\"");
            ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
            Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
            Assertions.assertThat(continuationResponse.responseLines()).containsExactly("\"\"");

            this.client.sendCommand("\"*\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
            Assertions.assertThat(authenticationResponse.explanation()).get().isEqualTo("authentication aborted");
        }

        @Test
        void oauthbearerLoginWithInvalidTokenShouldNotSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + INVALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void xoauth2LoginWithValidTokenShouldSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"XOAUTH2\" \"" + VALID_XOAUTH2_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void xoauth2LoginWithValidTokenAndContinuationShouldSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"XOAUTH2\"");
            ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
            Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
            Assertions.assertThat(continuationResponse.responseLines()).containsExactly("\"\"");

            this.client.sendCommand("\"" + VALID_XOAUTH2_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void xoauth2LoginWithValidTokenAndContinuationCanBeAborted() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"XOAUTH2\"");
            ManageSieveClient.ServerResponse continuationResponse = this.client.readResponse();
            Assertions.assertThat(continuationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
            Assertions.assertThat(continuationResponse.responseLines()).containsExactly("\"\"");

            this.client.sendCommand("\"*\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
            Assertions.assertThat(authenticationResponse.explanation()).get().isEqualTo("authentication aborted");
        }

        @Test
        void xoauth2LoginWithInvalidTokenShouldNotSucceed() throws Exception {
            this.client.sendCommand("AUTHENTICATE \"XOAUTH2\" \"" + INVALID_XOAUTH2_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = this.client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }
    }

    @Nested
    public class Introspection {
        private final ManageSieveServerTestSystem testSystem;
        private ClientAndServer authServer;

        public Introspection() throws Exception {
            this.testSystem = new ManageSieveServerTestSystem();
        }

        @AfterEach
        void tearDown() {
            this.testSystem.manageSieveServer.destroy();
            this.authServer.stop();
        }

        @Test
        void oauthbearerShouldSucceedWhenIntrospectReturnsActiveUser() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"active\": true, \"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void oauthbearerShouldFailWhenIntrospectReturnsInactiveUser() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"active\": false, \"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenIntrospectReturnsWrongActiveUser() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"active\": true, \"%s\": \"%s-wrong\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenIntrospectDoesNotContainActiveField() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenIntrospectDoesNotContainUserField() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"active\": true}", StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenIntrospectEndpointErrors() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(500));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerIntrospectionValidationShouldFailWhenLocalValidationFails() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(INTROSPECTION_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"active\": true, \"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(500));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.introspection.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), INTROSPECTION_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }
    }

    @Nested
    public class Userinfo {
        private final ManageSieveServerTestSystem testSystem;
        private ClientAndServer authServer;

        public Userinfo() throws Exception {
            this.testSystem = new ManageSieveServerTestSystem();
        }

        @AfterEach
        void tearDown() {
            this.testSystem.manageSieveServer.destroy();
            this.authServer.stop();
        }

        @Test
        void oauthbearerShouldSucceedWhenUserinfoClaimMatches() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), USERINFO_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.OK);
        }

        @Test
        void oauthbearerShouldFailWhenUserinfoClaimDiffers() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"%s\": \"test\"}", OidcTokenFixture.CLAIM), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), USERINFO_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenUserinfoClaimIsMissing() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{}"), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), USERINFO_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerShouldFailWhenUserinfoErrors() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(500));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), USERINFO_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }

        @Test
        void oauthbearerUserinfoValidationShouldFailWhenLocalValidationFails() throws Exception {
            this.authServer = ClientAndServer.startClientAndServer(0);
            this.authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"%s\": \"%s\"}", OidcTokenFixture.CLAIM, OidcTokenFixture.USER_EMAIL_ADDRESS), StandardCharsets.UTF_8));
            this.authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(500));
            HierarchicalConfiguration<ImmutableNode> configuration = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("managesieveserver.xml"));
            configuration.addProperty("oidc.jwksURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), JWKS_URI_PATH));
            configuration.addProperty("oidc.claim", OidcTokenFixture.CLAIM);
            configuration.addProperty("oidc.oidcConfigurationURL", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), DISCOVERY_URI_PATH));
            configuration.addProperty("oidc.scope", SCOPE);
            configuration.addProperty("oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", this.authServer.getLocalPort(), USERINFO_URI_PATH));
            testSystem.setUp(configuration);

            ManageSieveClient client = new ManageSieveClient();
            client.connect(testSystem.getBindedIP(), testSystem.getBindedPort());
            client.readResponse();

            client.sendCommand("AUTHENTICATE \"OAUTHBEARER\" \"" + VALID_OAUTHBEARER_INITIAL_CLIENT_RESPONSE + "\"");
            ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
            Assertions.assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        }
    }
}
