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

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

/**
 *
 * Run Transaction and handle begin, commit and rollback in the right order
 */
public abstract class TransactionalMapper implements Mapper {

    @Override
    public final <T> T execute(Transaction<T> transaction) throws MailboxException {
        begin();
        try {
            T value = transaction.run();
            commit();
            return value;
        } catch (MailboxException e) {
            rollback();
            throw e;
        }
    }

    public final <T> Mono<T> executeReactive(Mono<T> transaction) {
        return Mono.fromRunnable(Throwing.runnable(this::begin).sneakyThrow())
            .then(transaction)
            .doOnNext(Throwing.consumer(ignored -> commit()).sneakyThrow())
            .doOnError(MailboxException.class, Throwing.consumer(e -> rollback()).sneakyThrow());
    }

    public final Mono<Void> executeReactiveVoid(Mono<Void> transaction) {
        return Mono.fromRunnable(Throwing.runnable(this::begin).sneakyThrow())
                .then(transaction)
                .thenEmpty(Mono.fromRunnable(Throwing.runnable(this::commit).sneakyThrow()))
                .doOnError(MailboxException.class, Throwing.consumer(e -> rollback()).sneakyThrow());
    }

    /**
     * Begin transaction
     */
    protected abstract void begin() throws MailboxException;

    /**
     * Commit transaction
     */
    protected abstract void commit() throws MailboxException;
    
    /**
     * Rollback transaction
     */
    protected abstract void rollback() throws MailboxException;

}
