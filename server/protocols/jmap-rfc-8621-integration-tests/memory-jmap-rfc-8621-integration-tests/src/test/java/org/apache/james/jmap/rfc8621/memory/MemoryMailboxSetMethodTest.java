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

package org.apache.james.jmap.rfc8621.memory;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.rfc8621.contract.MailboxSetMethodContract;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.junit.jupiter.api.Test;

public class MemoryMailboxSetMethodTest extends PerClassMemoryBase implements MailboxSetMethodContract {
    @Override
    public MailboxId randomMailboxId() {
        return InMemoryId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }

    @Override
    public String errorInvalidMailboxIdMessage(String value) {
        return String.format("%s is not a mailboxId: For input string: \\\"%s\\\"", value, value);
    }

    @Test
    void updateShouldApplyBothPatchesForTokensThatParseToTheSameMailbox(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "original")).serialize();
        String alias = "0" + id;

        var response = given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [
                    ["Mailbox/get", {
                      "accountId": "%s",
                      "ids": ["%s"],
                      "properties": ["id", "name", "isSubscribed"]
                    }, "getOriginalMailbox"],
                    ["Mailbox/set", {
                      "accountId": "%s",
                      "update": {
                        "%s": {"name": "renamed"},
                        "%s": {"isSubscribed": true}
                      }
                    }, "updateAliases"],
                    ["Mailbox/get", {
                      "accountId": "%s",
                      "ids": ["%s"],
                      "properties": ["id", "name", "isSubscribed"]
                    }, "getUpdatedMailbox"]
                  ]
                }
                """.formatted(bobAccountId(), id, bobAccountId(), id, alias, bobAccountId(), id))
        .when().post()
        .then()
            .log().ifValidationFails()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract();

        Object originalMailbox = response.path("methodResponses.find { it[2] == 'getOriginalMailbox' }[1].list");
        assertThatJson(originalMailbox).isEqualTo("""
            [{"id": "%s", "name": "original", "isSubscribed": false}]
            """.formatted(id));

        Map<String, Object> update = response.path("methodResponses.find { it[2] == 'updateAliases' }[1].subMap(['updated', 'notUpdated'])");
        assertThatJson(update).isEqualTo("""
            {"updated": {"%s": {}}}
            """.formatted(id));

        Object updatedMailbox = response.path("methodResponses.find { it[2] == 'getUpdatedMailbox' }[1].list");
        assertThatJson(updatedMailbox).isEqualTo("""
            [{"id": "%s", "name": "renamed", "isSubscribed": true}]
            """.formatted(id));
    }

    @Test
    void updateShouldKeepLeadingZeroInTheErrorKeyForARejectedPatch(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "original")).serialize();
        String alias = "0" + id;

        Map<String, Object> update = given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [
                    ["Mailbox/set", {
                      "accountId": "%s",
                      "update": {
                        "%s": {"name": "renamed"},
                        "%s": {"sortOrder": 123}
                      }
                    }, "updateAliases"]
                  ]
                }
                """.formatted(bobAccountId(), id, alias))
        .when().post()
        .then()
            .log().ifValidationFails()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract()
            .path("methodResponses.find { it[2] == 'updateAliases' }[1].subMap(['updated', 'notUpdated'])");

        assertThatJson(update).isEqualTo("""
            {
              "updated": {
                "%s": "${json-unit.ignore}"
              },
              "notUpdated": {
                "%s": {
                  "type": "invalidArguments",
                  "description": "Can not modify server-set properties",
                  "properties": ["sortOrder"]
                }
              }
            }
            """.formatted(id, alias));
        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "renamed").serialize()).isEqualTo(id);
    }

    @Test
    void destroyShouldKeepDistinctErrorKeysForTokensThatParseToTheSameMailbox(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "INBOX")).serialize();

        Map<String, Object> result = given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [
                    ["Mailbox/set", {
                      "accountId": "%s",
                      "destroy": ["%s", "0%s"]
                    }, "destroyAliases"]
                  ]
                }
                """.formatted(bobAccountId(), id, id))
        .when().post()
        .then()
            .log().ifValidationFails()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract()
            .path("methodResponses.find { it[2] == 'destroyAliases' }[1].subMap(['destroyed', 'notDestroyed'])");

        assertThatJson(result).isEqualTo("""
            {
              "notDestroyed": {
                "%s": {"type": "invalidArguments", "description": "System mailboxes cannot be destroyed"},
                "0%s": {"type": "invalidArguments", "description": "System mailboxes cannot be destroyed"}
              }
            }
            """.formatted(id, id));

        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "INBOX").serialize()).isEqualTo(id);
    }

    @Test
    void failedDestroyShouldKeepEveryOriginalTokenAfterParsing(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "INBOX")).serialize();
        String alias = "0" + id;

        Map<String, Object> result = given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [
                    ["Mailbox/set", {
                      "accountId": "%s",
                      "destroy": ["%s", "%s"]
                    }, "destroySystemMailboxAliases"]
                  ]
                }
                """.formatted(bobAccountId(), id, alias))
        .when().post()
        .then()
            .log().ifValidationFails()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract()
            .path("methodResponses.find { it[2] == 'destroySystemMailboxAliases' }[1].subMap(['destroyed', 'notDestroyed'])");

        assertThatJson(result).isEqualTo("""
            {
              "notDestroyed": {
                "%s": {
                  "type": "invalidArguments",
                  "description": "System mailboxes cannot be destroyed"
                },
                "%s": {
                  "type": "invalidArguments",
                  "description": "System mailboxes cannot be destroyed"
                }
              }
            }
            """.formatted(id, alias));

        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "INBOX").serialize()).isEqualTo(id);
    }
}
