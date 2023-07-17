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

package org.apache.james.webadmin.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.apache.james.DefaultVacationService;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.date.DefaultZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationService;
import org.apache.james.vacation.memory.MemoryNotificationRegistry;
import org.apache.james.vacation.memory.MemoryVacationRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

class VacationRoutesTest {

    private static final String BOB = "bob@example.org";
    private static final String ALICE = "alice@example.org";
    private static final String CAROL = "carol@example.org";

    private static final Domain DOMAIN = Domain.of("example.org");

    private static final Vacation VACATION = Vacation.builder()
        .enabled(false)
        .fromDate(Optional.of(ZonedDateTime.parse("2021-09-13T10:00:00Z")))
        .toDate(Optional.of(ZonedDateTime.parse("2021-09-20T19:00:00Z")))
        .subject(Optional.of("I am on vacation"))
        .textBody(Optional.of("I am on vacation, will be back soon."))
        .htmlBody(Optional.of("<p>I am on vacation, will be back soon.</p>"))
        .build();

    private static String isoString(ZonedDateTime date) {
        return date.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private WebAdminServer webAdminServer;
    private VacationService vacationService;

    @BeforeEach
    public void setUp() throws Exception {
        vacationService = new DefaultVacationService(
            new MemoryVacationRepository(), new MemoryNotificationRegistry(new DefaultZonedDateTimeProvider()));

        SimpleDomainList domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        VacationRoutes vacationRoutes = new VacationRoutes(vacationService, usersRepository, new JsonTransformer());
        this.webAdminServer = WebAdminUtils.createWebAdminServer(vacationRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        usersRepository.addUser(Username.of(BOB), "secret");
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void getVacation() {
        vacationService.modifyVacation(AccountId.fromString(BOB), VacationPatch.builderFrom(VACATION).build()).block();

        when()
            .get(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body("enabled", equalTo(VACATION.isEnabled()))
            .body("fromDate", equalTo(isoString(VACATION.getFromDate().get())))
            .body("toDate", equalTo(isoString(VACATION.getToDate().get())))
            .body("subject", equalTo(VACATION.getSubject().get()))
            .body("textBody", equalTo(VACATION.getTextBody().get()))
            .body("htmlBody", equalTo(VACATION.getHtmlBody().get()));
    }

    @Test
    void getVacationFails() {
        when()
            .get(VacationRoutes.VACATION + SEPARATOR + CAROL)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("The user 'carol@example.org' does not exist"));
    }

    @Test
    void postVacationCreates() {
        AccountId bob = AccountId.fromString(BOB);

        given()
            .body("{\"enabled\":true,\"fromDate\":\"2021-09-20T10:00:00Z\",\"toDate\":\"2021-09-27T18:00:00Z\"," +
                "\"subject\":\"On vacation again\",\"textBody\":\"Need more vacation!\",\"htmlBody\":\"<p>Need more vacation!</p>\"}")
        .when()
            .post(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        Vacation vacation = vacationService.retrieveVacation(bob).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(vacation).isNotNull();
            softly.assertThat(vacation.isEnabled()).isTrue();
            softly.assertThat(vacation.getFromDate()).isEqualTo(Optional.of(ZonedDateTime.parse("2021-09-20T10:00:00Z")));
            softly.assertThat(vacation.getToDate()).isEqualTo(Optional.of(ZonedDateTime.parse("2021-09-27T18:00:00Z")));
            softly.assertThat(vacation.getSubject()).isEqualTo(Optional.of("On vacation again"));
            softly.assertThat(vacation.getTextBody()).isEqualTo(Optional.of("Need more vacation!"));
            softly.assertThat(vacation.getHtmlBody()).isEqualTo(Optional.of("<p>Need more vacation!</p>"));
        });
    }

    @Test
    void postVacationUpdatesAll() throws Exception {
        AccountId bob = AccountId.fromString(BOB);
        RecipientId alice = RecipientId.fromMailAddress(new MailAddress(ALICE));

        vacationService.modifyVacation(bob, VacationPatch.builderFrom(VACATION).build())
            .then(vacationService.registerNotification(bob, alice, Optional.empty())).block();

        given()
            .body("{\"enabled\":true,\"fromDate\":\"2021-09-20T10:00:00Z\",\"toDate\":\"2021-09-27T18:00:00Z\"," +
                "\"subject\":\"On vacation again\",\"textBody\":\"Need more vacation!\",\"htmlBody\":\"<p>Need more vacation!</p>\"}")
        .when()
            .post(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        Vacation vacation = vacationService.retrieveVacation(bob).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(vacation).isNotNull();
            softly.assertThat(vacation.isEnabled()).isTrue();
            softly.assertThat(vacation.getFromDate()).isEqualTo(Optional.of(ZonedDateTime.parse("2021-09-20T10:00:00Z")));
            softly.assertThat(vacation.getToDate()).isEqualTo(Optional.of(ZonedDateTime.parse("2021-09-27T18:00:00Z")));
            softly.assertThat(vacation.getSubject()).isEqualTo(Optional.of("On vacation again"));
            softly.assertThat(vacation.getTextBody()).isEqualTo(Optional.of("Need more vacation!"));
            softly.assertThat(vacation.getHtmlBody()).isEqualTo(Optional.of("<p>Need more vacation!</p>"));
        });

        Boolean registered = vacationService.isNotificationRegistered(bob, alice).block();
        assertThat(registered).isFalse();
    }

    @Test
    void postVacationUpdatesPartial() throws Exception {
        AccountId bob = AccountId.fromString(BOB);
        RecipientId alice = RecipientId.fromMailAddress(new MailAddress(ALICE));

        vacationService.modifyVacation(bob, VacationPatch.builderFrom(VACATION).build())
            .then(vacationService.registerNotification(bob, alice, Optional.empty())).block();

        given()
            .body("{\"enabled\":true,\"subject\":\"More vacation\"}")
        .when()
            .post(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        Vacation vacation = vacationService.retrieveVacation(bob).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(vacation).isNotNull();
            softly.assertThat(vacation.isEnabled()).isTrue();
            softly.assertThat(vacation.getFromDate()).isEqualTo(VACATION.getFromDate());
            softly.assertThat(vacation.getToDate()).isEqualTo(VACATION.getToDate());
            softly.assertThat(vacation.getSubject()).isEqualTo(Optional.of("More vacation"));
            softly.assertThat(vacation.getTextBody()).isEqualTo(VACATION.getTextBody());
            softly.assertThat(vacation.getHtmlBody()).isEqualTo(VACATION.getHtmlBody());
        });

        Boolean registered = vacationService.isNotificationRegistered(bob, alice).block();
        assertThat(registered).isFalse();
    }

