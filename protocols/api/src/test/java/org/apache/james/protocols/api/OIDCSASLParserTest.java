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

package org.apache.james.protocols.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.ImmutableList;

class OIDCSASLParserTest {
    static String TOKEN_WITHOUT_PREFIX = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ1RXNWV3hOckQxU3BJWUxZQzU5bWN0Q19ESHM1aFFfN0N1Tkd3YjJSWkVBIn0.eyJleHAiOjE2Mzk1NTUxMDgsImlhdCI6MTYzOTU1MTUwOCwiYXV0aF90aW1lIjoxNjM5NTUxNTA2LCJqdGkiOiIxYjVlMGRlMi04MjNhLTQxZDMtOTU2Mi0xMzk5N2U5YWNmMjYiLCJpc3MiOiJodHRwczovL2F1dGgudXBuLmludGVncmF0aW9uLW9wZW4tcGFhcy5vcmcvYXV0aC9yZWFsbXMvdXBuIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjIzZTBlZjg3LTZhYTMtNDdkYS1hY2NiLTI5YzU4OGQyYzFkOSIsInR5cCI6IkJlYXJlciIsImF6cCI6Im9wZW5wYWFzIiwic2Vzc2lvbl9zdGF0ZSI6ImQ3YzI5NjJmLTYyYmEtNDQ5YS04ZmFjLTI5YTU2ZGJmNjZiMCIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiKiJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiIsImRlZmF1bHQtcm9sZXMtdXBuIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwibmFtZSI6IkZpcnN0bmFtZTE0IFN1cm5hbWUxNCIsInByZWZlcnJlZF91c2VybmFtZSI6ImZpcnN0bmFtZTE0LnN1cm5hbWUxNCIsImdpdmVuX25hbWUiOiJGaXJzdG5hbWUxNCIsImZhbWlseV9uYW1lIjoiU3VybmFtZTE0IiwiZW1haWwiOiJmaXJzdG5hbWUxNC5zdXJuYW1lMTRAdXBuLmludGVncmF0aW9uLW9wZW4tcGFhcy5vcmcifQ.VdAgJ9OnLSlya0gC0AAoP23fv20Etoz73lAGcNNNj1OM5eLuenAM7m06uKKLTtX4pqbIYRS0JrMaSRWnxO-BWnJdE3KhF5SPGzuTZStLoMnQpOZMZK5qlejD7DaWky6CvyCcXPDmo8er7c_dk6OGKAXOPwWdLsyCBEFL3CE3wDegG3ZivTk-9kXcTAHx_-47MHOXca6zwyjrRW3g-bdhEzNqCEsgTs8wo3v9sIxFVfo_RFVVOzOG4QpVJOIFrTwgFv3FKiiJyr7YgvlpJTlPC2F-Bn0vtLeIu1iH5KLEhla3ospIjSeuseClqAE-_1ze7BUqJHyKUrpc2b5N84wzQA";
    static String BEARER_TOKEN = "Bearer " + TOKEN_WITHOUT_PREFIX;
    static char DELIMITER = 1;
    static String USER = "james@example.com";
    static String OAUTH_BEARER_PLAIN = String.join("" + DELIMITER,
        ImmutableList.of("n,user=" + USER, "auth=" + BEARER_TOKEN, "", ""));
    static String OAUTH_BEARER = Base64.getEncoder().encodeToString(OAUTH_BEARER_PLAIN.getBytes(StandardCharsets.US_ASCII));

    String buildOAuthBearerPlain(List<String> parts) {
        return Base64.getEncoder().encodeToString(String.join("" + DELIMITER, parts)
            .getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void parseShouldReturnResponseWhenValidInput() {
        Optional<OIDCSASLParser.OIDCInitialResponse> parseResult = OIDCSASLParser.parse(OAUTH_BEARER);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(parseResult.isPresent()).isTrue();
            softly.assertThat(parseResult.get().getToken()).isEqualTo(TOKEN_WITHOUT_PREFIX);
            softly.assertThat(parseResult.get().getAssociatedUser()).isEqualTo(USER);
        });
    }

    @Test
    void parseShouldReturnEmptyWhenMissingTokenPart() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,user=" + USER));
        assertThat(OIDCSASLParser.parse(input)).isEmpty();
    }

    @Test
    void parseShouldReturnEmptyWhenMissingUserPart() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,", "auth=" + BEARER_TOKEN));
        assertThat(OIDCSASLParser.parse(input)).isEmpty();
    }

    @Test
    void parseShouldReturnResponseWhenInputNotStartWithRightCharacters() {
        String input = buildOAuthBearerPlain(ImmutableList.of("user=" + USER,
            "auth=" + BEARER_TOKEN, "", ""));
        assertThat(OIDCSASLParser.parse(input)).isPresent();
    }

    @Test
    void parseShouldReturnResponseWhenInputHasDanglingPart() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,user=" + USER,
            "auth=" + BEARER_TOKEN,
            "host=linagora.com"));
        Optional<OIDCSASLParser.OIDCInitialResponse> parseResult = OIDCSASLParser.parse(input);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(parseResult.isPresent()).isTrue();
            softly.assertThat(parseResult.get().getToken()).isEqualTo(TOKEN_WITHOUT_PREFIX);
            softly.assertThat(parseResult.get().getAssociatedUser()).isEqualTo(USER);
        });
    }

    @Test
    void parseShouldReturnResponseWhenPartsDoNotSort() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,auth=" + BEARER_TOKEN,
            "user=" + USER,
            "host=linagora.com"));
        Optional<OIDCSASLParser.OIDCInitialResponse> parseResult = OIDCSASLParser.parse(input);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(parseResult.isPresent()).isTrue();
            softly.assertThat(parseResult.get().getToken()).isEqualTo(TOKEN_WITHOUT_PREFIX);
            softly.assertThat(parseResult.get().getAssociatedUser()).isEqualTo(USER);
        });
    }

    @Test
    void parseShouldReturnEmptyWhenInputHasSeveralAuthPart() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,auth=" + BEARER_TOKEN,
            "user=" + USER,
            "auth=token2"));
        assertThat(OIDCSASLParser.parse(input)).isEmpty();
    }

    @Test
    void parseShouldReturnEmptyWhenInputHasSeveralUserPart() {
        String input = buildOAuthBearerPlain(ImmutableList.of("n,user=" + USER,
            "user=" + USER,
            "auth=token2"));
        assertThat(OIDCSASLParser.parse(input)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"namespace",
        "@#$$@%#$",
        "",
        "1213"})
    void parseShouldReturnEmptyWhenInputHasInvalidFormat(String input) {
        assertThat(OIDCSASLParser.parse(input)).isEmpty();
    }
}
