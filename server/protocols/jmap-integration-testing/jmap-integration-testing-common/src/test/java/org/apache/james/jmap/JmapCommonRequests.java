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

import static com.jayway.restassured.RestAssured.with;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;

import java.util.List;
import java.util.Map;

import org.apache.james.jmap.api.access.AccessToken;
import org.apache.james.mailbox.Role;

import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.specification.ResponseSpecification;

public class JmapCommonRequests {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String NOT_UPDATED = ARGUMENTS + ".notUpdated";

    public static String getOutboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.OUTBOX);
    }
    public static String getDraftId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.DRAFTS);
    }

    public static String getMailboxId(AccessToken accessToken, Role role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(x -> x.get("role").equalsIgnoreCase(role.serialize()))
            .map(x -> x.get("id"))
            .findFirst().get();
    }

    public static List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
            .header("Authorization", accessToken.serialize())
            .body("[[\"getMailboxes\", {\"properties\": [\"role\", \"id\"]}, \"#0\"]]")
            .post("/jmap")
        .andReturn()
            .body()
            .jsonPath()
            .getList(ARGUMENTS + ".list");
    }

    public static boolean isAnyMessageFoundInRecipientsMailboxes(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.serialize())
                .body("[[\"getMessageList\", {}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messageList"))
                .body(ARGUMENTS + ".messageIds", hasSize(1));
            return true;

        } catch (AssertionError e) {
            return false;
        }
    }

    public static String getInboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.INBOX);
    }

    public static List<String> listMessageIdsForAccount(AccessToken accessToken) {
        return with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".messageIds");
    }

    public static String getLastMessageId(AccessToken accessToken) {
        return with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"sort\":[\"date desc\"]}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".messageIds[0]");
    }

    public static List<String> listMessageIdsInMailbox(AccessToken accessToken, String mailboxId) {
        return with()
                .header("Authorization", accessToken.serialize())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".messageIds");
    }

    public static ResponseSpecification getSetMessagesUpdateOKResponseAssertions(String messageId) {
        ResponseSpecBuilder builder = new ResponseSpecBuilder()
            .expectStatusCode(200)
            .expectBody(NAME, equalTo("messagesSet"))
            .expectBody(ARGUMENTS + ".updated", hasSize(1))
            .expectBody(ARGUMENTS + ".updated", contains(messageId))
            .expectBody(ARGUMENTS + ".error", isEmptyOrNullString())
            .expectBody(NOT_UPDATED, not(hasKey(messageId)));
        return builder.build();
    }
}
