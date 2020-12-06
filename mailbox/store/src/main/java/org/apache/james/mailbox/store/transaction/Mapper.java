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

package org.apache.james.mailbox.store.transaction;

import org.apache.james.mailbox.exception.MailboxException;

import reactor.core.publisher.Mono;

/**
 * Mapper which execute units of work in a {@link Transaction}
 */
public interface Mapper {
    
    /**
     * IMAP Request was complete. Cleanup all Request scoped stuff
     */
    default void endRequest() {

    }
    
    /**
     * Execute the given Transaction
     */
    default <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    default <T> Mono<T> executeReactive(Mono<T> transaction) {
        return transaction;
    }

    /**
     * Unit of work executed in a Transaction
     *
     */
    interface Transaction<T> {
        
        /**
         * Run unit of work in a Transaction and return a value
         */
        T run() throws MailboxException;
    }

    interface Operation {
        void run() throws MailboxException;
    }

    static Transaction<Void> toTransaction(Operation operation) throws MailboxException {
        return () -> {
            operation.run();
            return null;
        };
    }
}
