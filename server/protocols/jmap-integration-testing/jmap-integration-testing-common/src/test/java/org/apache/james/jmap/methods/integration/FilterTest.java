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

package org.apache.james.jmap.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.apache.james.jmap.TestingConstants.ALICE;
import static org.apache.james.jmap.TestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.TestingConstants.ARGUMENTS;
import static org.apache.james.jmap.TestingConstants.DOMAIN;
import static org.apache.james.jmap.TestingConstants.NAME;
import static org.apache.james.jmap.TestingConstants.jmapRequestSpecBuilder;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.restassured.RestAssured;

public abstract class FilterTest {

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract MailboxId randomMailboxId();

    private AccessToken accessToken;
    private GuiceJamesServer jmapServer;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
                .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort())
                .build();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE, ALICE_PASSWORD);
        accessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
    }

    @After
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void getFilterShouldReturnEmptyByDefault() {
        String body = "[[" +
                "  \"getFilter\", " +
                "  {}, " +
                "\"#0\"" +
                "]]";

        given()
            .header("Authorization", accessToken.serialize())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void setFilterShouldOverwritePreviouslyStoredRules() {
        MailboxId mailbox1 = randomMailboxId();
        MailboxId mailbox2 = randomMailboxId();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My first rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox2.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");
        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"42-ac\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"from\"," +
                  "        \"comparator\": \"exactly-equals\"," +
                  "        \"value\": \"marvin@h2.g2\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "            \"mailboxIds\": [\"" + mailbox1.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"getFilter\", " +
                  "  {}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(1))
            .body(ARGUMENTS + ".singleton[0].id", equalTo("42-ac"))
            .body(ARGUMENTS + ".singleton[0].name", equalTo("My last rule"))
            .body(ARGUMENTS + ".singleton[0].condition.field", equalTo("from"))
            .body(ARGUMENTS + ".singleton[0].condition.comparator", equalTo("exactly-equals"))
            .body(ARGUMENTS + ".singleton[0].condition.value", equalTo("marvin@h2.g2"))
            .body(ARGUMENTS + ".singleton[0].action.appendIn.mailboxIds", containsInAnyOrder(mailbox1.serialize()));
    }

    @Test
    public void setFilterShouldReturnUpdatedSingleton() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("filterSet"))
            .body(ARGUMENTS + ".updated", containsInAnyOrder("singleton"));
    }

    @Test
    public void setFilterShouldRejectDuplicatedRules() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("filterSet"))
            .body(ARGUMENTS + ".updated", hasSize(0))
            .body(ARGUMENTS + ".notUpdated.singleton.type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".notUpdated.singleton.description", equalTo("The following rules were duplicated: ['3000-34e']"));
    }

    @Test
    public void setFilterShouldRejectRulesTargetingSeveralMailboxes() {
        MailboxId mailbox1 = randomMailboxId();
        MailboxId mailbox2 = randomMailboxId();

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox1.serialize() + "\",\"" + mailbox2.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("filterSet"))
            .body(ARGUMENTS + ".updated", hasSize(0))
            .body(ARGUMENTS + ".notUpdated.singleton.type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".notUpdated.singleton.description", equalTo("The following rules targeted several mailboxes, which is not supported: ['3000-34e']"));
    }

    @Test
    public void setFilterShouldRejectAccountId() {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"accountId\": \"any\"," +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'accountId' of 'SetFilterRequest' is not supported"));
    }

    @Test
    public void setFilterShouldRejectIfInState() {
        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"ifInState\": \"any\"," +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'ifInState' of 'SetFilterRequest' is not supported"));
    }

    @Test
    public void getFilterShouldRetrievePreviouslyStoredRules() {
        MailboxId mailbox1 = randomMailboxId();
        MailboxId mailbox2 = randomMailboxId();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"42-ac\"," +
                  "      \"name\": \"My first rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"from\"," +
                  "        \"comparator\": \"exactly-equals\"," +
                  "        \"value\": \"marvin@h2.g2\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "            \"mailboxIds\": [\"" + mailbox1.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox2.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"getFilter\", " +
                  "  {}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(2))
            .body(ARGUMENTS + ".singleton[0].id", equalTo("42-ac"))
            .body(ARGUMENTS + ".singleton[0].name", equalTo("My first rule"))
            .body(ARGUMENTS + ".singleton[0].condition.field", equalTo("from"))
            .body(ARGUMENTS + ".singleton[0].condition.comparator", equalTo("exactly-equals"))
            .body(ARGUMENTS + ".singleton[0].condition.value", equalTo("marvin@h2.g2"))
            .body(ARGUMENTS + ".singleton[0].action.appendIn.mailboxIds", containsInAnyOrder(mailbox1.serialize()))
            .body(ARGUMENTS + ".singleton[1].id", equalTo("3000-34e"))
            .body(ARGUMENTS + ".singleton[1].name", equalTo("My last rule"))
            .body(ARGUMENTS + ".singleton[1].condition.field", equalTo("subject"))
            .body(ARGUMENTS + ".singleton[1].condition.comparator", equalTo("contains"))
            .body(ARGUMENTS + ".singleton[1].condition.value", equalTo("question"))
            .body(ARGUMENTS + ".singleton[1].action.appendIn.mailboxIds", containsInAnyOrder(mailbox2.serialize()));
    }

    @Test
    public void setFilterShouldClearPreviouslyStoredRulesWhenEmptyBody() {
        MailboxId mailbox = randomMailboxId();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-34e\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"getFilter\", " +
                  "  {}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void allFieldsAndComparatorsShouldBeSupported() {
        MailboxId mailbox = randomMailboxId();

        with()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": [" +
                  "    {" +
                  "      \"id\": \"3000-341\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"subject\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-342\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"cc\"," +
                  "        \"comparator\": \"not-contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-343\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"to\"," +
                  "        \"comparator\": \"exactly-equals\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-344\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"recipient\"," +
                  "        \"comparator\": \"not-exactly-equals\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }," +
                  "    {" +
                  "      \"id\": \"3000-345\"," +
                  "      \"name\": \"My last rule\"," +
                  "      \"condition\": {" +
                  "        \"field\": \"from\"," +
                  "        \"comparator\": \"contains\"," +
                  "        \"value\": \"question\"" +
                  "      }," +
                  "      \"action\": {" +
                  "        \"appendIn\": {" +
                  "          \"mailboxIds\": [\"" + mailbox.serialize() + "\"]" +
                  "        }" +
                  "      }" +
                  "    }" +
                  "  ]}, " +
                  "\"#0\"" +
                  "]]")
            .post("/jmap");

        given()
            .header("Authorization", accessToken.serialize())
            .body("[[" +
                  "  \"getFilter\", " +
                  "  {}, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(5))
            .body(ARGUMENTS + ".singleton[0].id", equalTo("3000-341"))
            .body(ARGUMENTS + ".singleton[0].condition.field", equalTo("subject"))
            .body(ARGUMENTS + ".singleton[0].condition.comparator", equalTo("contains"))
            .body(ARGUMENTS + ".singleton[1].id", equalTo("3000-342"))
            .body(ARGUMENTS + ".singleton[1].condition.field", equalTo("cc"))
            .body(ARGUMENTS + ".singleton[1].condition.comparator", equalTo("not-contains"))
            .body(ARGUMENTS + ".singleton[2].id", equalTo("3000-343"))
            .body(ARGUMENTS + ".singleton[2].condition.field", equalTo("to"))
            .body(ARGUMENTS + ".singleton[2].condition.comparator", equalTo("exactly-equals"))
            .body(ARGUMENTS + ".singleton[3].id", equalTo("3000-344"))
            .body(ARGUMENTS + ".singleton[3].condition.field", equalTo("recipient"))
            .body(ARGUMENTS + ".singleton[3].condition.comparator", equalTo("not-exactly-equals"))
            .body(ARGUMENTS + ".singleton[4].id", equalTo("3000-345"))
            .body(ARGUMENTS + ".singleton[4].condition.field", equalTo("from"))
            .body(ARGUMENTS + ".singleton[4].condition.comparator", equalTo("contains"));
    }

}
