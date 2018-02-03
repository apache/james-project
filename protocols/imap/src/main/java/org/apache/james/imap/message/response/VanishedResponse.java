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

package org.apache.james.imap.message.response;

import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.api.message.response.ImapResponseMessage;


public class VanishedResponse implements ImapResponseMessage {

    private final UidRange[] uids;
    private final boolean earlier;

    public VanishedResponse(UidRange[] vanishedIdRanges, boolean earlier) {
        this.earlier = earlier;
        this.uids = vanishedIdRanges;
    }
    
    /**
     * Return the uids which where expunged
     */
    public final UidRange[] getUids() {
        return uids;
    }
    
    /**
     * Return true if the <code>VANISHED</code> response was caused
     * because of an earlier SELECT/EXAMINE (QRESYNC) or UID FETCH (VANISHED)
     */
    public final boolean isEarlier() {
        return earlier;
    }
    
}
