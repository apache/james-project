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
package org.apache.james.mailbox.maildir;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.maildir.mail.MaildirMailboxMapper;
import org.apache.james.mailbox.maildir.mail.MaildirMessageMapper;
import org.apache.james.mailbox.maildir.user.MaildirSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

public class MaildirMailboxSessionMapperFactory extends
        MailboxSessionMapperFactory<MaildirId> {

    private final MaildirStore store;

    
    public MaildirMailboxSessionMapperFactory(MaildirStore store) {
        this.store = store;
    }
    
    
    @Override
    public MailboxMapper<MaildirId> createMailboxMapper(MailboxSession session)
            throws MailboxException {
        return new MaildirMailboxMapper(store, session);
    }

    @Override
    public MessageMapper<MaildirId> createMessageMapper(MailboxSession session)
            throws MailboxException {
        return new MaildirMessageMapper(session, store);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session)
            throws SubscriptionException {
        return new MaildirSubscriptionMapper(store);
    }

}
