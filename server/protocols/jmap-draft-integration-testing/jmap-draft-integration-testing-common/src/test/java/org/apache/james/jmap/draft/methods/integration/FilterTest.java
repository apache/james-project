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

package org.apache.james.jmap.draft.methods.integration;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static org.apache.james.jmap.HttpJmapAuthentication.authenticateJamesUser;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapCommonRequests.getOutboxId;
import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.util.Locale;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.JmapCommonRequests;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.restassured.RestAssured;

public abstract class FilterTest {

    protected abstract GuiceJamesServer createJmapServer() throws IOException;

    protected abstract MailboxId randomMailboxId();

    private AccessToken accessToken;
    private AccessToken bobAccessToken;
    private GuiceJamesServer jmapServer;

    private MailboxId matchedMailbox;
    private MailboxId inbox;

    @Before
    public void setup() throws Throwable {
        jmapServer = createJmapServer();
        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();

        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        accessToken = authenticateJamesUser(baseUri(jmapServer), ALICE, ALICE_PASSWORD);
        bobAccessToken = authenticateJamesUser(baseUri(jmapServer), BOB, BOB_PASSWORD);

        MailboxProbeImpl mailboxProbe = jmapServer.getProbe(MailboxProbeImpl.class);
        matchedMailbox = mailboxProbe.createMailbox(MailboxPath.forUser(ALICE, "matched"));
        inbox = mailboxProbe.createMailbox(MailboxPath.inbox(ALICE));
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
            .header("Authorization", accessToken.asString())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void getFilterShouldReturnEmptyWhenExplicitNullAccountId() {
        String body = "[[" +
                "  \"getFilter\", " +
                "  {\"accountId\": null}, " +
                "\"#0\"" +
                "]]";

        given()
            .header("Authorization", accessToken.asString())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("filter"))
            .body(ARGUMENTS + ".singleton", hasSize(0));
    }

