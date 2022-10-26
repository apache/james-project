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

package org.apache.james.jmap.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import nl.jqno.equalsverifier.EqualsVerifier;

class PreviewTest {

    private static final String PREVIEW_RAW_VALUE = "Hello James!";
    private static final Preview EMPTY_STRING_PREVIEW = Preview.from("");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Preview.class)
            .verify();
    }

    @Test
    void getValueShouldReturnCorrectPreviewString() {
        assertThat(new Preview(PREVIEW_RAW_VALUE).getValue())
            .isEqualTo(PREVIEW_RAW_VALUE);
    }

    @Test
    void fromShouldReturnACorrectPreview() {
        assertThat(Preview.from(PREVIEW_RAW_VALUE))
            .isEqualTo(new Preview(PREVIEW_RAW_VALUE));
    }

    @Test
    void fromShouldThrowWhenNullValue() {
        assertThatThrownBy(() -> Preview.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenValueLengthIsLongerThanMaximum256() {
        String errorMessageRegex = "the preview value '.*' has length longer than 256";
        assertThatThrownBy(() -> Preview.from(Strings.repeat("a", 257)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageMatching(errorMessageRegex);
    }

    @Test
    void fromShouldNotThrowWhenValueLengthIsEqualsToMaximum256() {
        assertThatCode(() -> Preview.from(Strings.repeat("a", 256)))
            .doesNotThrowAnyException();
    }

    @Nested
    class ComputeTest {
        
        @Test
        void computeShouldReturnEmptyStringPreviewWhenStringEmptyTextBody() throws Exception {
            assertThat(Preview.compute(""))
                .isEqualTo(EMPTY_STRING_PREVIEW);
        }

        @Test
        void computeShouldReturnEmptyStringPreviewWhenOnlySpaceTabAndBreakLines() throws Exception {
            assertThat(Preview.compute(" \n\t "))
                .isEqualTo(EMPTY_STRING_PREVIEW);
        }

        @Test
        void computeShouldReturnEmptyStringPreviewWhenOnlySpace() throws Exception {
            assertThat(Preview.compute(" "))
                .isEqualTo(EMPTY_STRING_PREVIEW);
        }

        @Test
        void computeShouldReturnEmptyStringPreviewWhenOnlyTab() throws Exception {
            assertThat(Preview.compute("\t"))
                .isEqualTo(EMPTY_STRING_PREVIEW);
        }

        @Test
        void computeShouldReturnEmptyStringPreviewWhenOnlyBreakLines() throws Exception {
            assertThat(Preview.compute("\n"))
                .isEqualTo(EMPTY_STRING_PREVIEW);
        }

        @Test
        void computeShouldReturnStringWithoutTruncation() throws Exception {
            String body = StringUtils.leftPad("a", 100, "b");

            assertThat(Preview.compute(body)
                    .getValue())
                .hasSize(100)
                .isEqualTo(body);
        }

        @Test
        void computeShouldReturnStringIsLimitedTo256Length() throws Exception {
            String body = StringUtils.leftPad("a", 300, "b");
            String expected = StringUtils.leftPad("b", 256, "b");

            assertThat(Preview.compute(body)
                    .getValue())
                .hasSize(256)
                .isEqualTo(expected);
        }

        @Test
        void computeShouldReturnNormalizeSpaceString() throws Exception {
            String body = "    this      is\n      the\r           preview\t         content\n\n         ";

            assertThat(Preview.compute(body))
                .isEqualTo(Preview.from("this is the preview content"));
        }
    }

    @Test
    void computeShouldSanitizeBadUtf8Splits() throws Exception {
        // This value would lead to a split in the middle of an emoji thus leading to an invalid UTF-8 string

        String b64 = "DQoNCiAgICANCiAgDQogICAgDQogICAgICANCiAgICAgICAgDQogICAgICAgICAg" +
            "DQogICAgICAgICAgICANCiAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgDQogICAgICAgICAgICAgICAgICANCiAgICAgICAgICA" +
            "gICAgICAgICAgDQogICAgICAgICAgICAgICAgICAgICAgDQogIA0KICBQcm9kdWN0IEh1bnQNCg0KDQogICAgICAgICAgICAgICAgICAgIA" +
            "0KICAgICAgICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICAgICAgICBGcmlkYXksIE9jdG9iZXIgMjFzdA0KICAgICAgICAgICAgI" +
            "CAgICAgICANCiAgICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICAgIA0KICAgICAgICAgICAgICANCiAgICAgICAgICAgIA0KICAg" +
            "ICAgICAgICAgDQogICAgICAgICAgICAgIA0KICAgICAgICAgICAgDQogICAgICAgICAgICANCiAgICAgICAgICAgICAgDQogICAgICAgICA" +
            "gICANCiAgICAgICAgICAgIA0KICAgICAgICAgICAgICANCiAgICAgICAgICAgICAgICANCiAgDQogIA0KICAgIA0KICAgICAgDQogICAgIC" +
            "AgIA0KICAgIEhleSB0aGVyZSwgQWxleGFuZHJlIQoKDQoNCiAgICBEaGVlcmFqIFBhbmRleSBqdXN0IHBvc3RlZCBEZXZSZXYgU3VwcG9yd" +
            "CAtIEluLWFwcCBzdXBwb3J0IGZvciBQTEcgY29tcGFuaWVzDQoNCiAgICAKCg0KDQogICAgICANCiAgICAgICAgDQogIA0KICAgIA0KICAg" +
            "ICAgDQogICAgICAgIA0KICAgICAgICAgIA0KICAgICAgICANCiAgICAgICAgDQogICAgICAgICAgRGV2UmV2IFN1cHBvcnQNCiAgICAgICA" +
            "gDQogICAgICANCiAgICAgIA0KICAgICAgICANCiAgICAgICAgICBJbi1hcHAgc3VwcG9ydCBmb3IgUExHIGNvbXBhbmllcw0KICAgICAgIC" +
            "ANCiAgICAgIA0KICAgIA0KICANCg0KDQoNCiAgICAgICAgICANCiAgICAgICAgICAgIA0KICAgICAgICAgICAgICAKDQogICAgICAgICAgI" +
            "CAgIEhlcmUgaXMgd2hhdCB0aGV5IHNhaWQgYWJvdXQgaXQ6DQogICAgICAgICAgICANCiAgICAgICAgICANCiAgICAgICAgICANCiAgDQogI" +
            "CAgDQogICAgICANCiAgICAgICAgDQogICAgICAgICAgW0RoZWVyYWogUGFuZGV5XQ0KICAgICAgICANCiAgICAgICAgDQogICAgICAgICAg" +
            "DQogICAgICAgICAgICAiSGV5IE1ha2VycyBhbmQgSHVudGVycyDwn5GLLAoKDQoNCkknbSBEaGVlcmFqIFBhbmRleSwgQ28tZm91bmRlciw" +
            "gYW5kIENFTyBhdCBEZXZSZXYuIFRvZGF5IGlzIGEgYmlnIGRheSBmb3IgdXMsIHdlJ3JlIHN1cGVyIGV4Y2l0ZWQgdG8gcHJlc2VudCB5b3" +
            "UgdGhlIERldlJldiBQcm9kdWN0LUxlZCBTdXBwb3J0IGFwcCBvbiBvdXIgRGV2Q1JNIHBsYXRmb3JtLCB3aXRoIHRoZSBnb2FsIG9mIHVuaW" +
            "Z5aW5nIHlvdXIgY29tcGFueSdzIGZyb250IGFuZCBiYWNrIG9mZmljZSBieSBtYWtpbmcgZXZlcnkgZW1wbG95ZWUgdGhpbmsgYWJvdXQgdGh" +
            "lIHByb2R1Y3QgYW5kIHRoZSBlbmQgdXNlcnMuCgoNCg0KSHVnZSB0aGFua3MgdG8gS2V2aW4gZm9yIGh1bnRpbmcgdXMg8J+ZjwoKDQoNCld" +
            "lJ3ZlIGNvbWUgYSBsb25nIHdheSBzaW5jZSBvdXIgbGFzdCBsYXVuY2gsIGFuZCB3ZSdyZSBoYXBweSB0byBhbm5vdW5jZSB0aGF0IHdlIG" +
            "FyZSBub3cgb2ZmaWNpYWxseSBvdXQgb2YgYmV0YSEgSXQncyBiZWVuIGEgbG9uZyBqb3VybmV5LCBidXQgd2UgY291bGRuJ3QgaGF2ZSBkb" +
            "25lIGl0IHdpdGhvdXQgdGhlIGJldGEgY3VzdG9tZXJzIHdobyB1c2VkIERldlJldiBhbmQgZ2F2ZSB1cyB0aGVpciB2YWx1YWJsZSBmZWVk" +
            "YmFjayBhbG9uZyB0aGUgd2F5LiBUaGFuayB5b3UgdG8gZXZlcnlvbmUgd2hvIGhlbHBlZCB1cyBpbXByb3ZlIG91ciBwcm9kdWN0IC0gd2U" +
            "gdHJ1bHkgY291bGRuJ3QgaGF2ZSBkb25lIGl0IHdpdGhvdXQgeW91LgoKDQoNClNvLCB3aGF0IGlzIERldlJldj8KCg0KDQpEZXZSZXYgaXM" +
            "gdGhlIHdvcmxkJ3MgZmlyc3QgRGV2Q1JNIGZvciBuZXctYWdlIHByb2R1Y3QtbGVkIGNvbXBhbmllcyBhbmQgc3RhcnR1cHMgd2l0aCBhIH" +
            "Zpc2lvbiB0byBicmluZyB0b2dldGhlciB0aGUgRGV2KERldmVsb3BlcnMpIGFuZCBSZXYgKEN1c3RvbWVycykgYW5kIGhlbHAgbWFrZSB5" +
            "b3VyIGNvbXBhbnkgbW9yZSBjdXN0b21lciBhbmQgcHJvZHVjdC1jZW50cmljLgoKDQoNCkRldlJldiBTdXBwb3J0IGFwcCBpcyB0aGUgZml" +
            "yc3QgYXBwIG9uIHRoZSBEZXZDUk0gcGxhdGZvcm0uIFRoZSBhcHAncyBnb2FsIGlzIHRvIGVtcG93ZXIgeW91ciBlbnRpcmUgb3JnYW5pem" +
            "F0aW9uIHRvIGVuZ2FnZSB5b3VyIGN1c3RvbWVyLCBhdXRvbWF0ZSBwcm9jZXNzIGFuZCBjb252ZXJ0IHByb2R1Y3QgZmVlZGJhY2sgaW50b" +
            "yBkZWxpZ2h0ZnVsIGV4cGVyaWVuY2UuCgoNCg0KSXQgY29udmVyZ2VzIGN1c3RvbWVyIGNvbnZlcnNhdGlvbnMgYW5kIGludGVyYWN0aW9u" +
            "cyBkaXJlY3RseSB0byB5b3VyIGRldmVsb3BlcidzIHdvcmsgYW5kIGtlZXBzIGV2ZXJ5dGhpbmcgaW4gc3luYyBhbmQgcmVhbC10aW1lIGZ" +
            "vciB5b3VyIGVuZCB1c2Vycy4KCg0KDQpUaGUgY29yZSBmZWF0dXJlcyB0aGF0IG1ha2UgRGV2UmV2IFN1cHBvcnQgZGlmZmVyZW50IGZyb2" +
            "0gb3RoZXIgcGxhdGZvcm1zOgoKDQoNClBMdUc6IFRoZSBwcm9kdWN0LWxlZCBjaGFubmVsIHRvIHRhbGsgdG8geW91ciB1c2Vycy4gQ2hhd" +
            "CwgcmVjb21tZW5kLCBhbmQgbnVkZ2UgeW91ciBjdXN0b21lcnMgdG8gZHJpdmUgYWRvcHRpb24gYW5kIGRlbGlnaHQuCgoNCg0KQ3VzdG9t" +
            "ZXIgSW5ib3g6IEEgYmktZGlyZWN0aW9uYWwsIHN5bmNocm9ub3VzIHZpZXcgaW50byByZWFsLXRpbWUgY3VzdG9tZXIgY29udmVyc2F0aW9" +
            "ucyBhY3Jvc3MgUEx1RywgU2xhY2ssIGFuZCBlbWFpbCB0byBlbnN1cmUgeW91IG5ldmVyIGxlYXZlIGEgY3VzdG9tZXIgaGFuZ2luZy4KCg" +
            "0KDQpUaWNrZXQgTWFuYWdlbWVudDogQSBtb2Rlcm4gdGlja2V0IG1hbmFnZW1lbnQgcGxhdGZvcm0gZW5yaWNoZWQgYnkgQ1JNIGRhdGEgY" +
            "W5kIGNvbm5lY3RlZCB0byBkZXZlbG9wZXIgaXNzdWVzIHRvIGJyaW5nIHByb2R1Y3QgYW5kIGN1c3RvbWVyIGNlbnRyaWNpdHkgdG8gZXZl" +
            "cnkgdGVhbSBtZW1iZXIuCgoNCg0KUmVhbHRpbWUgVXBkYXRlcyBmb3IgeW91ciB1c2VyczogTm93IGtlZXAgeW91ciB1c2VycyBhbHdheXM" +
            "gaW4gdGhlIGxvb3AsIHdpdGggdGhlIHJlYWwtdGltZSBzdGF0dXMgb2YgdGhlaXIgdGlja2V0cywgYWxsIHRocm91Z2ggdGhlIFBMdUcgd2" +
            "lkZ2V0LiAKCg0KDQpJbnRlZ3JhdGUgd2l0aCB5b3VyIGZhdm9yaXRlIGFwcHM6IEludGVncmF0ZSB5b3VyIGZhdm9yaXRlIGFwcHMgbGlrZS" +
            "BHaXRodWIsIEJpdGJ1Y2tldCwgSmlyYSwgU2xhY2ssIExpbmVhciwgYW5kIG1hbnkgbW9yZS4gCgoNCg0KSW50ZXJhY3Qgd2l0aCBEZXZSZX" +
            "YgZnJvbSBTbGFjazogTmV2ZXIgbGVhdmUgeW91ciBjdXN0b21lcnMgaW4gdGhlIGRhcmshIENyZWF0ZSBEZXZSZXYgdGlja2V0cyBhbmQga2" +
            "VlcCBjdXN0b21lcnMgdXAgdG8gZGF0ZSB3aXRob3V0IGV2ZXIgbGVhdmluZyBTbGFjay4gQ3JlYXRlIERldlJldiB0aWNrZXRzIG9yIGlzc3" +
            "VlcyBmcm9tIFNsYWNrIGNvbnZlcnNhdGlvbnMgYW5kIHN0YXkgdXAgdG8gZGF0ZS4KCg0KDQpLbm93bGVkZ2UtYmFzZWQgYXJ0aWNsZXMgYW5k" +
            "IHRoZWlyIG1hbmFnZW1lbnQ6IEFsb25nIHdpdGggYWRkaW5nIGFuZCBtYW5hZ2luZyBLQiBhcnRpY2xlcyBieSBEZXZPcmcsIHVzZXJzIHdpbGw" +
            "gbm93IGhhdmUgdGhlIGFiaWxpdHkgdG8gc2VhcmNoIHRoZW0gdGhyb3VnaCBQTHVHIHdpZGdldC4KCg0KDQpBdXRvbWF0aWMgR2l0SHViIFVwZ" +
            "GF0ZXM6IFB1dCB5b3VyIHdvcmsgaW4gYXV0b3BpbG90IHdpdGggR2l0aHViIGFuZCBEZXZSZXYgYW5kIG1ha2UgdXBkYXRlcyBpbiByZWFsLXRp" +
            "bWUsIGFuZCBtYWtlIHlvdXIgZGV2ZWxvcGVyIGZyZWUgZnJvbSBkb2luZyBzdGF0dXMgdXBkYXRlcy4KCg0KDQpOZXZlciBtaXNzIHlvdXIgbWV" +
            "zc2FnZXM6IEVtYWlsIGludGVncmF0aW9ucyB0byBrZWVwIHlvdSBub3RpZmllZCAyNHg3LgoKDQoNCkxpZ2h0IG1vZGU6IFdlIGFyZSBhbHNvIG" +
            "dvaW5nIHRvIHJlbGVhc2Ugb3VyIGFsbCAgbmV3IGxpZ2h0IG1vZGUgdGhlbWUgc2hvcnRseSwgYW5kIHdlIHRoaW5rIHlvdSdyZSBnb2luZyB0by" +
            "Bsb3ZlIGl0ISBUaGlzIHdhcyBvbmUgb2YgdGhlIG1vc3QgcmVxdWVzdGVkIGZlYXR1cmVzLCBhbmQgd2UncmUgc28gaGFwcHkgdG8gYmUgYWJsZ" +
            "SB0byBvZmZlciBpdCB0byBvdXIgdXNlcnMgZmluYWxseS4gR2l2ZSBEZXZSZXYgYSB0cnkgdG9kYXksIGFuZCBsZXQgdXMga25vdyB3aGF0IHl" +
            "vdSB0aGluayEKCg0KDQpDaGVlcnMg8J+NuwoKDQoNClRlYW0gRGV2UmV2IgoKDQogICAgICAgICAgDQogICAgICAgIA0KICAgICAgDQogICAgD" +
            "QogIA0KDQoNCg0KICAgICAgICANCiAgDQogICAgDQogICAgICBWaWV3IG9uIFByb2R1Y3QgSHVudA0KICAgIA0KICANCg0KDQogICAgICANCiA" +
            "gICANCg0KICAgICAgDQogICAgDQogIA0KDQogIA0KICAgICAgICAgICAgICAgIA0KICANCiAgICAgICAgICAgICAgICAgICAgDQogICAgWW91I" +
            "GNhbiBvcHQgb3V0IG9mIGZyaWVuZCBwcm9kdWN0IHBvc3Qgbm90aWZpY2F0aW9ucyBvciBtYW5hZ2UgYWxsIG9mIHlvdXIgZW1haWwgbm90aWZ" +
            "pY2F0aW9ucyBmcm9tIHlvdXIgcHJvZmlsZS4gT3IganVzdCB1bmZvbGxvdyBEaGVlcmFqIFBhbmRleQoKDQoNCiAgICAgICAgICAgICAgICAgI" +
            "ElmIHlvdSBoYXZlIGFueSBxdWVzdGlvbnMsIGZlZWRiYWNrLCBpZGVhcyBvciBwcm9ibGVtcyBkb24ndCBoZXNpdGF0ZSB0byBjb250YWN0IHVzI" +
            "QoKDQogICAgICAgICAgICAgICAgICBQcm9kdWN0IEh1bnQgSW5jLiwgOTAgR29sZCBTdCwgRkxSIDMsIFNhbiBGcmFuY2lzY28sIENBIDk0MTM" +
            "zCgoNCg0KDQogICAgICAgICAgICAgIA0KICAgICAgICAgICAgDQogICAgICAgICAgDQogICAgICAgIA0KICAgICAgDQogICAgDQogIA0KDQoNC" +
            "g0KICANCgoNCg==";
        String textBody = new String(Base64.getDecoder().decode(b64.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        Preview preview = Preview.compute(textBody);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .canEncode(preview.getValue())).isTrue();
            softly.assertThat(preview).isEqualTo(Preview.from("Product Hunt Friday, October 21st Hey there, Alexandre! " +
                "Dheeraj Pandey just posted DevRev Support - In-app support for PLG companies DevRev Support In-app " +
                "support for PLG companies Here is what they said about it: [Dheeraj Pandey] \"Hey Makers and Hunters "));
        });
    }
}