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
package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.model.MessageProperties.HeaderProperty;
import org.junit.Test;

public class MessageHeaderPropertyTest {

    @Test
    public void fromFieldNameShouldLowercaseFieldName() {
        assertThat(HeaderProperty.fromFieldName("FiElD")).isEqualTo(HeaderProperty.fromFieldName("field"));
    }

    @Test
    public void fromFieldNameShouldThrowWhenStartWithHeaderPrefix() {
        assertThatThrownBy(() -> HeaderProperty.fromFieldName("headers.FiElD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void valueOfShouldThrowWhenNull() {
        assertThatThrownBy(() -> HeaderProperty.valueOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void valueOfalueOfShouldThrowWhenNull() {
        assertThatThrownBy(() -> HeaderProperty.valueOf(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void valueOfShouldReturnLowerCasedProperty() {
        HeaderProperty headerProperty = HeaderProperty.valueOf("headers.ProP");

        assertThat(headerProperty.asFieldName()).isEqualTo("prop");
    }

    @Test
    public void valueOfShouldThrowWhenValueIsNotHeader() {
        assertThatThrownBy(() -> HeaderProperty.valueOf("ProP")).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void findShouldReturnStreamWhenValueStartsWithRightString() {
        assertThat(HeaderProperty.find(HeaderProperty.HEADER_PROPERTY_PREFIX + "myvalue"))
            .contains(HeaderProperty.valueOf(HeaderProperty.HEADER_PROPERTY_PREFIX + "myvalue"));
    }

    @Test
    public void findShouldReturnEmptyStreamWhenValueStartsWithWrongString() {
        assertThat(HeaderProperty.find("bad value" + HeaderProperty.HEADER_PROPERTY_PREFIX + "myvalue")).isEmpty();
    }
    
    @Test
    public void equalsShouldBeTrueWhenIdenticalProperties() {
        assertThat(HeaderProperty.valueOf("headers.prop")).isEqualTo(HeaderProperty.valueOf("headers.prop"));
    }

    @Test
    public void equalsShouldBeFalseWhenDifferentProperties() {
        assertThat(HeaderProperty.valueOf("headers.prop")).isNotEqualTo(HeaderProperty.valueOf("headers.other"));
    }

    @Test
    public void equalsShouldBeTrueWhenDifferentCaseProperties() {
        assertThat(HeaderProperty.valueOf("headers.prOP")).isEqualTo(HeaderProperty.valueOf("headers.PRop"));
    }
}
