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

package org.apache.james;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.json.JsonGenericSerializer;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class JsonSerializationVerifier<T, U extends DTO> {
    @FunctionalInterface
    public interface RequireJson<T, U extends DTO> {
        JsonSerializationVerifier<T, U> json(String json);
    }

    @FunctionalInterface
    public interface EqualityTester<T> extends BiConsumer<T, T> {

    }

    public static <T, U extends DTO> JsonSerializationVerifier<T, U> dtoModule(DTOModule<T, U> dtoModule) {
        return new JsonSerializationVerifier<>(JsonGenericSerializer
            .forModules(dtoModule)
            .withoutNestedType(),
            ImmutableList.of(),
            Optional.empty());
    }

    public static <T, U extends DTO> JsonSerializationVerifier<T, U> serializer(JsonGenericSerializer<T, U> serializer) {
        return new JsonSerializationVerifier<>(serializer, ImmutableList.of(), Optional.empty());
    }

    private static <T> EqualityTester<T> defaultEqualityTester() {
        RecursiveComparisonConfiguration recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.toString().equalsIgnoreCase(o2.toString()), Pattern.class);

        return (a, b) -> assertThat(a)
            .describedAs("Deserialization test [" + b + "]")
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(b);
    }

    private final List<Pair<String, T>> testValues;
    private final JsonGenericSerializer<T, U> serializer;
    private final Optional<EqualityTester<T>> equalityTester;

    private JsonSerializationVerifier(JsonGenericSerializer<T, U> serializer, List<Pair<String, T>> testValues, Optional<EqualityTester<T>> equalityTester) {
        this.testValues = testValues;
        this.serializer = serializer;
        this.equalityTester = equalityTester;
    }

    public RequireJson<T, U> bean(T bean) {
        return json -> new JsonSerializationVerifier<>(
            serializer,
            ImmutableList.<Pair<String, T>>builder()
                .addAll(testValues)
                .add(Pair.of(json, bean))
                .build(),
            equalityTester);
    }

    public JsonSerializationVerifier<T, U> equalityTester(EqualityTester<T> equalityTester) {
        return new JsonSerializationVerifier<>(
            serializer,
            testValues,
            Optional.of(equalityTester));
    }

    public JsonSerializationVerifier<T, U> testCase(T bean, String json) {
        return bean(bean).json(json);
    }

    public void verify() throws IOException {
        testValues.forEach(Throwing.<Pair<String, T>>consumer(this::verify).sneakyThrow());
    }

    private void verify(Pair<String, T> testValue) throws IOException {
        assertThatJson(serializer.serialize(testValue.getRight()))
            .describedAs("Serialization test [" + testValue.getRight() + "]")
            .isEqualTo(testValue.getLeft());

        equalityTester.orElse(defaultEqualityTester())
            .accept(serializer.deserialize(testValue.getLeft()), testValue.getRight());
    }
}
