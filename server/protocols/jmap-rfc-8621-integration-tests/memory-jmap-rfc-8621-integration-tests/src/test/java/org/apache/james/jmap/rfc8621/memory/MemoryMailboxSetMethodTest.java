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
    void updateShouldRejectTokensThatParseToTheSameMailbox(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "original")).serialize();
        String alias = "0" + id;
        String independent = probe.createMailbox(MailboxPath.forUser(bobUsername(), "independent")).serialize();

        Map<String, Object> result = given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [
                    ["Mailbox/set", {
                      "accountId": "%s",
                      "update": {
                        "%s": {"name": "first"},
                        "%s": {"name": "second"},
                        "%s": {"name": "success"}
                      }
                    }, "updateAliases"]
                  ]
                }
                """.formatted(bobAccountId(), id, alias, independent))
        .when().post()
        .then()
            .log().ifValidationFails()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract()
            .path("methodResponses.find { it[2] == 'updateAliases' }[1].subMap(['updated', 'notUpdated'])");

        assertThatJson(result).isEqualTo("""
            {
              "updated": {
                "%s": "${json-unit.ignore}"
              },
              "notUpdated": {
                "%s": {
                  "type": "invalidArguments",
                  "description": "Multiple update keys resolve to the same mailbox: '%s', '%s'"
                },
                "%s": {
                  "type": "invalidArguments",
                  "description": "Multiple update keys resolve to the same mailbox: '%s', '%s'"
                }
              }
            }
            """.formatted(independent, id, alias, id, alias, alias, id));

        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "original").serialize()).isEqualTo(id);
        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "success").serialize()).isEqualTo(independent);
        assertThat(probe.listUserMailboxes(bobUsername().asString())).doesNotContain("first", "second");
    }

    @Test
    void destroyShouldDeleteOnceForTokensThatParseToTheSameMailbox(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "deleted")).serialize();

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
              "destroyed": ["%s"]
            }
            """.formatted(id));

        assertThat(probe.listUserMailboxes(bobUsername().asString())).doesNotContain("deleted");
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
