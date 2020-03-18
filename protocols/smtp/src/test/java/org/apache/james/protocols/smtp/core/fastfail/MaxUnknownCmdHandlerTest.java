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
import java.util.Optional;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;

import com.google.common.base.Preconditions;

public class MaxUnknownCmdHandlerTest {

    
    @Test
    public void testRejectAndClose() throws Exception {
        SMTPSession session = new BaseFakeSMTPSession() {
            private final HashMap<AttachmentKey<?>, Object> map = new HashMap<>();

            @Override
            public Map<AttachmentKey<?>, Object> getState() {
                return map;
            }

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == State.Connection) {
                    throw new UnsupportedOperationException();
                } else {
                    return key.convert(map.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == State.Connection) {
                    throw new UnsupportedOperationException();
                } else {
                    return key.convert(map.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                if (state == State.Connection) {
                    throw new UnsupportedOperationException();
                } else {
                    return key.convert(map.get(key));
                }
            }
        };
        
        
        MaxUnknownCmdHandler handler = new MaxUnknownCmdHandler();
        handler.setMaxUnknownCmdCount(2);
        HookReturnCode resp = handler.doUnknown(session, "what").getResult();
        assertThat(resp).isEqualTo(HookReturnCode.declined());

        resp = handler.doUnknown(session, "what").getResult();
        assertThat(resp).isEqualTo(HookReturnCode.declined());
        
        resp = handler.doUnknown(session, "what").getResult();
        assertThat(resp).isEqualTo(new HookReturnCode(HookReturnCode.Action.DENY, HookReturnCode.ConnectionStatus.Disconnected));
    }
}
