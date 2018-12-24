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

package org.apache.james.event.json.dtos;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.event.json.DTOs;
import org.apache.james.event.json.JsonSerialize;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.Json;

class MailboxPathTest {
    private static final String MAILBOX_NAME = "mailboxName";
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME);
    private static final JsonSerialize JSON_SERIALIZE = new JsonSerialize(new TestId.Factory(), new TestMessageId.Factory());

    @Test
    void mailboxPathShouldBeWellSerialized() {
        assertThatJson(JSON_SERIALIZE.mailboxPathWrites().writes(DTOs.MailboxPath$.MODULE$.fromJava(MAILBOX_PATH)).toString())
            .isEqualTo(
                "{" +
                "  \"namespace\":\"#private\"," +
                "  \"user\":\"user\"," +
                "  \"name\":\"mailboxName\"" +
                "}");
    }

    @Test
    void mailboxPathWithNullUserShouldBeWellSerialized() {
        assertThatJson(JSON_SERIALIZE.mailboxPathWrites().writes(DTOs.MailboxPath$.MODULE$.fromJava(
            new MailboxPath(MailboxConstants.USER_NAMESPACE, null, MAILBOX_NAME))).toString())
            .isEqualTo(
                "{" +
                "  \"namespace\":\"#private\"," +
                "  \"name\":\"mailboxName\"" +
                "}");
    }

    @Test
    void mailboxPathWithEmptyNamespaceShouldBeWellSerialized() {
        assertThatJson(JSON_SERIALIZE.mailboxPathWrites().writes(DTOs.MailboxPath$.MODULE$.fromJava(
            new MailboxPath("", "user", MAILBOX_NAME))).toString())
            .isEqualTo(
                "{" +
                "  \"namespace\":\"#private\"," +
                "  \"user\":\"user\"," +
                "  \"name\":\"mailboxName\"" +
                "}");
    }

    @Test
    void mailboxPathWithShouldBeWellDeSerialized() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}")).get())
            .isEqualTo(DTOs.MailboxPath$.MODULE$.fromJava(
                new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME)));
    }

    @Test
    void mailboxPathWithNullUserShouldBeWellDeSerialized() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":null," +
            "  \"name\":\"mailboxName\"" +
            "}")).get())
            .isEqualTo(DTOs.MailboxPath$.MODULE$.fromJava(
                new MailboxPath(MailboxConstants.USER_NAMESPACE, null, MAILBOX_NAME)));
    }

    @Test
    void mailboxPathWithNoUserShouldBeWellDeSerialized() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"name\":\"mailboxName\"" +
            "}")).get())
            .isEqualTo(DTOs.MailboxPath$.MODULE$.fromJava(
                new MailboxPath(MailboxConstants.USER_NAMESPACE, null, MAILBOX_NAME)));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNoNamespace() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}"))
            .get().toJava())
            .isEqualTo(new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNullNamespace() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":null," +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}"))
            .get().toJava())
            .isEqualTo(new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", MAILBOX_NAME));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongNamespace() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":18," +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongUser() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":42," +
            "  \"name\":\"mailboxName\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenMissingMailboxName() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNullMailboxName() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":null" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongMailboxName() {
        assertThat(JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":42" +
            "}")))
            .isInstanceOf(JsError.class);
    }

}
