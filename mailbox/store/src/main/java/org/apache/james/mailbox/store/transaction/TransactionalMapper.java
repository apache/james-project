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

/**
 *
 * Run Transaction and handle begin, commit and rollback in the right order
 *
 */
public abstract class TransactionalMapper implements Mapper {

    /**
     * @see org.apache.james.mailbox.store.transaction.Mapper#execute(org.apache.james.mailbox.store.transaction.Mapper.Transaction)
     */
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
    
    /**
     * Begin transaction
     * 
     * @throws StorageException
     */
    protected abstract void begin() throws MailboxException;

    /**
     * Commit transaction
     * 
     * @throws StorageException
     */
    protected abstract void commit() throws MailboxException;
    
    /**
     * Rollback transaction
     * 
     * @throws StorageException
     */
    protected abstract void rollback() throws MailboxException;

}
