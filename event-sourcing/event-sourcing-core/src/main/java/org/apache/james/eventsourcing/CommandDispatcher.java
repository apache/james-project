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

package org.apache.james.eventsourcing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.apache.james.eventsourcing.eventstore.EventStoreFailedException;

import com.github.steveash.guavate.Guavate;
import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.Event;

public class CommandDispatcher {

    private static final int MAX_RETRY = 10;

    public static class UnknownCommandException extends RuntimeException {
        private final Command command;

        public UnknownCommandException(Command command) {
            super(String.format("Unknown command %s", command));
            this.command = command;
        }

        public Command getCommand() {
            return command;
        }
    }

    public static class TooManyRetries extends RuntimeException {
        private final Command command;
        private final int retries;


        public TooManyRetries(Command command, int retries) {
            super(String.format("Too much retries for command %s. Store failure after %d retries", command, retries));
            this.command = command;
            this.retries = retries;
        }


        public Command getCommand() {
            return command;
        }

        public int getRetries() {
            return retries;
        }
    }

    private final EventBus eventBus;
    @SuppressWarnings("rawtypes")
    private final Map<Class, CommandHandler> handlers;

    @Inject
    public CommandDispatcher(EventBus eventBus, Set<CommandHandler<?>> handlers) {
        this.eventBus = eventBus;
        this.handlers = handlers.stream()
            .collect(Guavate.toImmutableMap(CommandHandler::handledClass, handler -> handler));
    }

    public void dispatch(Command c) {
        trySeveralTimes(() -> tryDispatch(c))
            .orElseThrow(() -> new TooManyRetries(c, MAX_RETRY));
    }

    private Optional<Integer> trySeveralTimes(Supplier<Boolean> singleTry) {
        return IntStream.range(0, MAX_RETRY)
            .boxed()
            .filter(any -> singleTry.get())
            .findFirst();
    }

    @SuppressWarnings("unchecked")
    private boolean tryDispatch(Command c) {
        try {
            List<Event> events =
                Optional.ofNullable(handlers.get(c.getClass()))
                    .map(f -> f.handle(c))
                    .orElseThrow(() -> new UnknownCommandException(c));

            eventBus.publish(events);
            return true;
        } catch (EventStoreFailedException e) {
            return false;
        }
    }
}
