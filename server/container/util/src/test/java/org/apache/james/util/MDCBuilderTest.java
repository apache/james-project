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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.Closeable;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MDCBuilderTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";

    @Test
    void addContextShouldThrowOnNullKey() {
        assertThatNullPointerException()
            .isThrownBy(() -> 
                MDCBuilder.create()
                    .addContext(null, "any"));
    }

    @Test
    void addToContextShouldThrowOnNullKey() {
        assertThatNullPointerException()
            .isThrownBy(() ->
                MDCBuilder.create()
                    .addToContext(null, "any"));
    }

    @Test
    void buildContextMapShouldReturnEmptyWhenNoContext() {
        assertThat(MDCBuilder.create().buildContextMap())
            .isEmpty();
    }

    @Test
    void buildContextMapShouldReturnContext() {
        assertThat(
            MDCBuilder.create()
                .addToContext(KEY_1, VALUE_1)
                .addToContext(KEY_2, VALUE_2)
                .buildContextMap())
            .containsOnlyKeys(KEY_1, KEY_2)
            .containsEntry(KEY_1, VALUE_1)
            .containsEntry(KEY_2, VALUE_2);
    }

    @Test
    void addContextShouldFilterOutNullValues() {
        assertThat(
            MDCBuilder.create()
                .addToContext(KEY_1, null)
                .buildContextMap())
            .isEmpty();
    }

    @Test
    void addContextShouldAllowRecursiveBuild() {
        assertThat(
            MDCBuilder.create()
                .addToContext(KEY_1, VALUE_1)
                .addToContext(MDCBuilder.create()
                    .addToContext(KEY_2, VALUE_2))
                .buildContextMap())
            .containsOnlyKeys(KEY_1, KEY_2)
            .containsEntry(KEY_1, VALUE_1)
            .containsEntry(KEY_2, VALUE_2);
    }

    @Test
    void closeablesConstructorShouldThrowOnNullList() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MDCBuilder.Closeables(null));
    }

    @Test
    void closeablesCloseShouldNotThrowWhenEmpty() throws IOException {
        new MDCBuilder.Closeables(ImmutableList.of())
            .close();
    }

    @Test
    void closeablesCloseShouldCallAllUnderlyingCloseables() throws IOException {
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
    void closeablesCloseShouldCallAllUnderlyingCloseablesWhenError() throws IOException {
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
