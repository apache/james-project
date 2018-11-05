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

package org.apache.mailet;


import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

import com.google.common.collect.ImmutableMap;

public abstract class ContractMailTest {
    private final static AttributeName ATTRIBUTE_NAME_1 = AttributeName.of("name1");
    private final static AttributeName ATTRIBUTE_NAME_2 = AttributeName.of("name2");
    private final static Attribute ATTRIBUTE_1 = new Attribute(ATTRIBUTE_NAME_1, AttributeValue.of(true));
    private final static Attribute ATTRIBUTE_2 = new Attribute(ATTRIBUTE_NAME_2, AttributeValue.of("value2"));
    private final static Attribute ATTRIBUTE_1_BIS = new Attribute(ATTRIBUTE_NAME_1, AttributeValue.of("value1"));

    public abstract Mail newMail();

    @Nested
    public class AttributeTests {
        private Mail mail;
        
        @BeforeEach
        void setUp() {
            mail = newMail();
        }

        @Test
        void newMailShouldHaveNoAttribute() {
            assertThat(mail.attributes()).isEmpty();
        }
        
        @Test
        void newMailShouldHaveNoAttributeName() {
            assertThat(mail.attributeNames()).isEmpty();
        }

        @Test
        void newMailShouldHaveAnEmptyAttributeMap() {
            assertThat(mail.attributesMap()).isEmpty();
        }

        @Test
        void setAttributeShouldReturnEmptyWhenNoPreviousValue() {
            assertThat(mail.setAttribute(ATTRIBUTE_1)).isEmpty();
        }
    }
    
    @Nested
    public class OneAttributeMail {
        private Mail mail;
        
        @BeforeEach
        void setUp() {
            mail = newMail();
            mail.setAttribute(ATTRIBUTE_1);
        }
        
        @Test
        void shouldHaveOneAttribute() {
            assertThat(mail.attributes()).containsExactly(ATTRIBUTE_1);
        }
        
        @Test
        void shouldHaveOneAttributeName() {
            assertThat(mail.attributeNames()).containsExactly(ATTRIBUTE_NAME_1);
        }

        @Test
        void shouldHaveOneAttributeMap() {
            assertThat(mail.attributesMap()).isEqualTo(ImmutableMap.of(ATTRIBUTE_NAME_1, ATTRIBUTE_1));
        }
        
        @Test
        void shouldBeRetrievable() {
            assertThat(mail.getAttribute(ATTRIBUTE_NAME_1)).contains(ATTRIBUTE_1);
        }
        
        @Test
        void shouldBeRemovable() {
            SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(mail.removeAttribute(ATTRIBUTE_NAME_1)).contains(ATTRIBUTE_1);
                    softly.assertThat(mail.getAttribute(ATTRIBUTE_NAME_1)).isEmpty();
                }
            );
        }
        
        @Test
        void shouldBeReplacable() {
            SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(mail.setAttribute(ATTRIBUTE_1_BIS)).contains(ATTRIBUTE_1);
                    softly.assertThat(mail.getAttribute(ATTRIBUTE_NAME_1)).contains(ATTRIBUTE_1_BIS);
                }
            );
        }
    }
    
    @Nested
    public class TwoAttributesMail {
        private Mail mail;

        @BeforeEach
        void setUp() {
            mail = newMail();
            mail.setAttribute(ATTRIBUTE_1);
            mail.setAttribute(ATTRIBUTE_2);
        }

        @Test
        void shouldHaveTwoAttributes() {
            assertThat(mail.attributes()).containsExactlyInAnyOrder(ATTRIBUTE_1, ATTRIBUTE_2);
        }

        @Test
        void shouldHaveTwoAttributesName() {
            assertThat(mail.attributeNames()).containsExactlyInAnyOrder(ATTRIBUTE_NAME_1, ATTRIBUTE_NAME_2);
        }

        @Test
        void shouldHaveTwoAttributesMap() {
            assertThat(mail.attributesMap()).isEqualTo(ImmutableMap.of(ATTRIBUTE_NAME_1, ATTRIBUTE_1, ATTRIBUTE_NAME_2, ATTRIBUTE_2));
        }
    }
}
