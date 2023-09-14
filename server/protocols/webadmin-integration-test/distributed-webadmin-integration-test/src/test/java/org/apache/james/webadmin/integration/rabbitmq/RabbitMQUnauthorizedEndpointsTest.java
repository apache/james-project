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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.when;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.CleanupTasksPerformer;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.blob.objectstorage.aws.AwsS3BlobStoreExtension;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestRabbitMQModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.webadmin.integration.UnauthorizedEndpointsTest;
import org.apache.james.webadmin.integration.UnauthorizedModule;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.james.webadmin.routes.CassandraMappingsRoutes;
import org.apache.james.webadmin.routes.CassandraMigrationRoutes;
import org.apache.james.webadmin.routes.DLPConfigurationRoutes;
import org.apache.james.webadmin.routes.DomainMappingsRoutes;
import org.apache.james.webadmin.routes.DomainQuotaRoutes;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.apache.james.webadmin.routes.EventDeadLettersRoutes;
import org.apache.james.webadmin.routes.ForwardRoutes;
import org.apache.james.webadmin.routes.GlobalQuotaRoutes;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.james.webadmin.routes.MailQueueRoutes;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.SieveQuotaRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserQuotaRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag(BasicFeature.TAG)
class RabbitMQUnauthorizedEndpointsTest extends UnauthorizedEndpointsTest {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .disableCache()
                .deduplication()
                .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .vaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(new TestRabbitMQModule(DockerRabbitMQSingleton.SINGLETON))
            .overrideWith(new UnauthorizedModule())
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton())))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @ParameterizedTest
    @ValueSource(strings = {
        CassandraMigrationRoutes.VERSION_BASE,
        CassandraMigrationRoutes.VERSION_BASE + "/latest",
        DLPConfigurationRoutes.BASE_PATH + "/james.org",
        DomainMappingsRoutes.DOMAIN_MAPPINGS,
        DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com",
        DomainQuotaRoutes.BASE_PATH + "/james.org",
        DomainQuotaRoutes.BASE_PATH + "/james.org/count",
        DomainQuotaRoutes.BASE_PATH + "/james.org/size",
        DomainsRoutes.DOMAINS,
        UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes",
        UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT,
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size",
        UserRoutes.USERS,
        ForwardRoutes.ROOT_PATH,
        ForwardRoutes.ROOT_PATH + "/alice@james.org",
        AliasRoutes.ROOT_PATH,
        AliasRoutes.ROOT_PATH + "/bob@james.org",
        GlobalQuotaRoutes.QUOTA_ENDPOINT,
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count",
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size",
        GroupsRoutes.ROOT_PATH,
        GroupsRoutes.ROOT_PATH + "/group@james.org",
        MailQueueRoutes.BASE_URL + "/first_queue",
        MailRepositoriesRoutes.MAIL_REPOSITORIES,
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/1",
        SieveQuotaRoutes.DEFAULT_QUOTA_PATH,
        SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org",
        TasksRoutes.BASE,
        TasksRoutes.BASE + "/taskId",
        TasksRoutes.BASE + "/taskId/await",
        EventDeadLettersRoutes.BASE_PATH + "/groups",
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org",
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org/1"
    })
    @Override
    public void checkUrlProtectionOnGet(String url) {
        when()
            .get(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        CassandraMigrationRoutes.VERSION_BASE + "/upgrade",
        CassandraMigrationRoutes.VERSION_BASE + "/upgrade/latest",
        DeletedMessagesVaultRoutes.ROOT_PATH + "/joe@perdu.com",
        CassandraMappingsRoutes.ROOT_PATH,
        EventDeadLettersRoutes.BASE_PATH,
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org",
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org/1"
    })
    @Override
    public void checkUrlProtectionOnPost(String url) {
        when()
            .post(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }
}