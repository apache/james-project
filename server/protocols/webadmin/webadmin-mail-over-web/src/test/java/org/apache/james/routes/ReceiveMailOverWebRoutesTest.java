package org.apache.james.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.ReceiveMailOverWebRoutes;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class ReceiveMailOverWebRoutesTest {

    private WebAdminServer webAdminServer;

    private WebAdminServer createServer() {
        MemoryMailQueueFactory memoryMailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        return WebAdminUtils.createWebAdminServer(new ReceiveMailOverWebRoutes(memoryMailQueueFactory)).start();
    }

    @BeforeEach
    void setup() {
        webAdminServer = createServer();
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setBasePath(ReceiveMailOverWebRoutes.BASE_URL)
                .setPort(server.getPort().getValue())
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .build();
    }


    @Test
    public void whenToIsMissingInRequestThenRequestFails() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message-without-tos.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);
    }

    @Test
    public void statusCode201ReturnedWhenSendingMailWithAllRequiredFields() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(201);
    }

    @Test
    public void requestFailsOnEmptyBody() {
        given()
                .body("")
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);
    }
}
