package org.apache.james;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.apache.james.webadmin.routes.UserRoutes;
import org.apache.james.xodus.XodusJamesConfiguration;
import org.apache.james.xodus.XodusJamesServerMain;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;

class XodusWebAdminServerIntegrationTest implements JamesServerConcreteContract {

    private static final String DOMAIN = "domain.local";
    private static final String USERNAME = "alice@" + DOMAIN;
    private static final String SPECIFIC_DOMAIN = DomainsRoutes.DOMAINS + "/" + DOMAIN;
    private static final String SPECIFIC_USER = UserRoutes.USERS + "/" + USERNAME;

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<XodusJamesConfiguration>(tmpDir ->
        XodusJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(XodusJamesServerMain::createServer)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private DataProbe dataProbe;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
    }

    @Test
    void webAdminShouldManageDomains() throws Exception {
        // Create domain
        when()
            .put(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).contains(DOMAIN);

        // List domains
        when()
            .get(DomainsRoutes.DOMAINS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(".", hasItem(DOMAIN));

        // Delete domain
        when()
            .delete(SPECIFIC_DOMAIN)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listDomains()).doesNotContain(DOMAIN);
    }

    @Test
    void webAdminShouldManageUsers() throws Exception {
        dataProbe.addDomain(DOMAIN);

        // Create user
        given()
            .body("{\"password\":\"secret123\"}")
        .when()
            .put(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).contains(USERNAME);

        // Verify password
        given()
            .body("{\"password\":\"secret123\"}")
        .when()
            .post(SPECIFIC_USER + "/verify")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // List users
        when()
            .get(UserRoutes.USERS)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("username", hasItem(USERNAME));

        // Delete user
        when()
            .delete(SPECIFIC_USER)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(dataProbe.listUsers()).doesNotContain(USERNAME);
    }

    @Test
    void webAdminShouldPerformXodusBackupAndCheck() {
        // Health / Integrity Check
        when()
            .get("/xodus/check")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", org.hamcrest.Matchers.equalTo("HEALTHY"))
            .body("environmentOpen", org.hamcrest.Matchers.equalTo(true));

        // Online Hot Backup as async Task
        String taskId = when()
            .post("/xodus/backup")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", org.hamcrest.Matchers.startsWith("/tasks/"))
            .body("taskId", org.hamcrest.Matchers.notNullValue())
            .extract()
            .jsonPath()
            .getString("taskId");

        // Await task completion
        when()
            .get("/tasks/" + taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", org.hamcrest.Matchers.equalTo("completed"))
            .body("type", org.hamcrest.Matchers.equalTo("xodus-backup"));
    }

    @Test
    void webAdminShouldTriggerReIndexingTask() {
        String taskId = given()
            .queryParam("task", "reIndex")
        .when()
            .post("/mailboxes")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", org.hamcrest.Matchers.startsWith("/tasks/"))
            .extract()
            .jsonPath()
            .getString("taskId");

        when()
            .get("/tasks/" + taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", org.hamcrest.Matchers.equalTo("completed"));
    }

    @Test
    void webAdminShouldSupportMailboxExportAndRestore() throws Exception {
        // Ensure user exists
        when()
            .put(SPECIFIC_DOMAIN);
        given()
            .body("{\"password\":\"secret\"}")
        .when()
            .put(SPECIFIC_USER);

        // Standard Apache James RFC Transfer Export Task: POST /users/{username}/mailboxes?task=export
        String exportTaskId = given()
            .queryParam("task", "export")
        .when()
            .post(SPECIFIC_USER + "/mailboxes")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", org.hamcrest.Matchers.startsWith("/tasks/"))
            .extract()
            .jsonPath()
            .getString("taskId");

        // Await export task
        when()
            .get("/tasks/" + exportTaskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", org.hamcrest.Matchers.equalTo("completed"))
            .body("type", org.hamcrest.Matchers.equalTo("MailboxesExportTask"));

        // Create an empty ZIP archive (RFC backup format)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            // empty zip
        }
        byte[] zipData = baos.toByteArray();

        // Standard Apache James RFC Transfer Restore Task: POST /users/{username}/mailboxes?task=restore&force=true
        String restoreTaskId = given()
            .queryParam("task", "restore")
            .queryParam("force", "true")
            .body(zipData)
        .when()
            .post(SPECIFIC_USER + "/mailboxes")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .header("Location", org.hamcrest.Matchers.startsWith("/tasks/"))
            .extract()
            .jsonPath()
            .getString("taskId");

        // Await restore task
        when()
            .get("/tasks/" + restoreTaskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", org.hamcrest.Matchers.equalTo("completed"))
            .body("type", org.hamcrest.Matchers.equalTo("MailboxesRestoreTask"));
    }
}
