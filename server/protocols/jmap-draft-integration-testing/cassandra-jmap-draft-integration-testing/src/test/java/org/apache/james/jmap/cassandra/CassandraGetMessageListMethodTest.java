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

package org.apache.james.jmap.cassandra;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.TestingConstants.ALICE;

import java.io.IOException;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.CassandraJmapTestRule;
import org.apache.james.DockerCassandraRule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.draft.methods.integration.GetMessageListMethodTest;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class CassandraGetMessageListMethodTest extends GetMessageListMethodTest {

    @Rule
    public DockerCassandraRule cassandra = new DockerCassandraRule();

    @Rule 
    public CassandraJmapTestRule rule = CassandraJmapTestRule.defaultTestRule();

    @Override
    protected GuiceJamesServer createJmapServer() throws IOException {
        return rule.jmapServer(cassandra.getModule(),
            new TestJMAPServerModule(LIMIT_TO_3_MESSAGES));
    }

    @Override
    protected void await() {
        rule.await();
    }

    @Ignore("Demonstrate James respond 400 upon unavailable ElasticSearch")
    @Test
    public void getMessageListShouldReturn503WhenElasticSearchIsDown() throws Exception {
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, ALICE.asString(), "mailbox");

        mailboxProbe.appendMessage(ALICE.asString(), ALICE_MAILBOX,
            ClassLoader.getSystemResourceAsStream("eml/twoAttachments.eml"), new Date(), false, new Flags(Flags.Flag.FLAGGED));

        await();

        rule.getDockerElasticSearchRule().getDockerEs().pause();

        try {
            given()
                .header("Authorization", aliceAccessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"operator\":\"AND\",\"conditions\":[{\"isFlagged\":\"true\"},{\"isUnread\":\"true\"}]}}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(503);
        } finally {
            rule.getDockerElasticSearchRule().getDockerEs().unpause();
        }
    }
}
