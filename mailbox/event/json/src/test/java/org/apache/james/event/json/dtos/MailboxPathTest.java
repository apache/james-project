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
import static org.apache.james.event.json.SerializerFixture.DTO_JSON_SERIALIZE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.event.json.DTOs;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.Json;

class MailboxPathTest {
    private static final String MAILBOX_NAME = "mailboxName";
    public static final Username USER = Username.of("user");
    private static final MailboxPath MAILBOX_PATH = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, MAILBOX_NAME);

    @Test
    void mailboxPathShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.mailboxPathWrites().writes(DTOs.MailboxPath$.MODULE$.fromJava(MAILBOX_PATH)).toString())
            .isEqualTo(
                "{" +
                    "  \"namespace\":\"#private\"," +
                    "  \"user\":\"user\"," +
                    "  \"name\":\"mailboxName\"" +
                    "}");
    }

    @Test
    void mailboxPathWithEmptyNamespaceShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.mailboxPathWrites().writes(DTOs.MailboxPath$.MODULE$.fromJava(
            new MailboxPath("", USER, MAILBOX_NAME))).toString())
            .isEqualTo(
                "{" +
                    "  \"namespace\":\"#private\"," +
                    "  \"user\":\"user\"," +
                    "  \"name\":\"mailboxName\"" +
                    "}");
    }

    @Test
    void mailboxPathWithShouldBeWellDeSerialized() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}")).get())
            .isEqualTo(DTOs.MailboxPath$.MODULE$.fromJava(
                new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, MAILBOX_NAME)));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNoNamespace() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
                "  \"user\":\"user\"," +
                "  \"name\":\"mailboxName\"" +
                "}"))
            .get().toJava())
            .isEqualTo(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, MAILBOX_NAME));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNullNamespace() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
                "  \"namespace\":null," +
                "  \"user\":\"user\"," +
                "  \"name\":\"mailboxName\"" +
                "}"))
            .get().toJava())
            .isEqualTo(new MailboxPath(MailboxConstants.USER_NAMESPACE, USER, MAILBOX_NAME));
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongNamespace() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":18," +
            "  \"user\":\"user\"," +
            "  \"name\":\"mailboxName\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongUser() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":42," +
            "  \"name\":\"mailboxName\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenMissingMailboxName() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenNullMailboxName() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":null" +
            "}")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void mailboxPathDeserializationShouldFailWhenLongMailboxName() {
        assertThat(DTO_JSON_SERIALIZE.mailboxPathReads().reads(Json.parse("{" +
            "  \"namespace\":\"#private\"," +
            "  \"user\":\"user\"," +
            "  \"name\":42" +
            "}")))
            .isInstanceOf(JsError.class);
    }

}
