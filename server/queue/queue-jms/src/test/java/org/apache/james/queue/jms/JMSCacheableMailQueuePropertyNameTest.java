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

package org.apache.james.queue.jms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JMSCacheableMailQueuePropertyNameTest {

    private static final String ATTRIBUTE_PREFIX = "JAMES_ATTR_";
    private static final String PER_RECIPIENT_PREFIX = JMSCacheableMailQueue.JAMES_MAIL_PER_RECIPIENT_HEADERS + "_";

    @Nested
    class AttributePropertyNameEncoding {

        @Test
        void shouldPrependAttributePrefix() {
            assertThat(JMSCacheableMailQueue.encodeAttributePropertyName("simple"))
                .isEqualTo(ATTRIBUTE_PREFIX + "simple");
        }

        @Test
        void shouldPreserveValidJavaIdentifierParts() {
            String input = "simple_attribute_123$name";
            assertThat(JMSCacheableMailQueue.encodeAttributePropertyName(input))
                .isEqualTo(ATTRIBUTE_PREFIX + input);
        }

        @Test
        void shouldEncodeDotsInAttributeNames() {
            // Dots are common in hierarchical James attribute names e.g. org.apache.james.attribute
            String encoded = JMSCacheableMailQueue.encodeAttributePropertyName("org.apache.james");
            assertThat(encoded)
                .doesNotContain(".")
                .isEqualTo(ATTRIBUTE_PREFIX + "org_002e_apache_002e_james");
        }

        @Test
        void shouldEncodeHyphensAndSpacesInAttributeNames() {
            String encoded = JMSCacheableMailQueue.encodeAttributePropertyName("my-attr name");
            assertThat(encoded)
                .doesNotContain("-")
                .doesNotContain(" ")
                .isEqualTo(ATTRIBUTE_PREFIX + "my_002d_attr_0020_name");
        }

        @Test
        void shouldEncodeNonAsciiCharacters() {
            String encoded = JMSCacheableMailQueue.encodeAttributePropertyName("тест");
            // Cyrillic characters are valid Java identifier parts according to Character.isJavaIdentifierPart
            // Verify behavior conforms to Character.isJavaIdentifierPart
            StringBuilder expected = new StringBuilder(ATTRIBUTE_PREFIX);
            for (char c : "тест".toCharArray()) {
                if (Character.isJavaIdentifierPart(c)) {
                    expected.append(c);
                } else {
                    expected.append(String.format("_%04x_", (int) c));
                }
            }
            assertThat(encoded).isEqualTo(expected.toString());
        }

        @Test
        void shouldHandleEmptyAttributeName() {
            assertThat(JMSCacheableMailQueue.encodeAttributePropertyName(""))
                .isEqualTo(ATTRIBUTE_PREFIX);
        }
    }

    @Nested
    class PerRecipientHeaderPropertyNameEncoding {

        @Test
        void shouldPrependPerRecipientHeaderPrefix() {
            assertThat(JMSCacheableMailQueue.encodePerRecipientHeaderPropertyName("user"))
                .isEqualTo(PER_RECIPIENT_PREFIX + "user");
        }

        @Test
        void shouldEncodeEmailSpecialCharacters() {
            // Recipient addresses contain '@' and '.' and optionally '+', '-'
            String email = "user.name+tag@example-domain.com";
            String encoded = JMSCacheableMailQueue.encodePerRecipientHeaderPropertyName(email);

            assertThat(encoded)
                .startsWith(PER_RECIPIENT_PREFIX)
                .doesNotContain("@")
                .doesNotContain(".")
                .doesNotContain("+")
                .doesNotContain("-");

            assertThat(encoded)
                .isEqualTo(PER_RECIPIENT_PREFIX + "user_002e_name_002b_tag_0040_example_002d_domain_002e_com");
        }
    }

    @Nested
    class PerRecipientHeaderPropertyNameDecoding {

        @Test
        void shouldDecodeSimplePropertyName() {
            String propertyName = PER_RECIPIENT_PREFIX + "user";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user");
        }

        @Test
        void shouldDecodeEncodedHexSequences() {
            String propertyName = PER_RECIPIENT_PREFIX + "user_0040_domain_002e_com";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user@domain.com");
        }

        @Test
        void shouldTolerateUnderscoresWithoutValidHex() {
            // An underscore not followed by a 4-hex pattern should be preserved as-is
            String propertyName = PER_RECIPIENT_PREFIX + "user_name_test";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user_name_test");
        }

        @Test
        void shouldTolerateMalformedHexWithinUnderscores() {
            // _xxxx_ where xxxx is not valid hexadecimal should fall through and not throw
            String propertyName = PER_RECIPIENT_PREFIX + "user_zzzz_test";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user_zzzz_test");
        }

        @Test
        void shouldTolerateTrailingUnderscoreAtEnd() {
            String propertyName = PER_RECIPIENT_PREFIX + "user_";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user_");
        }

        @Test
        void shouldTolerateShortUnderscorePattern() {
            String propertyName = PER_RECIPIENT_PREFIX + "user_12_test";
            assertThat(JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(propertyName))
                .isEqualTo("user_12_test");
        }
    }

    @Nested
    class RoundTripEncodingDecoding {

        @ParameterizedTest
        @ValueSource(strings = {
            "user@domain.tld",
            "first.last@sub.domain.org",
            "user+tag@domain.com",
            "a_b-c.d+e@domain-with-dash.com",
            "123456@numbers.com",
            "user!def#ghi$jkl%mno&pqr'stu*vwx/yz=123?456^789_`{|}~@special.com",
            "admin@localhost"
        })
        void shouldRoundTripRecipientAddressesConsistently(String recipientAddress) {
            String encodedProperty = JMSCacheableMailQueue.encodePerRecipientHeaderPropertyName(recipientAddress);
            String decodedRecipient = JMSCacheableMailQueue.decodePerRecipientHeaderPropertyName(encodedProperty);

            assertThat(decodedRecipient).isEqualTo(recipientAddress);
        }

        @Test
        void encodedPropertyNameShouldBeValidJMSIdentifierPart() {
            String email = "complex.name+tag@sub-domain.example.com";
            String encodedProperty = JMSCacheableMailQueue.encodePerRecipientHeaderPropertyName(email);

            // In JMS, property names must follow Java identifier conventions
            assertThat(Character.isJavaIdentifierStart(encodedProperty.charAt(0))).isTrue();
            for (int i = 1; i < encodedProperty.length(); i++) {
                assertThat(Character.isJavaIdentifierPart(encodedProperty.charAt(i)))
                    .as("Character at index %d ('%c') must be a valid Java identifier part", i, encodedProperty.charAt(i))
                    .isTrue();
            }
        }
    }
}
