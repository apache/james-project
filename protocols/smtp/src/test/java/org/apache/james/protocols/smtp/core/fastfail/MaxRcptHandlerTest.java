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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

public class MaxRcptHandlerTest {
    
    private SMTPSession setupMockedSession(final int rcptCount) {
        return new BaseFakeSMTPSession() {
            HashMap<AttachmentKey<?>, Object> state = new HashMap<>();

            @Override
            public Map<AttachmentKey<?>, Object> getState() {
                return state;
            }

            @Override
            public boolean isRelayingAllowed() {
                return false;
            }

            @Override
            public int getRcptCount() {
                return rcptCount;
            }

        };
    }
    
    @Test
    void testRejectMaxRcpt() throws Exception {
        SMTPSession session = setupMockedSession(3);
        MaxRcptHandler handler = new MaxRcptHandler();
        
        handler.setMaxRcpt(2);
        HookReturnCode resp = handler.doRcpt(session, MaybeSender.nullSender(), new MailAddress("test@test")).getResult();
    
        assertThat(HookReturnCode.deny()).describedAs("Rejected.. To many recipients").isEqualTo(resp);
    }
  
  
    @Test
    void testNotRejectMaxRcpt() throws Exception {
        SMTPSession session = setupMockedSession(3);
        MaxRcptHandler handler = new MaxRcptHandler();    

        handler.setMaxRcpt(4);
        HookReturnCode resp = handler.doRcpt(session, MaybeSender.nullSender(), new MailAddress("test@test")).getResult();
        
        assertThat(HookReturnCode.declined()).describedAs("Not Rejected..").isEqualTo(resp);
    }

}
