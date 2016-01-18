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

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class MessagePropertyTest {
    
    @Test(expected=NullPointerException.class)
    public void valueOfShouldThrowWhenNull() {
        MessageProperty.valueOf(null);
    }
    
    @Test
    public void valueOfThenAsFieldNameShouldReturnLowerCasedProperty() {
        assertThat(MessageProperty.valueOf("ProP").asFieldName()).isEqualTo("prop");
    }
    
    @Test(expected=NullPointerException.class)
    public void headerValueOfShouldThrowWhenNull() {
        MessageProperty.headerValueOf(null);
    }
    
    @Test
    public void headerValueOfShouldReturnPrefixedProperty() {
        assertThat(MessageProperty.headerValueOf("Prop").asFieldName()).isEqualTo("headers.prop");
    }
    
    @Test
    public void isHeaderPropertyShouldReturnFalseWhenNotPrefixed() {
        assertThat(MessageProperty.valueOf("prop").isHeaderProperty()).isFalse();
    }
    
    @Test
    public void isHeaderPropertyShouldReturnFalseWhenPrefixedBySomethingElse() {
        assertThat(MessageProperty.valueOf("somethingelse.prop").isHeaderProperty()).isFalse();
    }
    
    @Test
    public void isHeaderPropertyShouldReturnTrueWhenPrefixedByHeaders() {
        assertThat(MessageProperty.valueOf("headers.prop").isHeaderProperty()).isTrue();
    }
    
    @Test(expected=NullPointerException.class)
    public void selectHeadersPropertiesShouldThrowWhenNull() {
        MessageProperty.selectHeadersProperties(null);
    }
    
    @Test
    public void selectHeadersPropertiesShouldReturnOnlyHeadersProperties() {
        Set<MessageProperty> properties = ImmutableSet.of(
                MessageProperty.from,
                MessageProperty.valueOf("headers.prop"),
                MessageProperty.valueOf("headers.prop2"),
                MessageProperty.valueOf("prop"));
        assertThat(MessageProperty.selectHeadersProperties(properties)).containsOnly(MessageProperty.headerValueOf("prop"), MessageProperty.headerValueOf("prop2"));
    }
    
    @Test
    public void equalsShouldBeTrueWhenIdenticalProperties() {
        assertThat(MessageProperty.valueOf("prop")).isEqualTo(MessageProperty.valueOf("prop"));
    }

    @Test
    public void equalsShouldBeFalseWhenDifferentProperties() {
        assertThat(MessageProperty.valueOf("prop")).isNotEqualTo(MessageProperty.valueOf("other"));
    }

    @Test
    public void equalsShouldBeTrueWhenDifferentCaseProperties() {
        assertThat(MessageProperty.valueOf("prOP")).isEqualTo(MessageProperty.valueOf("PRop"));
    }
}
