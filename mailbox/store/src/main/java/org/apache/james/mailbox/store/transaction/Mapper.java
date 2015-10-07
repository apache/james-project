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
 * Mapper which execute units of work in a {@link Transaction}
 *
 */
public interface Mapper {
    
    /**
     * IMAP Request was complete. Cleanup all Request scoped stuff
     */
    public void endRequest();
    
    /**
     * Execute the given Transaction
     * 
     * @param transaction 
     * @throws MailboxException
     */
    public <T> T execute(Transaction<T> transaction) throws MailboxException;
        
    /**
     * Unit of work executed in a Transaction
     *
     */
    public interface Transaction<T> {
        
        /**
         * Run unit of work in a Transaction and return a value
         * 
         * @throws MailboxException
         */
        public T run() throws MailboxException;
    }
    
    
    public abstract class VoidTransaction implements Transaction<Void> {
        
        public final Void run() throws MailboxException {
            runVoid();
            return null;
        }
        public abstract void runVoid() throws MailboxException;

    }
}
