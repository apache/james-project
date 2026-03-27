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

package org.apache.james.protocols.webadmin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.time.Duration;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.core.Username;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.imapserver.netty.IMAPServer;
import org.apache.james.imapserver.netty.ImapMetrics;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

import io.restassured.specification.RequestSpecification;

class ProtocolServerRoutesChannelsTest {
    private static final Username ALICE = Username.of("alice@domain.org");
    private static final Username BOB = Username.of("bob@domain.org");
    private static final String PASSWORD = "secret";

    @RegisterExtension
    TestIMAPClient aliceClient = new TestIMAPClient();
    @RegisterExtension
    TestIMAPClient bobClient = new TestIMAPClient();

    private IMAPServer imapServer;
    private WebAdminServer webAdminServer;
    private RequestSpecification spec;
    private int imapPort;

    @BeforeEach
    void setUp() throws Exception {
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(ALICE, PASSWORD);
        authenticator.addUser(BOB, PASSWORD);

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        imapServer = new IMAPServer(
            new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            DefaultImapProcessorFactory.createXListSupportingProcessor(
                resources.getMailboxManager(),
                resources.getEventBus(),
                new StoreSubscriptionManager(
                    resources.getMailboxManager().getMapperFactory(),
                    resources.getMailboxManager().getMapperFactory(),
                    resources.getEventBus()),
                null,
                resources.getQuotaManager(),
                resources.getQuotaRootResolver(),
                metricFactory,
                FetchProcessor.LocalCacheConfiguration.DEFAULT),
            new ImapMetrics(metricFactory),
            new NoopGaugeRegistry(),
            ImmutableSet.of());

        imapServer.setFileSystem(FileSystemImpl.forTestingWithConfigurationFromClasspath());
        imapServer.configure(ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServer.xml")));
        imapServer.init();

        imapPort = imapServer.getListenAddresses().get(0).getPort();

        DisconnectorNotifier disconnectorNotifier = new DisconnectorNotifier.InVMDisconnectorNotifier(imapServer);
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new ProtocolServerRoutes(ImmutableSet.of(), disconnectorNotifier, imapServer))
            .start();
        spec = WebAdminUtils.spec(webAdminServer.getPort());
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
        webAdminServer.destroy();
    }

    @Test
    void getChannelsShouldReturnEmptyWhenNoConnections() {
        given(spec).get("/servers/channels")
            .then().statusCode(200).body("", hasSize(0));
    }

    @Test
    void getChannelsShouldListLoggedInUser() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);

        awaitChannelCount(1);

        given(spec).get("/servers/channels")
            .then().statusCode(200)
            .body("[0].username", equalTo(ALICE.asString()))
            .body("[0].protocol", equalTo("IMAP"));
    }

    @Test
    void getChannelsByUserShouldFilterByUsername() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).get("/servers/channels/" + ALICE.asString())
            .then().statusCode(200)
            .body("", hasSize(1))
            .body("[0].username", equalTo(ALICE.asString()));

        given(spec).get("/servers/channels/" + BOB.asString())
            .then().statusCode(200)
            .body("", hasSize(1))
            .body("[0].username", equalTo(BOB.asString()));
    }

    @Test
    void getConnectedUsersShouldListDistinctLoggedInUsers() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).get("/servers/connectedUsers")
            .then().statusCode(200)
            .body("", hasSize(2))
            .body("", hasItem(ALICE.asString()))
            .body("", hasItem(BOB.asString()));
    }

    @Test
    void deleteChannelsByUserShouldDisconnectUser() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).delete("/servers/channels/" + ALICE.asString())
            .then().statusCode(204);

        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                given(spec).get("/servers/channels")
                    .then().body("username", hasItem(BOB.asString()))
                    .body("username.flatten()", org.hamcrest.Matchers.not(hasItem(ALICE.asString()))));
    }

    @Test
    void deleteAllChannelsShouldDisconnectEveryone() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).delete("/servers/channels")
            .then().statusCode(204);

        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                given(spec).get("/servers/channels")
                    .then().body("", hasSize(0)));
    }

    @Test
    void limitShouldRestrictNumberOfResults() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).get("/servers/channels?limit=1")
            .then().statusCode(200).body("", hasSize(1));
    }

    @Test
    void offsetShouldSkipResults() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        given(spec).get("/servers/channels?offset=1")
            .then().statusCode(200).body("", hasSize(1));
    }

    @Test
    void sortByShouldOrderResultsAlphabetically() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        assertThat(
            given(spec).get("/servers/channels?sortBy=username&sortDirection=asc")
                .then().statusCode(200).extract().jsonPath().<String>getList("username"))
            .isSortedAccordingTo(String::compareTo);
    }

    @Test
    void sortByDescShouldReverseOrder() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        assertThat(
            given(spec).get("/servers/channels?sortBy=username&sortDirection=desc")
                .then().statusCode(200).extract().jsonPath().<String>getList("username"))
            .isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    void sortByProtocolSpecificInformationShouldSortNumerically() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);
        bobClient.connect("127.0.0.1", imapPort).login(BOB, PASSWORD);

        awaitChannelCount(2);

        // cumulativeWrittenBytes is a numeric field in protocolSpecificInformation
        given(spec).get("/servers/channels?sortBy=protocolSpecificInformation.cumulativeWrittenBytes&sortType=numerical&sortDirection=asc")
            .then().statusCode(200).body("", hasSize(2));
    }

    @Test
    void unknownSortByShouldReturnResultsWithoutError() throws Exception {
        aliceClient.connect("127.0.0.1", imapPort).login(ALICE, PASSWORD);

        awaitChannelCount(1);

        given(spec).get("/servers/channels?sortBy=unknownField")
            .then().statusCode(200).body("", hasSize(1));
    }

    private void awaitChannelCount(int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                given(spec).get("/servers/channels")
                    .then().body("", hasSize(expected)));
    }
}
