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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

public class MDCBuilderTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void addContextShouldThrowOnNullKey() {
        expectedException.expect(NullPointerException.class);

        MDCBuilder.create()
            .addContext(null, "any");
    }

    @Test
    public void buildContextMapShouldReturnEmptyWhenNoContext() {
        assertThat(MDCBuilder.create().buildContextMap())
            .isEmpty();
    }

    @Test
    public void buildContextMapShouldReturnContext() {
        assertThat(
            MDCBuilder.create()
                .addContext(KEY_1, VALUE_1)
                .addContext(KEY_2, VALUE_2)
                .buildContextMap())
            .containsOnlyKeys(KEY_1, KEY_2)
            .containsEntry(KEY_1, VALUE_1)
            .containsEntry(KEY_2, VALUE_2);
    }

    @Test
    public void addContextShouldFilterOutNullValues() {
        assertThat(
            MDCBuilder.create()
                .addContext(KEY_1, null)
                .buildContextMap())
            .isEmpty();
    }

    @Test
    public void addContextShouldAllowRecursiveBuild() {
        assertThat(
            MDCBuilder.create()
                .addContext(KEY_1, VALUE_1)
                .addContext(MDCBuilder.create()
                    .addContext(KEY_2, VALUE_2))
                .buildContextMap())
            .containsOnlyKeys(KEY_1, KEY_2)
            .containsEntry(KEY_1, VALUE_1)
            .containsEntry(KEY_2, VALUE_2);
    }

    @Test
    public void closeablesConstructorShouldThrowOnNullList() {
        expectedException.expect(NullPointerException.class);

        new MDCBuilder.Closeables(null);
    }

    @Test
    public void closeablesCloseShouldNotThrowWhenEmpty() throws IOException {
        new MDCBuilder.Closeables(ImmutableList.of())
            .close();
    }

    @Test
    public void closeablesCloseShouldCallAllUnderlyingCloseables() throws IOException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();

        Closeable closeable1 = () -> builder.add(VALUE_1);
        Closeable closeable2 = () -> builder.add(VALUE_2);

        new MDCBuilder.Closeables(
            ImmutableList.of(closeable1, closeable2))
            .close();

        assertThat(builder.build())
            .containsExactly(VALUE_1, VALUE_2);
    }


    @Test
    public void closeablesCloseShouldCallAllUnderlyingCloseablesWhenError() throws IOException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();

        Closeable closeable1 = () -> builder.add(VALUE_1);
        Closeable closeable2 = () -> {
            throw new IOException();
        };
        Closeable closeable3 = () -> builder.add(VALUE_2);

        new MDCBuilder.Closeables(
            ImmutableList.of(closeable1, closeable2, closeable3))
            .close();

        assertThat(builder.build())
            .containsExactly(VALUE_1, VALUE_2);
    }

}
