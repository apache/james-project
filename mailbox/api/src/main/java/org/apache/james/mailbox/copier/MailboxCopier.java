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
package org.apache.james.mailbox.copier;

import java.io.IOException;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;

/**
 * Interface that exposes a method aimed to copy all mailboxes from a source
 * mailbox manager to a destination mailbox manager.
 * 
 */
public interface MailboxCopier {

    /**
     * Copy the mailboxes from a mailbox manager to another mailbox manager. The
     * implementation is responsible to read all mailboxes form the injected
     * srcMailboxManager and to copy all its contents to the dstMailboxManager.
     * 
     * @param src
     * @param dest
     */
    void copyMailboxes(MailboxManager src, MailboxManager dest) throws MailboxException, IOException;

}
