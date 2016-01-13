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
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayInputStream;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.JsonPath;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;

public abstract class GetMailboxesMethodTest {

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

    @Before
    public void setup() throws Exception {
        RestAssured.port = jmapServer.getPort();
        RestAssured.config = newConfig().encoderConfig(encoderConfig().defaultContentCharset(Charsets.UTF_8));

        String domain = "domain.tld";
        String username = "username@" + domain;
        String password = "password";
        jmapServer.serverProbe().addDomain(domain);
        jmapServer.serverProbe().addUser(username, password);
        accessToken = JmapAuthentication.authenticateJamesUser(username, password);
    }

    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullAccountId() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"accountId\": \"1\"}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullIds() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }
    
    @Test
    public void getMailboxesShouldErrorNotSupportedWhenRequestContainsNonNullProperties() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\": []}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"Not yet implemented\"},\"#0\"]]"));
    }

    @Test
    public void getMailboxesShouldErrorInvalidArgumentsWhenRequestIsInvalid() throws Exception {
        given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"ids\": true}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(equalTo("[[\"error\",{\"type\":\"invalidArguments\"},\"#0\"]]"));
    }

    @Test
    public void getMailboxesShouldReturnEmptyListWhenNoMailboxes() throws Exception {
        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"mailboxes\","))
            .extract()
            .asString();
        
        String firstResponsePath = "$.[0].[1]";
        assertThat(JsonPath.parse(response).<Integer>read(firstResponsePath + ".list.length()")).isEqualTo(0);
    }

    @Test
    public void getMailboxesShouldReturnMailboxesWhenAvailable() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "name");

        jmapServer.serverProbe().appendMessage(user, new MailboxPath(MailboxConstants.USER_NAMESPACE, user, "name"), 
                new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), false, new Flags());

        String response = given()
            .accept(ContentType.JSON)
            .contentType(ContentType.JSON)
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {}, \"#0\"]]")
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .content(startsWith("[[\"mailboxes\","))
            .extract()
            .asString();
        
        String firstMailboxPath = "$.[0].[1].list.[0]";
        assertThat(JsonPath.parse(response).<String>read(firstMailboxPath + ".name")).isEqualTo("name");
        assertThat(JsonPath.parse(response).<String>read(firstMailboxPath + ".parentId")).isNull();
        assertThat(JsonPath.parse(response).<String>read(firstMailboxPath + ".role")).isNull();
        assertThat(JsonPath.parse(response).<Integer>read(firstMailboxPath + ".sortOrder")).isEqualTo(1000);
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mustBeOnlyMailbox")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayReadItems")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayAddItems")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayRemoveItems")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayCreateChild")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayRename")).isFalse();
        assertThat(JsonPath.parse(response).<Boolean>read(firstMailboxPath + ".mayDelete")).isFalse();
        assertThat(JsonPath.parse(response).<Integer>read(firstMailboxPath + ".totalMessages")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<Integer>read(firstMailboxPath + ".unreadMessages")).isEqualTo(1);
        assertThat(JsonPath.parse(response).<Integer>read(firstMailboxPath + ".unreadThreads")).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getMailboxesShouldReturnMailboxesWithSortOrder() throws Exception {
        String user = "user";
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "inbox");
        jmapServer.serverProbe().createMailbox(MailboxConstants.USER_NAMESPACE, user, "trash");

        String response = given()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMailboxes\", {}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .content(startsWith("[[\"mailboxes\",{\"accountId\":null,\"state\":null,\"list\":[{\"id\":\""))
                .extract()
                .asString();
        assertThat(JsonPath.parse(response).<List<Map<String, Object>>>read("$.[0].[1].list[*].['name','sortOrder']"))
                .hasSize(2)
                .containsOnly(
                        ImmutableMap.of("name", "trash", "sortOrder", 60),
                        ImmutableMap.of("name", "inbox", "sortOrder", 10));
    }

}
