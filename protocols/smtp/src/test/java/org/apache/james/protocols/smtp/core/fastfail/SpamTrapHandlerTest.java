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

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Test;

class SpamTrapHandlerTest {
    private static final String SPAM_TRAP_RECIP1 = "spamtrap1@localhost";
    private static final String RECIP1 = "recip@localhost";
    
    private SMTPSession setUpSMTPSession(final String ip) {
        return new BaseFakeSMTPSession() {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(getRemoteIPAddress(), 10000);
            }
            
            public String getRemoteIPAddress() {
                return ip;
            }
        
        };
    }
    
    @Test
    void testSpamTrap() throws Exception {
        String spamIP = "192.168.100.1";
        String nonSpamIP = "192.168.100.2";
        long blockTime = 2000;
    
        ArrayList<String> rcpts = new ArrayList<>();
        rcpts.add(SPAM_TRAP_RECIP1);

        Instant now = Instant.now();
        UpdatableTickingClock updatableTickingClock = new UpdatableTickingClock(now);
        SpamTrapHandler handler = new SpamTrapHandler(updatableTickingClock);
    
        handler.setBlockTime(blockTime);
        handler.setSpamTrapRecipients(rcpts);

        HookReturnCode result = handler.doRcpt(setUpSMTPSession(spamIP), MaybeSender.nullSender(), new MailAddress(SPAM_TRAP_RECIP1)).getResult();
        assertThat(result).describedAs("Blocked on first connect as spam IP and BlockTime still valid.").isEqualTo(HookReturnCode.deny());

        result = handler.doRcpt(setUpSMTPSession(spamIP), MaybeSender.nullSender(), new MailAddress(RECIP1)).getResult();
        assertThat(result).describedAs("Blocked on second connect as spam IP and BlockTime still valid.").isEqualTo(HookReturnCode.deny());

        result = handler.doRcpt(setUpSMTPSession(nonSpamIP), MaybeSender.nullSender(), new MailAddress(RECIP1)).getResult();
        assertThat(result).describedAs("Not Blocked as not spam IP.").isEqualTo(HookReturnCode.declined());

        updatableTickingClock.setInstant(now.plusMillis(blockTime));
        result = handler.doRcpt(setUpSMTPSession(spamIP), MaybeSender.nullSender(), new MailAddress(RECIP1)).getResult();
        assertThat(result).describedAs("Not blocked as BlockTime exceeded.").isEqualTo(HookReturnCode.declined());
    }
}
