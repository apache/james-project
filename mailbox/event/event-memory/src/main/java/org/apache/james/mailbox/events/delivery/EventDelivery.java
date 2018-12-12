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

package org.apache.james.mailbox.events.delivery;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;

public interface EventDelivery {
    class ExecutionStages {
        private final CompletableFuture<Void> synchronousListenerFuture;
        private final CompletableFuture<Void> asynchronousListenerFuture;

        ExecutionStages(CompletableFuture<Void> synchronousListenerFuture, CompletableFuture<Void> asynchronousListenerFuture) {
            this.synchronousListenerFuture = synchronousListenerFuture;
            this.asynchronousListenerFuture = asynchronousListenerFuture;
        }

        public CompletableFuture<Void> synchronousListenerFuture() {
            return synchronousListenerFuture;
        }

        public CompletableFuture<Void> allListenerFuture() {
            return CompletableFuture.allOf(
                synchronousListenerFuture,
                asynchronousListenerFuture);
        }
    }


    ExecutionStages deliver(Collection<MailboxListener> mailboxListeners, Event event);
}
