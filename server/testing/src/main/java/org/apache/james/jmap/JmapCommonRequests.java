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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.model.MailboxId;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.ResponseSpecification;

public class JmapCommonRequests {
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String NOT_UPDATED = ARGUMENTS + ".notUpdated";

    public static String getOutboxId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.OUTBOX);
    }

    public static String getSentId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.SENT);
    }

    public static String getDraftId(AccessToken accessToken) {
        return getMailboxId(accessToken, Role.DRAFTS);
    }

    public static String getMailboxId(AccessToken accessToken, Role role) {
        return getAllMailboxesIds(accessToken).stream()
            .filter(mailbox -> mailbox.get("role").equals(role.serialize()))
            .map(mailbox -> mailbox.get("id"))
            .findFirst().get();
    }

    public static List<Map<String, String>> getAllMailboxesIds(AccessToken accessToken) {
        return with()
            .header("Authorization", accessToken.asString())
            .body("[[\"getMailboxes\", {\"properties\": [\"role\", \"name\", \"id\"]}, \"#0\"]]")
            .post("/jmap")
        .andReturn()
            .body()
            .jsonPath()
            .getList(ARGUMENTS + ".list");
    }

    public static boolean isAnyMessageFoundInRecipientsMailboxes(AccessToken recipientToken) {
        try {
            with()
                .header("Authorization", recipientToken.asString())
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

    public static boolean isAnyMessageFoundInRecipientsMailbox(AccessToken recipientToken, MailboxId mailboxId) {
        return isAnyMessageFoundInRecipientsMailbox(recipientToken, mailboxId.serialize());
    }

    public static boolean isAnyMessageFoundInRecipientsMailbox(AccessToken recipientToken, String serialize) {
        try {
            with()
                .header("Authorization", recipientToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + serialize + "\"]}}, \"#0\"]]")
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
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".messageIds");
    }

    public static String getLastMessageId(AccessToken accessToken) {
        return with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"sort\":[\"date desc\"]}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .body()
                .path(ARGUMENTS + ".messageIds[0]");
    }

    public static String getLatestMessageId(AccessToken accessToken, Role mailbox) {
        String mailboxId = getMailboxId(accessToken, mailbox);
        return with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId + "\"]}, \"sort\":[\"date desc\"]}, \"#0\"]]")
                .post("/jmap")
            .then()
                .extract()
                .path(ARGUMENTS + ".messageIds[0]");
    }

    public static String bodyOfMessage(AccessToken accessToken, String messageId) {
        return getMessageContent(accessToken, messageId)
                .get(ARGUMENTS + ".list[0].textBody");
    }

    public static List<String> receiversOfMessage(AccessToken accessToken, String messageId) {
        return getMessageContent(accessToken, messageId)
                .getList(ARGUMENTS + ".list[0].to.email");
    }

    private static JsonPath getMessageContent(AccessToken accessToken, String messageId) {
        return with()
                .header("Authorization", accessToken.asString())
                .body("[[\"getMessages\", {\"ids\": [\"" + messageId + "\"]}, \"#0\"]]")
            .when()
                .post("/jmap")
            .then()
                .statusCode(200)
                .body(NAME, equalTo("messages"))
                .body(ARGUMENTS + ".list", hasSize(1))
            .extract()
                .jsonPath();
    }

    public static List<String> listMessageIdsInMailbox(AccessToken accessToken, String mailboxId) {
        return with()
                .header("Authorization", accessToken.asString())
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
            .expectBody(ARGUMENTS + ".error", is(emptyOrNullString()))
            .expectBody(NOT_UPDATED, not(hasKey(messageId)));
        return builder.build();
    }

    public static void deleteMessages(AccessToken accessToken, List<String> idsToDestroy) {
        String idString = concatMessageIds(idsToDestroy);

        with()
            .header("Authorization", accessToken.asString())
            .body("[[\"setMessages\", {\"destroy\": [" + idString + "]}, \"#0\"]]")
            .post("/jmap");
    }

    public static String concatMessageIds(List<String> ids) {
        return ids.stream()
            .map(id -> "\"" + id + "\"")
            .collect(Collectors.joining(","));
    }
}