    @Test
    void postVacationFails() {
        given()
            .body("{\"enabled\":true,\"subject\":\"On vacation again\"}")
        .when()
            .post(VacationRoutes.VACATION + SEPARATOR + CAROL)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("The user 'carol@example.org' does not exist"));
    }

    @Test
    void postVacationHandlesBadJson() {
        given()
            .body("{not really a JSON object}")
        .when()
            .post(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400));
    }

    @Test
    void deleteVacation() throws Exception {
        AccountId bob = AccountId.fromString(BOB);
        RecipientId alice = RecipientId.fromMailAddress(new MailAddress(ALICE));

        vacationService.modifyVacation(bob, VacationPatch.builderFrom(VACATION).build())
            .then(vacationService.registerNotification(bob, alice, Optional.empty())).block();

        when()
            .delete(VacationRoutes.VACATION + SEPARATOR + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        Vacation vacation = vacationService.retrieveVacation(bob).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(vacation).isNotNull();
            softly.assertThat(vacation.isEnabled()).isFalse();
            softly.assertThat(vacation.getFromDate()).isEmpty();
            softly.assertThat(vacation.getToDate()).isEmpty();
            softly.assertThat(vacation.getSubject()).isEmpty();
            softly.assertThat(vacation.getTextBody()).isEmpty();
            softly.assertThat(vacation.getHtmlBody()).isEmpty();
        });

        Boolean registered = vacationService.isNotificationRegistered(bob, alice).block();
        assertThat(registered).isFalse();
    }

    @Test
    void deleteVacationFails() {
        when()
            .delete(VacationRoutes.VACATION + SEPARATOR + CAROL)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is(ErrorResponder.ErrorType.NOT_FOUND.getType()))
            .body("message", is("The user 'carol@example.org' does not exist"));
    }
}
