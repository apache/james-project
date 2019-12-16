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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class Runnables {
    public static void runParallel(Runnable... runnables) {
        Flux<Runnable> stream = Flux.just(runnables);
        runParallel(stream);
    }

    public static void runParallel(Flux<Runnable> runnables) {
        runnables
            .publishOn(Schedulers.elastic())
            .parallel()
            .runOn(Schedulers.elastic())
            .flatMap(runnable -> {
                runnable.run();
                return Mono.empty();
            })
            .sequential()
            .then()
            .block();
    }
}
