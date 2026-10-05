package org.apache.james;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.xodus.XodusJamesConfiguration;
import org.apache.james.xodus.XodusJamesServerMain;
import org.awaitility.Awaitility;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.restassured.RestAssured;

public class XodusBackupRestoreTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusBackupRestoreTest.class);

    private static final String DOMAIN = "backup-test.local";
    private static final String USER = "user@" + DOMAIN;
    private static final String PASSWORD = "password123";
    private static final int INITIAL_MESSAGES = 10;
    private static final int MESSAGES_TO_DELETE = 4;

    @Test
    void shouldBackupDeleteAndRestoreSuccessfully(@TempDir Path workingDir) throws Exception {
        XodusJamesConfiguration config = XodusJamesConfiguration.builder()
            .workingDirectory(workingDir.toFile())
            .configurationFromClasspath()
            .build();

        GuiceJamesServer server = XodusJamesServerMain.createServer(config);
        server.start();

        int initialDomains = 0;
        try {
            // 1. Setup Domain & User
            server.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DOMAIN)
                .addUser(USER, PASSWORD);

            org.apache.james.util.Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
            int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
            WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build();

            // 2. Inject INITIAL_MESSAGES via SMTP
            SMTPMessageSender smtpSender = new SMTPMessageSender(DOMAIN);
            for (int i = 1; i <= INITIAL_MESSAGES; i++) {
                smtpSender.connect("127.0.0.1", smtpPort)
                    .authenticate(USER, PASSWORD)
                    .sendMessageWithHeaders(USER, USER, "Subject: Msg " + i + "\r\n\r\nBody " + i);
            }

            // Await delivery via IMAP
            TestIMAPClient imapClient = new TestIMAPClient();
            Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try {
                        imapClient.connect("127.0.0.1", imapPort)
                            .login(USER, PASSWORD)
                            .select(TestIMAPClient.INBOX);
                        boolean ok = imapClient.getMessageCount(TestIMAPClient.INBOX) == INITIAL_MESSAGES;
                        imapClient.disconnect();
                        return ok;
                    } catch (Exception e) {
                        return false;
                    }
                });

            LOGGER.info("All {} messages delivered to INBOX", INITIAL_MESSAGES);

            // 3. Online Hot Backup via WebAdmin (as async Task)
            File backupDir = workingDir.resolve("backups").toFile();
            backupDir.mkdirs();

            String taskId = given()
                .queryParam("backupDir", backupDir.getAbsolutePath())
            .when()
                .post("/xodus/backup")
            .then()
                .statusCode(HttpStatus.CREATED_201)
                .extract()
                .jsonPath()
                .getString("taskId");

            when()
                .get("/tasks/" + taskId + "/await")
            .then()
                .statusCode(HttpStatus.OK_200)
                .body("status", org.hamcrest.Matchers.equalTo("completed"));

            File[] generatedZips = backupDir.listFiles((d, name) -> name.endsWith(".zip"));
            assertThat(generatedZips).isNotNull().isNotEmpty();
            File backupZipFile = generatedZips[0];
            assertThat(backupZipFile).exists().isNotEmpty();
            LOGGER.info("Backup successfully generated at {}", backupZipFile.getAbsolutePath());

            // 4. Verify initial domains and users count via WebAdmin
            int initialUsers = when()
                .get("/xodus/check")
            .then()
                .statusCode(HttpStatus.OK_200)
                .body("status", org.hamcrest.Matchers.equalTo("HEALTHY"))
                .extract()
                .jsonPath()
                .getInt("totalUsers");

            initialDomains = when()
                .get("/xodus/check")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .jsonPath()
                .getInt("totalDomains");

            assertThat(initialUsers).isEqualTo(1);
            assertThat(initialDomains).isGreaterThanOrEqualTo(1);
            LOGGER.info("Initial counts before deletion: users={}, domains={}", initialUsers, initialDomains);

            // Delete user via WebAdmin
            when()
                .delete("/users/" + USER)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            int usersAfterDeletion = when()
                .get("/xodus/check")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .jsonPath()
                .getInt("totalUsers");

            assertThat(usersAfterDeletion).isEqualTo(0);
            LOGGER.info("User {} deleted. Remaining users in Xodus: {}", USER, usersAfterDeletion);

        } finally {
            server.stop();
        }

        // 5. Restore from backup ZIP into var/xodus
        File xodusDir = workingDir.resolve("var").resolve("xodus").toFile();
        assertThat(xodusDir).exists();

        // Clear live database directory before restoring
        deleteDirectoryRecursively(xodusDir.toPath());
        xodusDir.mkdirs();

        // Extract backup zip
        File backupDir = workingDir.resolve("backups").toFile();
        File[] zipFiles = backupDir.listFiles((d, name) -> name.endsWith(".zip"));
        assertThat(zipFiles).isNotNull().isNotEmpty();
        File backupZip = zipFiles[0];

        unzip(backupZip, xodusDir);
        LOGGER.info("Extracted backup into {}", xodusDir.getAbsolutePath());

        // 6. Restart Server with restored database
        GuiceJamesServer restoredServer = XodusJamesServerMain.createServer(config);
        restoredServer.start();

        try {
            WebAdminGuiceProbe restoredWebAdminProbe = restoredServer.getProbe(WebAdminGuiceProbe.class);
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(restoredWebAdminProbe.getWebAdminPort()).build();

            // Verify integrity check via WebAdmin
            int restoredUsers = when()
                .get("/xodus/check")
            .then()
                .statusCode(HttpStatus.OK_200)
                .body("status", org.hamcrest.Matchers.equalTo("HEALTHY"))
                .body("environmentOpen", org.hamcrest.Matchers.equalTo(true))
                .extract()
                .jsonPath()
                .getInt("totalUsers");

            int restoredDomains = when()
                .get("/xodus/check")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .jsonPath()
                .getInt("totalDomains");

            // Verify original user and domain are fully restored from backup!
            assertThat(restoredUsers).isEqualTo(1);
            assertThat(restoredDomains).isEqualTo(initialDomains);

            // Verify user can be queried and authenticated via WebAdmin
            when()
                .get("/users")
            .then()
                .statusCode(HttpStatus.OK_200)
                .body("username", org.hamcrest.Matchers.hasItem(USER));

            LOGGER.info("RESTORE SUCCESSFUL: User {} restored and verified in Xodus database!", USER);
        } finally {
            restoredServer.stop();
        }
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
