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

package org.apache.james.mailbox;

import java.util.concurrent.locks.ReadWriteLock;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;

/**
 * The {@link MailboxPathLocker} is responsible to help to synchronize the
 * access to a {@link MailboxPath} and execute an given {@link LockAwareExecution}
 * 
 * Implementations that are not able to handle read / write locks in a different way are needed to handle all locks as write lock.
 */
public interface MailboxPathLocker {
    enum LockType {
        Read,
        Write
    }

    /**
     * Execute the {@link LockAwareExecution} while holding a lock on the
     * {@link MailboxPath}. If writeLock is true the implementation need to make sure that no other threads can read and write while the lock
     * is hold. The contract is the same as documented in {@link ReadWriteLock}.
     */
    <T> T executeWithLock(MailboxPath path, LockAwareExecution<T> execution, LockType lockType) throws MailboxException;

    /**
     * Execute code while holding a lock
     */
    interface LockAwareExecution<T> {

        /**
         * Execute code block
         */
        T execute() throws MailboxException;
    }

}
