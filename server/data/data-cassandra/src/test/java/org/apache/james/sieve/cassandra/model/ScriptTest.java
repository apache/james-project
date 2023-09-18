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

package org.apache.james.sieve.cassandra.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ScriptTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Script.class).verify();
    }

    @Test
    void buildShouldThrowOnMissingContent() {
        assertThatThrownBy(() -> Script.builder()
                .name("name")
                .isActive(false)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingActivation() {
        assertThatThrownBy(() -> Script.builder()
                .name("name")
                .content("content")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrowOnMissingName() {
        assertThatThrownBy(() -> Script.builder()
                .content("content")
                .isActive(false)
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldPreserveName() {
        ScriptName name = new ScriptName("name");
        assertThat(
            Script.builder()
                .name(name)
                .content("content")
                .isActive(false)
                .build()
                .getName())
            .isEqualTo(name);
    }

    @Test
    void buildShouldPreserveContent() {
        ScriptContent content = new ScriptContent("content");
        assertThat(
            Script.builder()
                .name("name")
                .content(content)
                .isActive(false)
                .build()
                .getContent())
            .isEqualTo(content);
    }

    @Test
    void buildShouldPreserveActiveWhenFalse() {
        assertThat(
            Script.builder()
                .name("name")
                .content("content")
                .isActive(false)
                .build()
                .isActive())
            .isFalse();
    }

    @Test
    void buildShouldPreserveActiveWhenTrue() {
        assertThat(
            Script.builder()
                .name("name")
                .content("content")
                .isActive(true)
                .build()
                .isActive())
            .isTrue();
    }

    @Test
    void buildShouldComputeSizeWhenAbsent() {
        String content = "content";
        assertThat(
            Script.builder()
                .name("name")
                .content(content)
                .isActive(true)
                .build()
                .getSize())
            .isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void buildShouldPreserveSize() {
        long size = 48L;
        assertThat(
            Script.builder()
                .name("name")
                .content("content")
                .isActive(true)
                .size(size)
                .build()
                .getSize())
            .isEqualTo(size);
    }

    @Test
    void toSummaryShouldWork() {
        String name = "name";
        boolean isActive = true;
        long size = 48L;
        assertThat(
            Script.builder()
                .name(name)
                .content("content")
                .isActive(isActive)
                .size(size)
                .build()
                .toSummary())
            .isEqualTo(new ScriptSummary(new ScriptName(name), isActive, size));
    }

    @Test
    void copyOfShouldAllowModifyingName() {
        String content = "content";
        String newName = "newName";

        Script originalScript = Script.builder()
            .name("name")
            .content(content)
            .isActive(true)
            .build();

        assertThat(
            Script.builder()
                .copyOf(originalScript)
                .name(newName)
                .build())
            .isEqualTo(Script.builder()
                .name(newName)
                .content(content)
                .isActive(true)
                .build());
    }

    @Test
    void copyOfShouldAllowModifyingActivation() {
        String content = "content";
        String name = "name";

        Script originalScript = Script.builder()
            .name(name)
            .content(content)
            .isActive(true)
            .build();

        assertThat(
            Script.builder()
                .copyOf(originalScript)
                .isActive(false)
                .build())
            .isEqualTo(Script.builder()
                .name(name)
                .content(content)
                .isActive(false)
                .build());
    }

    @Test
    void copyOfShouldAllowModifyingContent() {
        String name = "name";
        String content = "content";

        Script originalScript = Script.builder()
            .name(name)
            .content(content)
            .isActive(true)
            .build();

        assertThat(
            Script.builder()
                .copyOf(originalScript)
                .content(content)
                .build())
            .isEqualTo(Script.builder()
                .name(name)
                .content(content)
                .isActive(true)
                .build());
    }

}
