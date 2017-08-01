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

package org.apache.james.protocols.pop3.core;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

public class MessageMetaDataUtils {

    /**
     * Returns the {@link MessageMetaData} for the given message number or <code>null</code> if it can not be 
     * found.
     * 
     * @param session
     * @param number
     * @return data
     */
    public static MessageMetaData getMetaData(POP3Session session, int number) {
        @SuppressWarnings("unchecked")
        List<MessageMetaData> uidList = (List<MessageMetaData>) session.getAttachment(POP3Session.UID_LIST, State.Transaction);
        if (uidList == null || number > uidList.size()) {
            return null;
        } else {
            return uidList.get(number -1);
        }
    }

    /**
     * Check whether POP3 UID is compatible with RFC1939
     * 
     * @param uid
     * @return
     */
    public static boolean isRFC1939Compatible(String uid) {
    	if (uid == null) {
            return false;
        }

        return IntStream.range(0, uid.length())
            .allMatch(i -> uid.charAt(i) >= 0x21 && uid.charAt(i) <= 0x7E);
    }
}
