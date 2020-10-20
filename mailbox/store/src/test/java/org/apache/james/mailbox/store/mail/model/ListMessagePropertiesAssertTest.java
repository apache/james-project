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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMessagePropertiesAssert.assertProperties;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ListMessagePropertiesAssertTest {
    static final String OTHER_VALUE = "US-ASCII";
    static final String OTHER_LOCAL_NAME = StandardNames.MIME_CONTENT_MD5_NAME;
    static final String OTHER_NAMESPACE = StandardNames.MIME_CONTENT_TYPE_PARAMETER_SPACE;
    static final String VALUE = "7bit";
    static final String LOCAL_NAME = StandardNames.MIME_CONTENT_TRANSFER_ENCODING_NAME;
    static final String NAMESPACE = StandardNames.NAMESPACE_RFC_2045;

    static final Property PROPERTY1 = new Property(NAMESPACE, LOCAL_NAME, VALUE);
    static final Property PROPERTY2 = new Property(OTHER_NAMESPACE, OTHER_LOCAL_NAME, OTHER_VALUE);
    
    List<Property> actual;
    
    @BeforeEach
    void setUp() {
        actual = ImmutableList.of(PROPERTY1, PROPERTY2);
    }
    
    @Test
    void containsOnlyShouldWork() {
        assertProperties(actual).containsOnly(ImmutableList.of(createProperty(NAMESPACE, LOCAL_NAME, VALUE),
            createProperty(OTHER_NAMESPACE, OTHER_LOCAL_NAME, OTHER_VALUE)));
    }
    
    @Test
    void containsOnlyShouldFailWhenNotEnoughElement() {
        assertThatThrownBy(() -> assertProperties(actual).containsOnly(ImmutableList.of(
                createProperty(NAMESPACE, LOCAL_NAME, VALUE))))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void containsOnlyShouldFailWhenNamespaceMismatch() {
        assertThatThrownBy(() -> assertProperties(actual).containsOnly(ImmutableList.of(
                createProperty(NAMESPACE, LOCAL_NAME, VALUE),
                createProperty(OTHER_NAMESPACE, LOCAL_NAME, VALUE))))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void containsOnlyShouldFailWhenNameMismatch() {
        assertThatThrownBy(() -> assertProperties(actual).containsOnly(ImmutableList.of(
                createProperty(NAMESPACE, LOCAL_NAME, VALUE),
                createProperty(NAMESPACE, OTHER_LOCAL_NAME, VALUE))))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void containsOnlyShouldFailWhenValueMismatch() {
        assertThatThrownBy(() -> assertProperties(actual).containsOnly(ImmutableList.of(
                createProperty(NAMESPACE, LOCAL_NAME, VALUE),
                createProperty(NAMESPACE, LOCAL_NAME, OTHER_VALUE))))
            .isInstanceOf(AssertionError.class);
    }

    Property createProperty(String namespace, String name, String value) {
        return new Property(namespace, name, value);
    }
}
