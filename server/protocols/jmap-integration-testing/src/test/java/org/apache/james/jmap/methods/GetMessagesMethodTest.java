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

package org.apache.james.jmap.methods;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.JmapAuthentication;
import org.apache.james.jmap.JmapServer;
import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMessagesMethodTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();
    private JmapServer jmapServer = jmapServer(temporaryFolder, embeddedElasticSearch, cassandra);

    protected abstract JmapServer jmapServer(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra);

    @Rule
    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch)
        .around(jmapServer);

    private AccessToken accessToken;
    private ParseContext jsonPath;
    private String username;

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));
        jsonPath = JsonPath.using(Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build());

        String domain = "domain.tld";
        username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        jmapServer.serverProbe().createMailbox("#private", "username", "inbox");
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @Test
    public void getMessagesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMessagesShouldIgnoreUnknownArguments() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"WAT\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .body("[0][1].notFound", hasSize(0))
            .body("[0][1].list", hasSize(0))
            .body("[0][2]", equalTo("#0"));
    }

    @Test
    public void getMessagesShouldErrorInvalidArgumentsWhenRequestContainsInvalidArgument() throws Exception {
        given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessages\", {\"ids\": null}, \"#0\"]]")
                .when()
                .post("/jmap")
                .then()
                .statusCode(200)
                .content(equalTo("[[\"error\",{\"type\":\"invalidArguments\"},\"#0\"]]"));
    }

    @Test
    public void getMessagesShouldReturnEmptyListWhenNoMessage() throws Exception {
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messages\","))
            .extract()
            .asString();
        
        assertThat(JsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<Integer>read("$.[0].[1].list.length()")).isEqualTo(0);
    }

    @Test
    public void getMessagesShouldReturnNoFoundIndicesWhenMessageNotFound() throws Exception {
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"username|inbox|12\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messages\","))
            .extract()
            .asString();
        
        assertThat(JsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<List<String>>read("$.[0].[1].notFound")).containsExactly("username|inbox|12");
    }
    
    @Test
    public void getMessagesShouldReturnMessagesWhenAvailable() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messages\","))
            .extract()
            .asString();

        String firstResponsePath = "$.[0].[1]";
        String firstMessagePath = firstResponsePath + ".list[0]";

        assertThat(JsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<Integer>read(firstResponsePath + ".list.length()")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<String>read(firstMessagePath + ".id")).isEqualTo(username + "|inbox|1");
        assertThat(JsonPath.parse(response).<String>read(firstMessagePath + ".subject")).isEqualTo("my test subject");
        assertThat(JsonPath.parse(response).<String>read(firstMessagePath + ".textBody")).isEqualTo("testmail");
        assertThat(JsonPath.parse(response).<Boolean>read(firstMessagePath + ".isUnread")).isTrue();
        assertThat(JsonPath.parse(response).<String>read(firstMessagePath + ".preview")).isEqualTo("testmail");
        assertThat(JsonPath.parse(response).<Map<String, String>>read(firstMessagePath + ".headers")).containsExactly(MapEntry.entry("subject", "my test subject"));
        assertThat(JsonPath.parse(response).<String>read(firstMessagePath + ".date")).isEqualTo("2014-10-30T14:12:00Z");
    }
    
    @Test
    public void getMessagesShouldReturnFilteredPropertiesMessagesWhenAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream("Subject: my test subject\r\n\r\ntestmail".getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"], \"properties\": [\"id\", \"subject\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messages\","))
            .extract()
            .asString();

        String firstResponsePath = "$.[0].[1]";
        String firstMessagePath = firstResponsePath + ".list[0]";

        assertThat(jsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<Integer>read(firstResponsePath + ".list.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".id")).isEqualTo(username + "|inbox|1");
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".subject")).isEqualTo("my test subject");
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".textBody")).isNull();
        assertThat(jsonPath.parse(response).<Boolean>read(firstMessagePath + ".isUnread")).isNull();
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".preview")).isNull();
        assertThat(jsonPath.parse(response).<Map<String, String>>read(firstMessagePath + ".headers")).isNull();
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".date")).isNull();
    }
    
    @Test
    public void getMessagesShouldReturnFilteredHeaderPropertyWhenAsked() throws Exception {
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, username, "inbox");

        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        jmapServer.serverProbe().appendMessage(username, new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "inbox"),
                new ByteArrayInputStream(("From: user@domain.tld\r\n"
                        + "header1: Header1Content\r\n"
                        + "HEADer2: Header2Content\r\n"
                        + "Subject: my test subject\r\n"
                        + "\r\n"
                        + "testmail").getBytes()), Date.from(dateTime.toInstant()), false, new Flags());
        
        embeddedElasticSearch.awaitForElasticSearch();
        
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMessages\", {\"ids\": [\"" + username + "|inbox|1\"], \"properties\": [\"headers.from\", \"headers.heADER2\"]}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"messages\","))
            .extract()
            .asString();

        String firstResponsePath = "$.[0].[1]";
        String firstMessagePath = firstResponsePath + ".list[0]";

        assertThat(jsonPath.parse(response).<Integer>read("$.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<Integer>read(firstResponsePath + ".list.length()")).isEqualTo(1);
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".id")).isEqualTo(username + "|inbox|1");
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".subject")).isNull();
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".textBody")).isNull();
        assertThat(jsonPath.parse(response).<Boolean>read(firstMessagePath + ".isUnread")).isNull();
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".preview")).isNull();
        assertThat(jsonPath.parse(response).<Map<String, String>>read(firstMessagePath + ".headers")).containsOnly(MapEntry.entry("from", "user@domain.tld"), MapEntry.entry("header2", "Header2Content"));
        assertThat(jsonPath.parse(response).<String>read(firstMessagePath + ".date")).isNull();
    }
}
