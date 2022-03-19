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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import jakarta.mail.Flags;

import org.apache.james.event.json.DTOs;
import org.junit.jupiter.api.Test;

import play.api.libs.json.Json;

class FlagsTest {
    @Test
    void emptyFlagsShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags()))
            .toString())
            .isEqualTo("{\"systemFlags\":[],\"userFlags\":[]}");
    }

    @Test
    void answeredShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.ANSWERED)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Answered\"],\"userFlags\":[]}");
    }

    @Test
    void deletedShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.DELETED)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Deleted\"],\"userFlags\":[]}");
    }

    @Test
    void draftShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.DRAFT)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Draft\"],\"userFlags\":[]}");
    }

    @Test
    void flaggedShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.FLAGGED)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Flagged\"],\"userFlags\":[]}");
    }

    @Test
    void recentShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.RECENT)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Recent\"],\"userFlags\":[]}");
    }

    @Test
    void seenShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags(Flags.Flag.SEEN)))
            .toString())
            .isEqualTo("{\"systemFlags\":[\"Seen\"],\"userFlags\":[]}");
    }

    @Test
    void userFlagShouldBeWellSerialized() {
        assertThatJson(DTO_JSON_SERIALIZE.flagWrites().writes(DTOs.Flags$.MODULE$.fromJavaFlags(
            new Flags("user flag")))
            .toString())
            .isEqualTo("{\"systemFlags\":[],\"userFlags\":[\"user flag\"]}");
    }

    @Test
    void emptyFlagsShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags());
    }

    @Test
    void answeredShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Answered\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.ANSWERED));
    }

    @Test
    void deletedShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Deleted\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.DELETED));
    }

    @Test
    void draftShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Draft\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.DRAFT));
    }

    @Test
    void flaggedShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Flagged\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.FLAGGED));
    }

    @Test
    void recentShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Recent\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.RECENT));
    }

    @Test
    void seenShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"Seen\"],\"userFlags\":[]}"))
                .get()))
            .isEqualTo(new Flags(Flags.Flag.SEEN));
    }

    @Test
    void userFlagShouldBeWellDeSerialized() {
        assertThat(DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[],\"userFlags\":[\"user flag\"]}"))
                .get()))
            .isEqualTo(new Flags("user flag"));
    }

    @Test
    void deserializeShouldThrowWhenUnknownSystemFlag() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"unknown\"],\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenBadCaseSystemFlag() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[\"AnSwErEd\"],\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenNullSystemFlag() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":null,\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenSystemFlagContainsNullElements() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[null,\"Draft\"],\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenSystemFlagContainsNotStringElements() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[42,\"Draft\"],\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenUserFlagsContainsNullElements() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[],\"userFlags\":[null, \"a\"]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenUserFlagsContainsNonStringElements() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[],\"userFlags\":[42, \"a\"]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenNoUserFlags() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"systemFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deserializeShouldThrowWhenNoSystemFlags() {
        assertThatThrownBy(() -> DTOs.Flags$.MODULE$.toJavaFlags(
            DTO_JSON_SERIALIZE.flagsReads().reads(Json.parse("{\"userFlags\":[]}"))
                .get()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
