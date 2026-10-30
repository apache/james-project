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
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

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

        given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {"using":["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
                 "methodCalls":[["Mailbox/set",{"accountId":"%s","update":{
                   "%s":{"name":"first"},"%s":{"name":"second"},"%s":{"name":"success"}
                 }},"set"]]}
                """.formatted(bobAccountId(), id, alias, independent))
        .when().post()
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].notUpdated.size()", equalTo(2))
            .body("methodResponses[0][1].notUpdated.'" + id + "'.type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].notUpdated.'" + alias + "'.type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].updated.size()", equalTo(1));

        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "original").serialize()).isEqualTo(id);
        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "success").serialize()).isEqualTo(independent);
        assertThat(probe.listUserMailboxes(bobUsername().asString())).doesNotContain("first", "second");
    }

    @Test
    void destroyShouldDeleteOnceForTokensThatParseToTheSameMailbox(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "deleted")).serialize();

        given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {"using":["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
                 "methodCalls":[["Mailbox/set",{"accountId":"%s","destroy":["%s","0%s"]},"set"]]}
                """.formatted(bobAccountId(), id, id))
        .when().post()
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].destroyed", equalTo(java.util.List.of(id)))
            .body("methodResponses[0][1].notDestroyed", nullValue());

        assertThat(probe.listUserMailboxes(bobUsername().asString())).doesNotContain("deleted");
    }

    @Test
    void failedDestroyShouldKeepEveryOriginalTokenAfterParsing(GuiceJamesServer server) {
        MailboxProbeImpl probe = server.getProbe(MailboxProbeImpl.class);
        String id = probe.createMailbox(MailboxPath.forUser(bobUsername(), "INBOX")).serialize();
        String alias = "0" + id;

        given()
            .header("Accept", ACCEPT_RFC8621_VERSION_HEADER())
            .body("""
                {"using":["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail"],
                 "methodCalls":[["Mailbox/set",{"accountId":"%s","destroy":["%s","%s"]},"set"]]}
                """.formatted(bobAccountId(), id, alias))
        .when().post()
        .then()
            .statusCode(200)
            .body("methodResponses[0][1].notDestroyed.size()", equalTo(2))
            .body("methodResponses[0][1].notDestroyed.'" + id + "'.type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].notDestroyed.'" + alias + "'.type", equalTo("invalidArguments"))
            .body("methodResponses[0][1].destroyed", nullValue());

        assertThat(probe.getMailboxId("#private", bobUsername().asString(), "INBOX").serialize()).isEqualTo(id);
    }
}
