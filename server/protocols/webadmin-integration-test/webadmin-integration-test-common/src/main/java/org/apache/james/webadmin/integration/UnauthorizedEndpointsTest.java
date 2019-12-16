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

package org.apache.james.webadmin.integration;

import static io.restassured.RestAssured.when;

import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.AliasRoutes;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.RestAssured;

public abstract class UnauthorizedEndpointsTest {

    @BeforeEach
    void setup(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    protected void checkUrlProtectionOnGet(String url) {
        when()
            .get(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        DeletedMessagesVaultRoutes.ROOT_PATH + "/joe@perdu.com",
        EventDeadLettersRoutes.BASE_PATH,
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org",
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org/1"
    })
    protected void checkUrlProtectionOnPost(String url) {
        when()
            .post(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        DLPConfigurationRoutes.BASE_PATH + "/james.org",
        DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com",
        DomainQuotaRoutes.BASE_PATH + "/james.org/count",
        DomainQuotaRoutes.BASE_PATH + "/james.org/size",
        DomainQuotaRoutes.BASE_PATH + "/james.org",
        DomainsRoutes.DOMAINS + "/james.org",
        UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size",
        UserRoutes.USERS + "/user@james.org",
        ForwardRoutes.ROOT_PATH + "/alice@james.org/bob@james.org",
        AliasRoutes.ROOT_PATH + "/bob@james.org/sources/bob-alias@james.org",
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count",
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size",
        GlobalQuotaRoutes.QUOTA_ENDPOINT,
        GroupsRoutes.ROOT_PATH + "/group@james.org/user@james.org",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo",
        SieveQuotaRoutes.DEFAULT_QUOTA_PATH,
        SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org"
    })
    void checkUrlProtectionOnPut(String url) {
        when()
            .put(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        DLPConfigurationRoutes.BASE_PATH + "/james.org",
        DomainQuotaRoutes.BASE_PATH + "/james.org/count",
        DomainQuotaRoutes.BASE_PATH + "/james.org/size",
        DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com",
        DomainsRoutes.DOMAINS + "/james.org",
        UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes",
        UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count",
        UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size",
        UserRoutes.USERS + "/user@james.org",
        ForwardRoutes.ROOT_PATH + "/alice@james.org/bob@james.org",
        AliasRoutes.ROOT_PATH + "/bob@james.org/sources/bob-alias@james.org",
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count",
        GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size",
        GroupsRoutes.ROOT_PATH + "/group@james.org/user@james.org",
        MailQueueRoutes.BASE_URL,
        MailQueueRoutes.BASE_URL + "/first_queue/mails",
        MailQueueRoutes.BASE_URL + "/second_queue/mails",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/1",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails",
        SieveQuotaRoutes.DEFAULT_QUOTA_PATH,
        SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org",
        TasksRoutes.BASE + "/taskId",
        EventDeadLettersRoutes.BASE_PATH + "/groups/group@james.org/1"
    })
    void checkUrlProtectionOnDelete(String url) {
        when()
            .delete(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        MailQueueRoutes.BASE_URL + "/first_queue/mails",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails",
        MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/name1"
    })
    void checkUrlProtectionOnPath(String url) {
        when()
            .patch(url)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }
}