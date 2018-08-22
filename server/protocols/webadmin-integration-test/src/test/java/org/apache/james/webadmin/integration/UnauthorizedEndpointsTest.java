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
import static io.restassured.RestAssured.with;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.CassandraMigrationRoutes;
import org.apache.james.webadmin.routes.DLPConfigurationRoutes;
import org.apache.james.webadmin.routes.DomainMappingsRoutes;
import org.apache.james.webadmin.routes.DomainQuotaRoutes;
import org.apache.james.webadmin.routes.DomainsRoutes;
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
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import io.restassured.RestAssured;

public class UnauthorizedEndpointsTest {

    @ClassRule
    public static DockerCassandraRule cassandra = new DockerCassandraRule();
    
    @Rule
    public CassandraJmapTestRule cassandraJmapTestRule = CassandraJmapTestRule.defaultTestRule();

    private GuiceJamesServer guiceJamesServer;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = cassandraJmapTestRule.jmapServer(cassandra.getModule(), new UnauthorizedModule())
                .overrideWith(new WebAdminConfigurationModule());
        guiceJamesServer.start();
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void getCassandraMigrationShouldBeAuthenticated() {
        when()
            .get(CassandraMigrationRoutes.VERSION_BASE)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void upgradeCassandraMigrationShouldBeAuthenticated() {
        when()
            .post(CassandraMigrationRoutes.VERSION_BASE + "/upgrade")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void upgradeLatestCassandraMigrationShouldBeAuthenticated() {
        when()
            .post(CassandraMigrationRoutes.VERSION_BASE + "/upgrade/latest")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getLatestCassandraMigrationShouldBeAuthenticated() {
        when()
            .get(CassandraMigrationRoutes.VERSION_BASE + "/latest")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void storeDLPShouldBeAuthenticated() {
        String storeBody =
                "{\"rules\": [" +
                        "  {" +
                        "    \"id\": \"1\"," +
                        "    \"expression\": \"expression 1\"," +
                        "    \"explanation\": \"explanation 1\"," +
                        "    \"targetsSender\": true," +
                        "    \"targetsRecipients\": true," +
                        "    \"targetsContent\": true" +
                        "  }," +
                        "  {" +
                        "    \"id\": \"2\"," +
                        "    \"expression\": \"expression 2\"," +
                        "    \"explanation\": \"explanation 2\"," +
                        "    \"targetsSender\": false," +
                        "    \"targetsRecipients\": false," +
                        "    \"targetsContent\": false" +
                        "  }]}";

        with()
            .body(storeBody)
        .when()
            .put(DLPConfigurationRoutes.BASE_PATH + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void clearDLPShouldBeAuthenticated() {
        when()
            .delete(DLPConfigurationRoutes.BASE_PATH + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void listDLPShouldBeAuthenticated() {
        when()
            .get(DLPConfigurationRoutes.BASE_PATH + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteDomainMappingShouldBeAuthenticated() {
        with()
            .body("to.com")
        .when()
            .delete(DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void addDomainMappingShouldBeAuthenticated() {
        with()
            .body("to.com")
        .when()
            .put(DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getDomainMappingsShouldBeAuthenticated() {
        with()
            .get(DomainMappingsRoutes.DOMAIN_MAPPINGS)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getDomainMappingShouldBeAuthenticated() {
        with()
            .get(DomainMappingsRoutes.DOMAIN_MAPPINGS + "/from.com")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getDomainQuotaShouldBeAuthenticated() {
        with()
            .get(DomainQuotaRoutes.BASE_PATH + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getCountDomainQuotaShouldBeAuthenticated() {
        with()
            .get(DomainQuotaRoutes.BASE_PATH + "/james.org/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getSizeDomainQuotaShouldBeAuthenticated() {
        with()
            .get(DomainQuotaRoutes.BASE_PATH + "/james.org/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteCountDomainQuotaShouldBeAuthenticated() {
        with()
            .delete(DomainQuotaRoutes.BASE_PATH + "/james.org/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteSizeDomainQuotaShouldBeAuthenticated() {
        with()
            .delete(DomainQuotaRoutes.BASE_PATH + "/james.org/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putCountDomainQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(DomainQuotaRoutes.BASE_PATH + "/james.org/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putSizeDomainQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(DomainQuotaRoutes.BASE_PATH + "/james.org/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putDomainQuotaShouldBeAuthenticated() {
        with()
            .body("{\"count\":52,\"size\":42}")
        .when()
            .put(DomainQuotaRoutes.BASE_PATH + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getDomainsShouldBeAuthenticated() {
        when()
            .get(DomainsRoutes.DOMAINS)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void createDomainShouldBeAuthenticated() {
        when()
            .put(DomainsRoutes.DOMAINS + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteDomainShouldBeAuthenticated() {
        when()
            .delete(DomainsRoutes.DOMAINS + "/james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getUserMailboxesShouldBeAuthenticated() {
        when()
            .get(UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getMailboxUserMailboxesShouldBeAuthenticated() {
        when()
            .get(UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteUserMailboxesShouldBeAuthenticated() {
        when()
            .delete(UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteMailboxUserMailboxesShouldBeAuthenticated() {
        when()
            .delete(UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void createMailboxUserMailboxesShouldBeAuthenticated() {
        when()
            .put(UserMailboxesRoutes.USERS_BASE + "/someuser/mailboxes/mymailbox")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getUserQuotaShouldBeAuthenticated() {
        when()
            .get(UserQuotaRoutes.USERS_QUOTA_ENDPOINT)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getUserUserQuotaShouldBeAuthenticated() {
        when()
            .get(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getCountUserQuotaShouldBeAuthenticated() {
        when()
            .get(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getSizeUserQuotaShouldBeAuthenticated() {
        when()
            .get(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteCountUserQuotaShouldBeAuthenticated() {
        when()
            .delete(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteSizeUserQuotaShouldBeAuthenticated() {
        when()
            .delete(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putUserQuotaShouldBeAuthenticated() {
        with()
            .body("{\"count\":52,\"size\":42}")
        .when()
            .put(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putCountUserQuotaShouldBeAuthenticated() {
        with()
            .body("35")
        .when()
            .put(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putSizeUserQuotaShouldBeAuthenticated() {
        with()
            .body("35")
        .when()
            .put(UserQuotaRoutes.USERS_QUOTA_ENDPOINT + "/joe@perdu.com/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getUsersShouldBeAuthenticated() {
        when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void createUserShouldBeAuthenticated() {
        with()
            .body("{\"password\":\"password\"}")
        .when()
            .put(UserRoutes.USERS + "/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteUserShouldBeAuthenticated() {
        when()
            .delete(UserRoutes.USERS + "/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getForwardsShouldBeAuthenticated() {
        when()
            .get(ForwardRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getForwardShouldBeAuthenticated() {
        when()
            .get(ForwardRoutes.ROOT_PATH + "/alice@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putForwardDestinationShouldBeAuthenticated() {
        when()
            .put(ForwardRoutes.ROOT_PATH + "/alice@james.org/bob@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteForwardDestinationShouldBeAuthenticated() {
        when()
            .delete(ForwardRoutes.ROOT_PATH + "/alice@james.org/bob@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getGlobalQuotaShouldBeAuthenticated() {
        when()
            .get(GlobalQuotaRoutes.QUOTA_ENDPOINT)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getCountGlobalQuotaShouldBeAuthenticated() {
        when()
            .get(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getSizeGlobalQuotaShouldBeAuthenticated() {
        when()
            .get(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteCountGlobalQuotaShouldBeAuthenticated() {
        when()
            .delete(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteSizeGlobalQuotaShouldBeAuthenticated() {
        when()
            .delete(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putCountGlobalQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/count")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putSizeGlobalQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(GlobalQuotaRoutes.QUOTA_ENDPOINT + "/size")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putGlobalQuotaShouldBeAuthenticated() {
        with()
            .body("{\"count\":52,\"size\":42}")
        .when()
            .put(GlobalQuotaRoutes.QUOTA_ENDPOINT)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getGroupsShouldBeAuthenticated() {
        when()
            .get(GroupsRoutes.ROOT_PATH)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getGroupShouldBeAuthenticated() {
        when()
            .get(GroupsRoutes.ROOT_PATH + "/group@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putGroupMemberShouldBeAuthenticated() {
        when()
            .put(GroupsRoutes.ROOT_PATH + "/group@james.org/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteGroupMemberShouldBeAuthenticated() {
        when()
            .delete(GroupsRoutes.ROOT_PATH + "/group@james.org/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void listMailQueuesShouldBeAuthenticated() {
        when()
            .get(MailQueueRoutes.BASE_URL)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getMailQueueShouldBeAuthenticated() {
        when()
            .get(MailQueueRoutes.BASE_URL + "/first_queue")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteMailShouldBeAuthenticated() {
        with()
            .param("sender", "123")
        .when()
            .delete(MailQueueRoutes.BASE_URL + "/first_queue/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void clearMailQueueShouldBeAuthenticated() {
        when()
            .delete(MailQueueRoutes.BASE_URL + "/second_queue/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void flushMailQueueShouldBeAuthenticated() {
        with()
            .queryParam("delayed", "true")
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch(MailQueueRoutes.BASE_URL + "/first_queue/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putMailRepositoriesShouldBeAuthenticated() {
        with()
            .params("protocol", "memory")
        .when()
            .put(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void listMailRepositoriesShouldBeAuthenticated() {
        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getMailRepositoriesShouldBeAuthenticated() {
        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getMailsMailRepositoriesShouldBeAuthenticated() {
        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getMailMailRepositoriesShouldBeAuthenticated() {
        when()
            .get(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/1")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteAMailMailRepositoriesShouldBeAuthenticated() {
        when()
            .delete(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/1")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteMailsMailRepositoriesShouldBeAuthenticated() {
        when()
            .delete(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void reprocessingAllMailRepositoriesShouldBeAuthenticated() {
        with()
            .param("action", "reprocess")
        .when()
            .patch(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void reprocessingOneMailRepositoriesShouldBeAuthenticated() {
        with()
            .param("action", "reprocess")
        .when()
            .patch(MailRepositoriesRoutes.MAIL_REPOSITORIES + "/myRepo/mails/name1")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getGlobalSieveQuotaShouldBeAuthenticated() {
        when()
            .get(SieveQuotaRoutes.DEFAULT_QUOTA_PATH)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deleteGlobalSieveQuotaShouldBeAuthenticated() {
        when()
            .delete(SieveQuotaRoutes.DEFAULT_QUOTA_PATH)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putGlobalSieveQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(SieveQuotaRoutes.DEFAULT_QUOTA_PATH)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getPerUserSieveQuotaShouldBeAuthenticated() {
        when()
            .get(SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void deletePerUsersSieveQuotaShouldBeAuthenticated() {
        when()
            .delete(SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void putPerUserSieveQuotaShouldBeAuthenticated() {
        with()
            .body("42")
        .when()
            .put(SieveQuotaRoutes.ROOT_PATH + "/users/user@james.org")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getTasksRoutesShouldBeAuthenticated() {
        when()
            .get(TasksRoutes.BASE)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void getTaskShouldBeAuthenticated() {
        when()
            .get(TasksRoutes.BASE + "/taskId")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void cancelTaskShouldBeAuthenticated() {
        when()
            .delete(TasksRoutes.BASE + "/taskId")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }

    @Test
    public void awaitTaskShouldBeAuthenticated() {
        when()
            .get(TasksRoutes.BASE + "/taskId/await")
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED_401);
    }
}