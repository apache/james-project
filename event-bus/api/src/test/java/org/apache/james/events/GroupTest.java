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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mailbox.events.GenericGroup;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class GroupTest {
    static class GroupA extends Group {

    }

    static class GroupB extends Group {

    }

    static class GroupC extends GroupA {

    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Group.class)
            .usingGetClass()
            .verify();
    }

    @Test
    void equalsShouldReturnTrueOnSameClass() {
        assertThat(new GroupA()).isEqualTo(new GroupA());
    }

    @Test
    void equalsShouldReturnFalseOnDifferentClass() {
        assertThat(new GroupA()).isNotEqualTo(new GroupB());
    }

    @Test
    void equalsShouldReturnFalseOnSubClass() {
        assertThat(new GroupA()).isNotEqualTo(new GroupC());
    }

    @Test
    void equalsShouldReturnFalseOnParentClass() {
        assertThat(new GroupC()).isNotEqualTo(new GroupA());
    }

    @Test
    void genericGroupShouldMatchBeanContract() {
        EqualsVerifier.forClass(GenericGroup.class)
            .withRedefinedSuperclass()
            .verify();
    }

    @Test
    void asStringShouldReturnFqdnByDefault() {
        assertThat(new EventBusTestFixture.GroupA().asString()).isEqualTo("org.apache.james.events.EventBusTestFixture$GroupA");
    }

    @Test
    void asStringShouldReturnNameWhenGenericGroup() {
        assertThat(new GenericGroup("abc").asString()).isEqualTo("org.apache.james.mailbox.events.GenericGroup-abc");
    }

    @Test
    void deserializeShouldReturnGroupWhenGenericGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.events.GenericGroup-abc"))
            .isEqualTo(new GenericGroup("abc"));
    }

    @Test
    void deserializeShouldReturnGroupWhenEmptyGenericGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.events.GenericGroup-"))
            .isEqualTo(new GenericGroup(""));
    }

    @Test
    void deserializeShouldReturnGroupWhenExtendsGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.events.EventBusTestFixture$GroupA"))
            .isEqualTo(new EventBusTestFixture.GroupA());
    }

    @Test
    void deserializeShouldThrowWhenClassNotFound() {
        assertThatThrownBy(() -> Group.deserialize("org.apache.james.events.Noone"))
            .isInstanceOf(Group.GroupDeserializationException.class);
    }

    @Test
    void deserializeShouldThrowWhenNotAGroup() {
        assertThatThrownBy(() -> Group.deserialize("java.lang.String"))
            .isInstanceOf(Group.GroupDeserializationException.class);
    }

    @Test
    void deserializeShouldThrowWhenConstructorArgumentsRequired() {
        assertThatThrownBy(() -> Group.deserialize("org.apache.james.mailbox.events.GenericGroup"))
            .isInstanceOf(Group.GroupDeserializationException.class);
    }

    @Test
    void deserializeShouldThrowWhenNull() {
        assertThatThrownBy(() -> Group.deserialize(null))
            .isInstanceOf(Group.GroupDeserializationException.class);
    }

    @Test
    void deserializeShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> Group.deserialize(""))
            .isInstanceOf(Group.GroupDeserializationException.class);
    }
}