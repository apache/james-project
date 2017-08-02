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
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Test;

public class ResolvableEhloHeloHandlerTest {

    public final static String INVALID_HOST = "foo.bar";
    
    public final static String VALID_HOST = "james.apache.org";


    private SMTPSession setupMockSession(String argument,
             final boolean relaying, final boolean authRequired, final String user, MailAddress recipient) {

        return new BaseFakeSMTPSession() {

            HashMap<String,Object> connectionMap = new HashMap<>();
            HashMap<String,Object> map = new HashMap<>();

            public boolean isAuthSupported() {
                return authRequired;
            }

            public String getUser() {
                return user;
            }

            public Map<String,Object> getConnectionState() {
                return connectionMap;
            }

            public boolean isRelayingAllowed() {
                return relaying;
            }

            public Map<String,Object> getState() {
                return map;
            }

            /*
             * (non-Javadoc)
             * @see org.apache.james.protocols.api.ProtocolSession#setAttachment(java.lang.String, java.lang.Object, org.apache.james.protocols.api.ProtocolSession.State)
             */
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

            /*
             * (non-Javadoc)
             * @see org.apache.james.protocols.api.ProtocolSession#getAttachment(java.lang.String, org.apache.james.protocols.api.ProtocolSession.State)
             */
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
                if (host.equals(INVALID_HOST)) 
                    throw new UnknownHostException();
                return InetAddress.getLocalHost().getHostName();
            }
            
        };
    }
    
    @Test
    public void testRejectInvalidHelo() throws MailAddressException {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,false,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();
        
        handler.doHelo(session, INVALID_HOST);
        assertNotNull("Invalid HELO",session.getAttachment(ResolvableEhloHeloHandler.BAD_EHLO_HELO, State.Transaction));
        
        int result = handler.doRcpt(session,null, mailAddress).getResult();
        assertEquals("Reject", result,HookReturnCode.DENY);
    }
    
    @Test
    public void testNotRejectValidHelo() throws MailAddressException {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(VALID_HOST,false,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();

  
        handler.doHelo(session, VALID_HOST);
        assertNull("Valid HELO",session.getAttachment(ResolvableEhloHeloHandler.BAD_EHLO_HELO, State.Transaction));

        int result = handler.doRcpt(session,null, mailAddress).getResult();
        assertEquals("Not reject", result,HookReturnCode.DECLINED);
    }
   
    @Test
    public void testRejectInvalidHeloAuthUser() throws MailAddressException {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,false,true,"valid@user",mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertNotNull("Value stored",session.getAttachment(ResolvableEhloHeloHandler.BAD_EHLO_HELO, State.Transaction));
        
        
        int result = handler.doRcpt(session,null, mailAddress).getResult();
        assertEquals("Reject", result,HookReturnCode.DENY);
    }
    
   
    @Test
    public void testRejectRelay() throws MailAddressException {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST,true,false,null,mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertNotNull("Value stored",session.getAttachment(ResolvableEhloHeloHandler.BAD_EHLO_HELO, State.Transaction));
        
        
        int result = handler.doRcpt(session,null, mailAddress).getResult();
        assertEquals("Reject", result,HookReturnCode.DENY);
    }
}
    
