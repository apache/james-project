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

package org.apache.james.imap.processor.base;

import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;

public class FakeMailboxListenerAdded extends MailboxListener.Added {

    public List<Long> uids;

    public FakeMailboxListenerAdded(MailboxSession session, List<Long> uids, MailboxPath path) {
        super(session, path);
        this.uids = uids;
    }

    /**
     * @see org.apache.james.mailbox.MailboxListener.MessageEvent#getUids()
     */
    public List<Long> getUids() {
        return uids;
    }


    /**
     * @see org.apache.james.mailbox.MailboxListener.Added#getMetaData(long)
     */
    public MessageMetaData getMetaData(long uid) {
        return new MessageMetaData() {
            
            public long getUid() {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public long getSize() {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public Date getInternalDate() {
                // TODO Auto-generated method stub
                return null;
            }
            
            public Flags getFlags() {
                // TODO Auto-generated method stub
                return null;
            }

            public long getModSeq() {
                // TODO Auto-generated method stub
                return 0;
            }
        };
    }


}