    @Test
    public void getFilterShouldReturnErrorWhenUnsupportedAccountId() {
        String body = "[[" +
                "  \"getFilter\", " +
                "  {\"accountId\": \"any\"}, " +
                "\"#0\"" +
                "]]";

        given()
            .header("Authorization", accessToken.asString())
            .body(body)
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body(NAME, equalTo("error"))
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("The field 'accountId' of 'GetFilterRequest' is not supported"));
    }

    @Category(BasicFeature.class)
    @Test
    public void setFilterShouldOverwritePreviouslyStoredRules() {
        MailboxId mailbox1 = randomMailboxId();
        MailboxId mailbox2 = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
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
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        with()
            .header("Authorization", accessToken.asString())
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
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", accessToken.asString())
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
    public void setFilterShouldRejectRuleWithInvalidRuleName() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-34e\"," +
                "      \"name\": null," +
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
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("`name` is mandatory"));
    }

    @Test
    public void setFilterShouldRejectRuleWithInvalidRuleId() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": null," +
                "      \"name\": \"some name\"," +
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
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("`id` is mandatory"));
    }

    @Test
    public void setFilterShouldRejectRuleWithoutRuleCondition() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-34e\"," +
                "      \"name\": \"some name\"," +
                "      \"condition\": null," +
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
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", containsString("`condition` is mandatory"));
    }

    @Test
    public void setFilterShouldRejectRuleWithInvalidRuleCondition() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-34e\"," +
                "      \"name\": \"some name\"," +
                "      \"condition\": {" +
                "        \"field\": \"subject\" " +
                "       }," +
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
            .body(ARGUMENTS + ".type", equalTo("invalidArguments"))
            .body(ARGUMENTS + ".description", equalTo("'null' is not a valid comparator name"));
    }

    @Test
    public void setFilterShouldReturnUpdatedSingleton() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
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
            .header("Authorization", accessToken.asString())
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
    public void setFilterShouldAcceptNullAccountId() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"accountId\": null," +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("filterSet"))
            .body(ARGUMENTS + ".updated", hasSize(1));
    }

    @Test
    public void setFilterShouldRejectIfInState() {
        given()
            .header("Authorization", accessToken.asString())
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
    public void setFilterShouldAcceptNullIfInState() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"ifInState\": null," +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .body(NAME, equalTo("filterSet"))
            .body(ARGUMENTS + ".updated", hasSize(1));
    }

    @Category(BasicFeature.class)
    @Test
    public void getFilterShouldRetrievePreviouslyStoredRules() {
        MailboxId mailbox1 = randomMailboxId();
        MailboxId mailbox2 = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
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
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", accessToken.asString())
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

    @Category(BasicFeature.class)
    @Test
    public void setFilterShouldClearPreviouslyStoredRulesWhenEmptyBody() {
        MailboxId mailbox = randomMailboxId();

        given()
            .header("Authorization", accessToken.asString())
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
            .statusCode(200);

        with()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                  "  \"setFilter\", " +
                  "  {" +
                  "    \"singleton\": []" +
                  "  }, " +
                  "\"#0\"" +
                  "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", accessToken.asString())
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

        given()
            .header("Authorization", accessToken.asString())
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
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", accessToken.asString())
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

    @Category(BasicFeature.class)
    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenFromRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + BOB.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.atMost(ONE_MINUTE)
            .until(() -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenToRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"to\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"},{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenCcRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails cc-ed to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"cc\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"cc\": [{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenRecipientRuleMatchesCc() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails cc-ed to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"recipient\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"cc\": [{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenRecipientRuleMatchesTo() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails cc-ed to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"recipient\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"cc\": [{ \"name\": \"Cedric\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenSubjectRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"subject\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"matchme\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"},{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"matchme\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Category(BasicFeature.class)
    @Test
    public void messageShouldBeAppendedInInboxWhenFromDoesNotMatchRule() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.atMost(ONE_MINUTE)
            .until(() -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenToDoesNotMatchRule() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"to\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenCcDoesNotMatchRule() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails cc-ed to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"cc\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"cc\": [{ \"name\": \"Cedric\", \"email\": \"" + BOB.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenRecipientDoesNotMatchRule() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails cc-ed to cedric\"," +
                "      \"condition\": {" +
                "        \"field\": \"recipient\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + CEDRIC.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"cc\": [{ \"name\": \"Cedric\", \"email\": \"" + BOB.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenSubjectRuleDoesNotMatchRule() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"subject\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"matchme\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"},{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"nomatch\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenSubjectRuleDoesNotMatchRuleBecauseOfCase() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"subject\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"different case value\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"},{ \"name\": \"Cedric\", \"email\": \"" + CEDRIC.asString() + "\"}]," +
            "      \"subject\": \"DIFFERENT CASE VALUE\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenContainsComparatorMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"bo\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenContainsComparatorDoesNotMatch() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"ced\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenExactlyEqualsMatchesName() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"exactly-equals\"," +
                "        \"value\": \"Bob\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenExactlyEqualsMatchesAddress() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"exactly-equals\"," +
                "        \"value\": \"" + BOB.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenExactlyEqualsMatchesFullHeader() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"exactly-equals\"," +
                "        \"value\": \"Bob <" + BOB.asString() + ">\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenExactlyEqualsMatchesCaseInsensitivelyFullHeader() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"exactly-equals\"," +
                "        \"value\": \"bob <" + BOB.asString().toUpperCase(Locale.ENGLISH) + ">\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenExactlyEqualsComparatorDoesNotMatch() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"exactly-equals\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"nomatch\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenNotContainsComparatorMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"not-contains\"," +
                "        \"value\": \"other\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenNotContainsComparatorDoesNotMatch() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"not-contains\"," +
                "        \"value\": \"bob\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenContainsNotExactlyEqualsMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"not-exactly-equals\"," +
                "        \"value\": \"nomatch\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenNotExactlyEqualsMatchesAddress() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"not-exactly-equals\"," +
                "        \"value\": \"" + BOB.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenNotExactlyEqualsMatchesFullHeader() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"not-exactly-equals\"," +
                "        \"value\": \"Bob <" + BOB.asString() + ">\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Bob\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInInboxWhenNotExactlyEqualsComparatorMatchesName() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"not-exactly-equals\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"Bob\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap")
        .then()
            .statusCode(200);

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenFirstRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + BOB.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }," +
                "    {" +
                "      \"id\": \"3000-346\"," +
                "      \"name\": \"Emails to alice\"," +
                "      \"condition\": {" +
                "        \"field\": \"to\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + ALICE.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void messageShouldBeAppendedInSpecificMailboxWhenSecondRuleMatches() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"unknown@james.org\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }," +
                "    {" +
                "      \"id\": \"3000-346\"," +
                "      \"name\": \"Emails to alice\"," +
                "      \"condition\": {" +
                "        \"field\": \"to\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + ALICE.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
    }

    @Test
    public void inboxShouldBeEmptyWhenFromRuleMatchesInSpecificMailbox() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"" + BOB.asString() + "\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox));
        assertThat(JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox)).isFalse();
    }

    @Test
    public void matchedMailboxShouldBeEmptyWhenFromRuleDoesntMatch() {
        given()
            .header("Authorization", accessToken.asString())
            .body("[[" +
                "  \"setFilter\", " +
                "  {" +
                "    \"singleton\": [" +
                "    {" +
                "      \"id\": \"3000-345\"," +
                "      \"name\": \"Emails from bob\"," +
                "      \"condition\": {" +
                "        \"field\": \"from\"," +
                "        \"comparator\": \"contains\"," +
                "        \"value\": \"unknown@james.org\"" +
                "      }," +
                "      \"action\": {" +
                "        \"appendIn\": {" +
                "          \"mailboxIds\": [\"" + matchedMailbox.serialize() + "\"]" +
                "        }" +
                "      }" +
                "    }" +
                "  ]}, " +
                "\"#0\"" +
                "]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200);

        String messageCreationId = "creationId1337";
        String requestBody = "[[" +
            "  \"setMessages\"," +
            "  {" +
            "    \"create\": { \"" + messageCreationId  + "\" : {" +
            "      \"from\": { \"name\": \"Me\", \"email\": \"" + BOB.asString() + "\"}," +
            "      \"to\": [{ \"name\": \"Alice\", \"email\": \"" + ALICE.asString() + "\"}]," +
            "      \"subject\": \"subject\"," +
            "      \"mailboxIds\": [\"" + getOutboxId(bobAccessToken) + "\"]" +
            "    }}" +
            "  }," +
            "  \"#0\"" +
            "]]";

        with()
            .header("Authorization", bobAccessToken.asString())
            .body(requestBody)
            .post("/jmap");

        calmlyAwait.until(
            () -> JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, inbox));
        assertThat(JmapCommonRequests.isAnyMessageFoundInRecipientsMailbox(accessToken, matchedMailbox)).isFalse();
    }
}
