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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MDCBuilder {

    public interface VoidOperation {
        void perform();
    }

    public static <T> T withMdc(MDCBuilder mdcBuilder, Supplier<T> answerSupplier) {
        try (Closeable closeable = mdcBuilder.build()) {
            try {
                return answerSupplier.get();
            } catch (RuntimeException e) {
                LOGGER.error("Got error, logging its context", e);
                throw e;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void withMdc(MDCBuilder mdcBuilder, VoidOperation logOperation) {
        withMdc(mdcBuilder, () -> {
            logOperation.perform();
            return null;
        });
    }

    public static final String HOST = "host";
    public static final String IP = "ip";
    public static final String PROTOCOL = "protocol";
    public static final String USER = "user";
    public static final String ACTION = "action";
    public static final String SESSION_ID = "sessionId";
    public static final String CHARSET = "charset";

    private static final Logger LOGGER = LoggerFactory.getLogger(MDCBuilder.class);

    public static class Closeables implements Closeable {
        private final List<Closeable> closeables;

        public Closeables(List<Closeable> closeables) {
            Preconditions.checkNotNull(closeables);
            this.closeables = ImmutableList.copyOf(closeables);
        }

        @Override
        public void close() throws IOException {
            closeables.forEach(this::closeQuietly);
        }

        private void closeQuietly(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close Closeable", e);
            }
        }
    }

    public static MDCBuilder create() {
        return new MDCBuilder();
    }

    public static MDCBuilder of(String key, Object value) {
        return create()
            .addContext(key, value);
    }

    private final ImmutableMap.Builder<String, String> contextMap = ImmutableMap.builder();
    private final ImmutableList.Builder<MDCBuilder> nestedBuilder = ImmutableList.builder();

    private MDCBuilder() {

    }

    public MDCBuilder addContext(MDCBuilder nested) {
        this.nestedBuilder.add(nested);
        return this;
    }

    public MDCBuilder addContext(String key, Object value) {
        Preconditions.checkNotNull(key);
        Optional.ofNullable(value)
            .ifPresent(nonNullValue -> contextMap.put(key, nonNullValue.toString()));
        return this;
    }

    @VisibleForTesting
    Map<String, String> buildContextMap() {
        return ImmutableMap.<String, String>builder()
            .putAll(nestedBuilder.build()
                .stream()
                .map(MDCBuilder::buildContextMap)
                .flatMap(map -> map.entrySet().stream())
                .collect(Guavate.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .putAll(contextMap.build())
            .build();
    }

    public <T> T execute(Supplier<T> supplier) {
        return MDCBuilder.withMdc(this, supplier);
    }

    public <T> Supplier<T> wrapArround(Supplier<T> supplier) {
        return () -> execute(supplier);
    }

    public Closeable build() {
        return new Closeables(
            buildContextMap()
                .entrySet()
                .stream()
                .map(entry -> MDC.putCloseable(entry.getKey(), entry.getValue()))
                .collect(Guavate.toImmutableList()));
    }

}
