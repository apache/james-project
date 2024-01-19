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

package org.apache.james.webadmin.integration.postgres;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.hamcrest.Matchers.is;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.task.TaskManager;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Module;

import io.restassured.RestAssured;

public class PostgresWebAdminServerBlobGCIntegrationTest {
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2015-10-30T16:12:00Z");

    public static class ClockExtension implements GuiceModuleTestExtension {
        private UpdatableTickingClock clock;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            clock = new UpdatableTickingClock(TIMESTAMP.toInstant());
        }

        @Override
        public Module getModule() {
            return binder -> binder.bind(Clock.class).toInstance(clock);
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return parameterContext.getParameter().getType() == UpdatableTickingClock.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return clock;
        }
    }

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.IN_MEMORY)
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .build())
        .extension(PostgresExtension.empty())
        .extension(new ClockExtension())
        .server(configuration -> PostgresJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username@" + DOMAIN;

    private DataProbe dataProbe;
    private MailboxProbe mailboxProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer, UpdatableTickingClock clock) throws Exception {
        clock.setInstant(TIMESTAMP.toInstant());

        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);

        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(USERNAME, "secret");
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void blobGCShouldRemoveUnreferencedAndInactiveBlobId(UpdatableTickingClock clock) throws MailboxException {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithOnlyAttachment.eml");
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            mailInputStream.newStream(0, -1),
            new Date(),
            false,
            new Flags());

        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete("blobs")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is("BlobGCTask"))
            .body("additionalInformation.referenceSourceCount", is(0))
            .body("additionalInformation.blobCount", is(2))
            .body("additionalInformation.gcedBlobCount", is(2))
            .body("additionalInformation.errorCount", is(0));
    }

    @Test
    void blobGCShouldNotRemoveActiveBlobId() throws MailboxException {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithOnlyAttachment.eml");
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            mailInputStream.newStream(0, -1),
            new Date(),
            false,
            new Flags());

        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete("blobs")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is("BlobGCTask"))
            .body("additionalInformation.referenceSourceCount", is(0))
            .body("additionalInformation.blobCount", is(2))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0));
    }

    @Test
    void blobGCShouldNotRemoveReferencedBlobId(UpdatableTickingClock clock) throws MailboxException {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithOnlyAttachment.eml");
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            mailInputStream.newStream(0, -1),
            new Date(),
            false,
            new Flags());
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete("blobs")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is("BlobGCTask"))
            .body("additionalInformation.referenceSourceCount", is(2))
            .body("additionalInformation.blobCount", is(2))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0));
    }

    @Test
    void blobGCShouldNotRemoveReferencedBlobIdToAnotherMailbox(UpdatableTickingClock clock) throws Exception {
        SharedByteArrayInputStream mailInputStream = ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithOnlyAttachment.eml");
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.inbox(Username.of(USERNAME)),
            mailInputStream.newStream(0, -1),
            new Date(),
            false,
            new Flags());

        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, "CustomBox");
        mailboxProbe.appendMessage(
            USERNAME,
            MailboxPath.forUser(Username.of(USERNAME), "CustomBox"),
            mailInputStream.newStream(0, -1),
            new Date(),
            false,
            new Flags());

        mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, USERNAME, MailboxConstants.INBOX);
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete("blobs")
            .jsonPath()
            .getString("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is(TaskManager.Status.COMPLETED.getValue()))
            .body("taskId", is(taskId))
            .body("type", is("BlobGCTask"))
            .body("additionalInformation.referenceSourceCount", is(2))
            .body("additionalInformation.blobCount", is(2))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0));
    }
}
