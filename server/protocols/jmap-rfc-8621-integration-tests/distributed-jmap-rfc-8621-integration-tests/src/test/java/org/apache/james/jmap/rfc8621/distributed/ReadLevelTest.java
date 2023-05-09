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

package org.apache.james.jmap.rfc8621.distributed;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.with;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

import io.netty.handler.codec.http.HttpHeaderNames;

@Tag(CategoryTags.BASIC_FEATURE)
class ReadLevelTest {
    private static class TestingSessionProbe implements GuiceProbe {
        private final TestingSession testingSession;

        @Inject
        private TestingSessionProbe(TestingSession testingSession) {
            this.testingSession = testingSession;
        }

        public TestingSession getTestingSession() {
            return testingSession;
        }
    }

    private static class TestingSessionModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(TestingSessionProbe.class);

            bind(CqlSession.class).to(TestingSession.class);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .enableJMAP()
            .blobStore(BlobStoreConfiguration.builder()
                .cassandra()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new TestingSessionModule()))
        .build();

    private MessageId messageId;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(Fixture.DOMAIN().asString())
            .addUser(Fixture.BOB().asString(), Fixture.BOB_PASSWORD());

        requestSpecification = baseRequestSpecBuilder(server)
            .setAuth(authScheme(new UserCredential(Fixture.BOB(), Fixture.BOB_PASSWORD())))
            .build();
        server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(Fixture.BOB()));
        messageId = server.getProbe(MailboxProbeImpl.class)
            .appendMessage(Fixture.BOB().asString(), MailboxPath.inbox(Fixture.BOB()), AppendCommand.from(createMessage()))
            .getMessageId();

        Thread.sleep(1000); // Await for the preview to be computed
    }

    @Test
    void gettingEmailMetadataShouldNotReadBlobs(GuiceJamesServer server) {
        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
                StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(0);
    }

    @Test
    void gettingEmailHeadersShouldReadBlobOnce(GuiceJamesServer server) {
        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\",  \"messageId\", \"inReplyTo\", " +
            "                       \"references\", \"to\", \"cc\", \"bcc\", \"from\", \"sender\", " +
            "                       \"replyTo\", \"subject\", \"headers\", \"header:anything\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(1);
    }

    @Test
    void gettingEmailFastViewShouldReadBlobOnce(GuiceJamesServer server) {
        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\",  \"messageId\", \"inReplyTo\", " +
            "                       \"references\", \"to\", \"cc\", \"bcc\", \"from\", \"sender\", " +
            "                       \"replyTo\", \"subject\", \"headers\", \"header:anything\", " +
            "                       \"preview\", \"hasAttachment\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(1);
    }

    @Test
    void gettingEmailFastViewShouldReadBlobTwiceUponCacheMisses(GuiceJamesServer server) {
        server.getProbe(JmapGuiceProbe.class).clearMessageFastViewProjection();

        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\",  \"messageId\", \"inReplyTo\", " +
            "                       \"references\", \"to\", \"cc\", \"bcc\", \"from\", \"sender\", " +
            "                       \"replyTo\", \"subject\", \"headers\", \"header:anything\", " +
            "                       \"preview\", \"hasAttachment\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(2);
    }

    @Test
    void previewMissesShouldPopulateTheProjection(GuiceJamesServer server) {
        server.getProbe(JmapGuiceProbe.class).clearMessageFastViewProjection();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\",  \"messageId\", \"inReplyTo\", " +
            "                       \"references\", \"to\", \"cc\", \"bcc\", \"from\", \"sender\", " +
            "                       \"replyTo\", \"subject\", \"headers\", \"header:anything\", " +
            "                       \"preview\", \"hasAttachment\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        given()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post()
            .then()
            .statusCode(SC_OK);

        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(1);
    }

    @Test
    void gettingEmailBodyShouldReadBlobTwice(GuiceJamesServer server) {
        StatementRecorder statementRecorder = server.getProbe(TestingSessionProbe.class)
            .getTestingSession()
            .recordStatements();

        String request = "{" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"urn:ietf:params:jmap:mail\"]," +
            "  \"methodCalls\": [[" +
            "    \"Email/get\"," +
            "    {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"ids\": [\"" + messageId.serialize() + "\"]," +
            "      \"properties\": [\"id\", \"size\", \"mailboxIds\", \"mailboxIds\", \"blobId\", " +
            "                       \"threadId\", \"receivedAt\",  \"messageId\", \"inReplyTo\", " +
            "                       \"references\", \"to\", \"cc\", \"bcc\", \"from\", \"sender\", " +
            "                       \"replyTo\", \"subject\", \"headers\", \"header:anything\", " +
            "                       \"preview\", \"hasAttachment\", \"bodyStructure\", \"textBody\", \"htmlBody\",\n" +
            "                       \"attachments\", \"bodyValues\"]" +
            "    }," +
            "    \"c1\"]]" +
            "} ";
        with()
            .header(HttpHeaderNames.ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .post();

        assertThat(statementRecorder.listExecutedStatements(
            StatementRecorder.Selector.preparedStatementStartingWith("SELECT * FROM blobs")))
            .hasSize(2);
    }

    private Message createMessage() throws Exception {
        return Message.Builder
            .of()
            .setSubject("test")
            .setSender(Fixture.ANDRE().asString())
            .setFrom(Fixture.ANDRE().asString())
            .setSubject("World domination \r\n" +
                " and this is also part of the header")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build();
    }
}
