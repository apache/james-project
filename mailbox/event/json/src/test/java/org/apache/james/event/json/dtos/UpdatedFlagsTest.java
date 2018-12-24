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

import org.junit.jupiter.api.Test;

import play.api.libs.json.JsError;
import play.api.libs.json.Json;

class UpdatedFlagsTest {
    @Test
    void flagsUpdatedShouldThrowWhenMoqSeqIsAString() {
        assertThat(DTO_JSON_SERIALIZE.updatedFlagsReads().reads(Json.parse(
            "      {" +
                "        \"uid\": 123456," +
                "        \"modSeq\": \"35\"," +
                "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                "      }")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void flagsUpdatedShouldThrowWhenMoqSeqIsNull() {
        assertThat(DTO_JSON_SERIALIZE.updatedFlagsReads().reads(Json.parse(
            "      {" +
                "        \"uid\": 123456," +
                "        \"modSeq\": null," +
                "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                "      }")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void flagsUpdatedShouldThrowWhenMoqSeqIsNotAnInteger() {
        assertThat(DTO_JSON_SERIALIZE.updatedFlagsReads().reads(Json.parse(
            "      {" +
                "        \"uid\": 123456," +
                "        \"modSeq\": 35.2567454," +
                "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                "      }")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void flagsUpdatedShouldThrowWhenOldFlagIsMissing() {
        assertThat(DTO_JSON_SERIALIZE.updatedFlagsReads().reads(Json.parse(
            "      {" +
                "        \"uid\": 123456," +
                "        \"modSeq\": 35," +
                "        \"newFlags\": {\"systemFlags\":[\"Answered\",\"Draft\"],\"userFlags\":[\"New Flag 1\"]}" +
                "      }")))
            .isInstanceOf(JsError.class);
    }

    @Test
    void flagsUpdatedShouldThrowWhenNewFlagIsMissing() {
        assertThat(DTO_JSON_SERIALIZE.updatedFlagsReads().reads(Json.parse(
            "      {" +
                "        \"uid\": 123456," +
                "        \"modSeq\": 35," +
                "        \"oldFlags\": {\"systemFlags\":[\"Deleted\",\"Seen\"],\"userFlags\":[\"Old Flag 1\"]}" +
                "      }")))
            .isInstanceOf(JsError.class);
    }
}
