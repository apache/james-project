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

package org.apache.james.utils;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Inject;

public class InitializationOperations {

    private final Set<InitializationOperation> initializationOperations;
    private final Startables startables;

    @Inject
    public InitializationOperations(Set<InitializationOperation> initializationOperations, Startables startables) {
        this.initializationOperations = initializationOperations;
        this.startables = startables;
    }

    public void initModules() {
        Set<InitializationOperation> processed = processStartables();
        
        processOthers(processed);
    }

    private Set<InitializationOperation> processStartables() {
        return startables.get().stream()
            .flatMap(this::configurationPerformerFor)
            .distinct()
            .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
            .peek(Throwing.consumer(InitializationOperation::initModule).sneakyThrow())
            .collect(Collectors.toSet());
    }

    /**
     * Locate configuration performer for this class.
     *
     * We reorder the performer so that one class requirements are always satisfied (startable order is wrong for
     * provisioned instances...)
     */
    private Stream<InitializationOperation> configurationPerformerFor(Class<?> startable) {
        return initializationOperations.stream()
                .filter(x -> startable.isAssignableFrom(x.forClass()))
                .flatMap(x -> Stream.concat(x.requires().stream().flatMap(this::configurationPerformerFor), Stream.of(x)));
    }

    private void processOthers(Set<InitializationOperation> processed) {
        initializationOperations.stream()
            .filter(x -> !processed.contains(x))
            .forEach(Throwing.consumer(InitializationOperation::initModule).sneakyThrow());
    }
}
