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
package org.apache.james.mailbox.store;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;


public abstract class AbstractMailboxPathLocker implements MailboxPathLocker{

    @Override
    public <T> T executeWithLock(MailboxSession session, MailboxPath path, LockAwareExecution<T> execution) throws MailboxException {
        return executeWithLock(session, path, execution, true);
    }
    
    @Override
    public <T> T executeWithLock(MailboxSession session, MailboxPath path, LockAwareExecution<T> execution, boolean writeLock) throws MailboxException {
        try {
            lock(session, path, writeLock);
            return execution.execute();
        } finally {
            unlock(session, path, writeLock);
        }
    }

    
    /**
     * Perform lock
     * 
     * @param session
     * @param path
     * @throws MailboxException
     */
    protected abstract void lock(MailboxSession session, MailboxPath path, boolean writeLock) throws MailboxException;

    /**
     * Release lock
     * 
     * @param session
     * @param path
     * @throws MailboxException
     */
    protected abstract void unlock(MailboxSession session, MailboxPath path, boolean writeLock) throws MailboxException;

}
