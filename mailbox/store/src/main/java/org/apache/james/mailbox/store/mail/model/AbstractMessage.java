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
package org.apache.james.mailbox.store.mail.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

import javax.mail.Flags;



/**
 * Abstract base class for {@link Message}
 *
 */
public abstract class AbstractMessage<Id extends MailboxId> implements Message<Id> {
    

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Message<Id> other) {
        return (int) (getUid() - other.getUid());
    }
    
    

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#createFlags()
     */
    public final Flags createFlags() {
        final Flags flags = new Flags();

        if (isAnswered()) {
            flags.add(Flags.Flag.ANSWERED);
        }
        if (isDeleted()) {
            flags.add(Flags.Flag.DELETED);
        }
        if (isDraft()) {
            flags.add(Flags.Flag.DRAFT);
        }
        if (isFlagged()) {
            flags.add(Flags.Flag.FLAGGED);
        }
        if (isRecent()) {
            flags.add(Flags.Flag.RECENT);
        }
        if (isSeen()) {
            flags.add(Flags.Flag.SEEN);
        }
        String[] userFlags = createUserFlags();
        if (userFlags != null && userFlags.length > 0) {
            for (int i = 0; i < userFlags.length; i++) {
                flags.add(userFlags[i]);
            }
        }
        return flags;
    }
    
    
    /**
     * Return all stored user flags or null if none are stored. By default this return null as no user flags are stored
     * permanent. This method SHOULD get overridden, If the implementation supports to store user flags.
     * 
     * @return userFlags
     */
    protected String[] createUserFlags() {
        return null;
    }
    
    
    /**
     * The number of octets contained in the body of this part.
     * 
     * @return number of octets
     */
    public long getBodyOctets() {
        return getFullContentOctets() - getBodyStartOctet();
    }
    
    /**
     * Return the start octet of the body
     * 
     * @return startOctet
     */
    protected abstract int getBodyStartOctet();



    
    /**
     * This implementation just concat {@link #getHeaderContent()} and {@link #getBodyContent()}.
     * 
     * Implementation should override this if they can provide a more performant solution
     * 
     * @return content
     * @throws exception
     */
    public InputStream getFullContent() throws IOException {
        return new SequenceInputStream(getHeaderContent(), getBodyContent());
    }

    

}
