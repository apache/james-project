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
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class MessageHeaderPropertyTest {

    @Test(expected=NullPointerException.class)
    public void valueOfShouldThrowWhenNull() {
        MessageHeaderProperty.valueOf(null);
    }

    @Test(expected=NullPointerException.class)
    public void valueOfalueOfShouldThrowWhenNull() {
        MessageHeaderProperty.valueOf(null);
    }

    @Test
    public void valueOfShouldReturnLowerCasedProperty() {
        MessageHeaderProperty headerProperty = MessageHeaderProperty.valueOf("ProP");

        assertThat(headerProperty.asFieldName()).isEqualTo("prop");
    }

    @Test
    public void equalsShouldBeTrueWhenIdenticalProperties() {
        assertThat(MessageHeaderProperty.valueOf("prop")).isEqualTo(MessageHeaderProperty.valueOf("prop"));
    }

    @Test
    public void equalsShouldBeFalseWhenDifferentProperties() {
        assertThat(MessageHeaderProperty.valueOf("prop")).isNotEqualTo(MessageHeaderProperty.valueOf("other"));
    }

    @Test
    public void equalsShouldBeTrueWhenDifferentCaseProperties() {
        assertThat(MessageHeaderProperty.valueOf("prOP")).isEqualTo(MessageHeaderProperty.valueOf("PRop"));
    }
}
