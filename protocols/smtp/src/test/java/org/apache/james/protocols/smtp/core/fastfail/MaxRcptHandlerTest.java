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

import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;



public class MaxRcptHandlerTest {
    
    private SMTPSession setupMockedSession(final int rcptCount) {
        return new BaseFakeSMTPSession() {
            HashMap<String,Object> state = new HashMap<>();

            public Map<String,Object> getState() {
                return state;
            }

            public boolean isRelayingAllowed() {
                return false;
            }

            public int getRcptCount() {
                return rcptCount;
            }

        };
    }
    
    @Test
    public void testRejectMaxRcpt() throws MailAddressException {
        SMTPSession session = setupMockedSession(3);
        MaxRcptHandler handler = new MaxRcptHandler();
        
        handler.setMaxRcpt(2);
        int resp = handler.doRcpt(session,null,new MailAddress("test@test")).getResult();
    
        assertEquals("Rejected.. To many recipients", resp, HookReturnCode.DENY);
    }
  
  
    @Test
    public void testNotRejectMaxRcpt() throws MailAddressException {
        SMTPSession session = setupMockedSession(3);
        MaxRcptHandler handler = new MaxRcptHandler();    

        handler.setMaxRcpt(4);
        int resp = handler.doRcpt(session,null,new MailAddress("test@test")).getResult();
        
        assertEquals("Not Rejected..", resp, HookReturnCode.DECLINED);
    }

}
