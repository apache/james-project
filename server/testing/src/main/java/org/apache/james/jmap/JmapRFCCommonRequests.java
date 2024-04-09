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

package org.apache.james.jmap;

import static io.restassured.RestAssured.with;
import static io.restassured.http.ContentType.JSON;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Role;
import org.hamcrest.Matchers;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.path.json.JsonPath;

public class JmapRFCCommonRequests {
    public record UserCredential(Username username, String password, String accountId) {
    }
    public static final Header ACCEPT_JMAP_RFC_HEADER = new Header("accept", "application/json; jmapVersion=rfc-8621");

    public static String getOutboxId(UserCredential userCredential) {
        return getMailboxId(userCredential, Role.OUTBOX);
    }

    public static String getSentId(UserCredential userCredential) {
        return getMailboxId(userCredential, Role.SENT);
    }

    public static String getDraftId(UserCredential userCredential) {
        return getMailboxId(userCredential, Role.DRAFTS);
    }

    public static String getMailboxId(UserCredential userCredential, Role role) {
        return getAllMailboxesIds(userCredential).stream()
            .filter(mailbox -> mailbox.get("role").equals(role.serialize()))
            .map(mailbox -> mailbox.get("id"))
            .findFirst().orElseThrow();
    }

    public static UserCredential getUserCredential(Username username, String password) {
        return getUserCredential(username.asString(), password);
    }

    public static UserCredential getUserCredential(String username, String password) {
        String accountId = with()
            .auth().basic(username, password)
            .header(ACCEPT_JMAP_RFC_HEADER)
            .get("/jmap/session")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .path("primaryAccounts[\"urn:ietf:params:jmap:core\"]");

        return new UserCredential(Username.of(username), password, accountId);
    }

    public static List<Map<String, String>> getAllMailboxesIds(UserCredential userCredential) {
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail",
                        "urn:apache:james:params:jmap:mail:shares"
                    ],
                    "methodCalls": [
                        [
                            "Mailbox/get",
                            {
                                "accountId": "%s"
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(userCredential.accountId()))
            .post("/jmap")
            .andReturn()
            .body()
            .jsonPath()
            .getList("methodResponses[0][1].list");
    }

    public static List<String> listMessageIdsForAccount(UserCredential userCredential) {
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail"
                    ],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "sort": [
                                    {
                                        "isAscending": false,
                                        "property": "receivedAt"
                                    }
                                ]
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(userCredential.accountId()))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .extract()
            .body()
            .path("methodResponses[0][1].ids");
    }

    public static String getLastMessageId(UserCredential userCredential) {
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                    {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "sort": [ {
                                        "isAscending": false,
                                        "property": "receivedAt"
                                    } ]
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(userCredential.accountId()))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getString("methodResponses[0][1].ids[0]");
    }

    public static Object bodyOfMessage(UserCredential userCredential, String messageId) {
        System.out.println("MessageId: " + messageId);
        return getMessageContent(userCredential, messageId)
            .get("methodResponses[0][1].list[0].textBody");
    }

    public static String getLatestMessageId(UserCredential userCredential, Role mailbox) {
        String mailboxId = getMailboxId(userCredential, mailbox);
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                    {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "filter": {
                                    "inMailbox": "%s"
                                },
                                "sort": [ {
                                        "isAscending": false,
                                        "property": "receivedAt"
                                    } ]
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(userCredential.accountId(), mailboxId))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getString("methodResponses[0][1].ids[0]");
    }


    public static JsonPath getMessageContent(UserCredential userCredential, String messageId) {
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    "methodCalls": [
                        [
                            "Email/get",
                            {
                                "accountId": "%s",
                                "ids": ["%s"]
                            },
                            "c1"
                        ]
                    ]
                }
                """.formatted(userCredential.accountId(), messageId))
        .when()
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .jsonPath();
    }

    public static List<String> listMessageIdsInMailbox(UserCredential userCredential, String mailboxId) {
        return with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [
                        "urn:ietf:params:jmap:core",
                        "urn:ietf:params:jmap:mail"
                    ],
                    "methodCalls": [
                        [
                            "Email/query",
                            {
                                "accountId": "%s",
                                "filter": {
                                    "inMailbox": "%s"
                                },
                                "sort": [
                                    {
                                        "isAscending": false,
                                        "property": "receivedAt"
                                    }
                                ]
                            },
                            "c1"
                        ]
                    ]
                }""".formatted(userCredential.accountId(), mailboxId))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .path("methodResponses[0][1].ids");
    }

    public static void deleteMessages(UserCredential userCredential, List<String> idsToDestroy) {
        String idString = idsToDestroy.stream()
            .map(id -> "\"" + id + "\"")
            .collect(Collectors.joining(","));

        with()
            .auth().basic(userCredential.username().asString(), userCredential.password())
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
                {
                    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    "methodCalls": [
                        [
                            "Email/set",
                            {
                                "accountId": "%s",
                                "destroy": [%s]
                            },
                            "c1"
                        ]
                    ]
                }""".formatted(userCredential.accountId(), idString))
            .post("/jmap")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("methodResponses[0][1].destroyed", Matchers.is(Matchers.hasSize(idsToDestroy.size())));
    }
}
