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

import java.nio.charset.StandardCharsets;

import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ScriptTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Script.class).verify();
    }

    @Test
    public void buildShouldThrowOnMissingContent() {
        expectedException.expect(IllegalStateException.class);

        Script.builder()
            .name("name")
            .isActive(false)
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingActivation() {
        expectedException.expect(IllegalStateException.class);

        Script.builder()
            .name("name")
            .content("content")
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingName() {
        expectedException.expect(IllegalStateException.class);

        Script.builder()
            .content("content")
            .isActive(false)
            .build();
    }

    @Test
    public void buildShouldPreserveName() {
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
    public void buildShouldPreserveContent() {
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
    public void buildShouldPreserveActiveWhenFalse() {
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
    public void buildShouldPreserveActiveWhenTrue() {
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
    public void buildShouldComputeSizeWhenAbsent() {
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
    public void buildShouldPreserveSize() {
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
    public void toSummaryShouldWork() {
        String name = "name";
        boolean isActive = true;
        assertThat(
            Script.builder()
                .name(name)
                .content("content")
                .isActive(isActive)
                .size(48L)
                .build()
                .toSummary())
            .isEqualTo(new ScriptSummary(new ScriptName(name), isActive));
    }

    @Test
    public void copyOfShouldAllowModifyingName() {
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
    public void copyOfShouldAllowModifyingActivation() {
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
    public void copyOfShouldAllowModifyingContent() {
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
