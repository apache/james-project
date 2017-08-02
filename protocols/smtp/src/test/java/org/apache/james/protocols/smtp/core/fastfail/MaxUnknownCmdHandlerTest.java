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


package org.apache.james.protocols.smtp.core.fastfail;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;

public class MaxUnknownCmdHandlerTest {

    
    @Test
    public void testRejectAndClose() throws MailAddressException {
        SMTPSession session = new BaseFakeSMTPSession() {
            private final HashMap<String,Object> map = new HashMap<>();

            public Map<String,Object> getState() {
                return map;
            }
            /*
             * (non-Javadoc)
             * @see org.apache.james.protocols.api.ProtocolSession#setAttachment(java.lang.String, java.lang.Object, org.apache.james.protocols.api.ProtocolSession.State)
             */
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    throw new UnsupportedOperationException();

                } else {
                    if (value == null) {
                        return map.remove(key);
                    } else {
                        return map.put(key, value);
                    }
                }
            }

            /*
             * (non-Javadoc)
             * @see org.apache.james.protocols.api.ProtocolSession#getAttachment(java.lang.String, org.apache.james.protocols.api.ProtocolSession.State)
             */
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    throw new UnsupportedOperationException();
                } else {
                    return map.get(key);
                }
            }
        };
        
        
        MaxUnknownCmdHandler handler = new MaxUnknownCmdHandler();
        handler.setMaxUnknownCmdCount(2);
        int resp = handler.doUnknown(session, "what").getResult();
        assertEquals(HookReturnCode.DECLINED, resp);

        resp = handler.doUnknown(session, "what").getResult();
        assertEquals(HookReturnCode.DECLINED, resp);
        
        resp = handler.doUnknown(session, "what").getResult();
        assertEquals(HookReturnCode.DENY | HookReturnCode.DISCONNECT, resp);
    }
}
