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

import static org.apache.james.protocols.api.ProtocolSession.State.Transaction;
import static org.apache.james.protocols.smtp.core.fastfail.ResolvableEhloHeloHandler.BAD_EHLO_HELO;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;

public class ResolvableEhloHeloHandlerTest {

    public static final String INVALID_HOST = "foo.bar";
    
    public static final String VALID_HOST = "james.apache.org";


    private SMTPSession setupMockSession(String argument,
             final boolean relaying, final boolean authRequired, final String user, MailAddress recipient) {

        return new BaseFakeSMTPSession() {

            HashMap<String,Object> connectionMap = new HashMap<>();
            HashMap<String,Object> map = new HashMap<>();

            @Override
            public boolean isAuthSupported() {
                return authRequired;
            }

            @Override
            public String getUser() {
                return user;
            }

            @Override
            public Map<String,Object> getConnectionState() {
                return connectionMap;
            }

            @Override
            public boolean isRelayingAllowed() {
                return relaying;
            }

            @Override
            public Map<String,Object> getState() {
                return map;
            }

            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionMap.remove(key);
                    } else {
                        return connectionMap.put(key, value);
                    }
                } else {
                    if (value == null) {
                        return map.remove(key);
                    } else {
                        return connectionMap.put(key, value);
                    }
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    return connectionMap.get(key);
                } else {
                    return connectionMap.get(key);
                }
            }

        };
    }
    
    private ResolvableEhloHeloHandler createHandler() {
        return new ResolvableEhloHeloHandler() {

            @Override
            protected String resolve(String host) throws UnknownHostException {
                if (host.equals(INVALID_HOST)) {
                    throw new UnknownHostException();
                }
                return InetAddress.getLocalHost().getHostName();
            }
            
        };
    }
    
    @Test
    public void testRejectInvalidHelo() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,false,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();
        
        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Invalid HELO").isNotNull();

        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
    
    @Test
    public void testNotRejectValidHelo() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(VALID_HOST,false,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();

  
        handler.doHelo(session, VALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Valid HELO").isNull();

        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.declined()).describedAs("Not reject").isEqualTo(result);
    }
   
    @Test
    public void testRejectInvalidHeloAuthUser() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,false,true,"valid@user",mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Value stored").isNotNull();


        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
    
   
    @Test
    public void testRejectRelay() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,true,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Value stored").isNotNull();


        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
}
    
