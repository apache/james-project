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

import static org.apache.james.event.json.SerializerFixture.DTO_JSON_SERIALIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import play.api.libs.json.Json;

class MessageMetaDataTest {
    @Nested
    class StructureTest {
        @Test
        void deserializeShouldThrowWhenNoFlags() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenNoUid() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenNoModSeq() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenNoSize() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenNoInternalDate() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenNoMessageId() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": 35," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class ModSeqTest {
        @Test
        void deserializeShouldThrowWhenNullModSeq() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": null," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenStringModSeq() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"modSeq\": \"42\"," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"size\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class SizeTest {
        @Test
        void deserializeShouldThrowWhenNullSize() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": null," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deserializeShouldThrowWhenStringSize() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": \"42\"," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class DeserializationErrorOnInternalDate {
        @Test
        void deSerializeShouldThrowWhenInternalDateIsNotInISOFormatBecauseOfMissingTWord() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14 12:52:36+07:00\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateContainsOnlyDate() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateIsMissingHourPart() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14TZ\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateIsMissingTimeZone() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51.541\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateIsMissingHours() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14Z\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateIsEmpty() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldThrowWhenInternalDateIsNull() {
            assertThatThrownBy(() -> DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": null," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get())
                .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void deSerializeShouldParseValidISOInstants() {
            assertThat(DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51+00:00\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get()
                    .internalDate())
                .isEqualTo(Instant.parse("2018-12-14T09:41:51Z"));
        }

        @Test
        void deSerializeShouldParseWhenInternalDateIsMissingMilliSeconds() {
            assertThat(DTO_JSON_SERIALIZE.messageMetaDataReads().reads(Json.parse("{" +
                    "        \"uid\": 123456," +
                    "        \"size\": 42," +
                    "        \"flags\": {" +
                    "          \"systemFlags\":[\"Answered\",\"Draft\"], " +
                    "          \"userFlags\":[\"User Custom Flag\"]}," +
                    "        \"modSeq\": 45,  " +
                    "        \"internalDate\": \"2018-12-14T09:41:51+00:00\"," +
                    "        \"messageId\": \"42\"" +
                    "}"))
                    .get()
                    .internalDate())
                .isEqualTo(Instant.parse("2018-12-14T09:41:51Z"));
        }
    }
}