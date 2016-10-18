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
package org.apache.james.mailbox.model;

import java.util.Date;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;

public interface MessageMetaData {

    MessageUid getUid();
    
    
    /**
     * Return the modify-sequence number of the message. This is kind of optional and the mailbox
     * implementation may not support this. If so it will return -1
     */
    long getModSeq();

    Flags getFlags();
    
    /**
     * Return the size in bytes
     */
    long getSize();

    /**
     * <p>
     * IMAP defines this as the time when the message has arrived to the server
     * (by smtp). Clients are also allowed to set the internalDate on append.
     * </p>
     */
    Date getInternalDate();
    
    MessageId getMessageId();
}
