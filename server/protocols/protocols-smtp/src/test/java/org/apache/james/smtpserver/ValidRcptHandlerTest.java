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
package org.apache.james.smtpserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.mock.InMemoryUsersRepository;
import org.junit.Before;
import org.junit.Test;

public class ValidRcptHandlerTest {

    private static final String VALID_DOMAIN = "localhost";
    private static final String VALID_USER = "postmaster";
    private static final String INVALID_USER = "invalid";
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";
    UsersRepository users;
    ValidRcptHandler handler;

    @Before
    public void setUp() throws Exception {

        users = new InMemoryUsersRepository();
        users.addUser(VALID_USER, "xxx");
        
        handler = new ValidRcptHandler();
        handler.setUsersRepository(users);
        handler.setRecipientRewriteTable(setUpRecipientRewriteTable());

        MemoryDomainList memoryDomainList = new MemoryDomainList(mock(DNSService.class));
        memoryDomainList.addDomain(VALID_DOMAIN);
        handler.setDomainList(memoryDomainList);
    }

    private SMTPSession setupMockedSMTPSession(SMTPConfiguration conf, MailAddress rcpt,
                                               final boolean relayingAllowed) {

        return new BaseFakeSMTPSession() {

            @Override
            public boolean isRelayingAllowed() {
                return relayingAllowed;
            }
            
            private final HashMap<String, Object> sstate = new HashMap<>();
            private final HashMap<String, Object> connectionState = new HashMap<>();

            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionState.remove(key);
                    }
                    return connectionState.put(key, value);
                } else {
                    if (value == null) {
                        return sstate.remove(key);
                    }
                    return sstate.put(key, value);
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sstate.get(key);
                }
            }
        };
    }

    private RecipientRewriteTable setUpRecipientRewriteTable() {
        return new RecipientRewriteTable() {

            @Override
            public Mappings getMappings(String user, String domain) throws ErrorMappingException,
                    RecipientRewriteTableException {
                if (user.equals(USER1)) {
                    return MappingsImpl.fromCollection(Arrays.asList("address@localhost"));
                } else if (user.equals(USER2)) {
                    throw new ErrorMappingException("554 BOUNCE");
                }
                return MappingsImpl.empty();
            }

            @Override
            public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public void removeRegexMapping(String user, String domain, String regex) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void addAddressMapping(String user, String domain, String address) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void removeAddressMapping(String user, String domain, String address) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void removeErrorMapping(String user, String domain, String error) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public Mappings getUserDomainMappings(String user, String domain) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public void addAliasDomainMapping(String aliasDomain, String realDomain) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }

            @Override
            public void removeAliasDomainMapping(String aliasDomain, String realDomain) throws
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");

            }
        };
    }

    private SMTPConfiguration setupMockedSMTPConfiguration() {

        return new SMTPConfiguration() {

            @Override
            public String getHelloName() {
                throw new UnsupportedOperationException("Unimplemented Stub Method");
            }

            @Override
            public long getMaxMessageSize() {
                throw new UnsupportedOperationException("Unimplemented Stub Method");
            }

            @Override
            public boolean isRelayingAllowed(String remoteIP) {
                throw new UnsupportedOperationException("Unimplemented Stub Method");
            }

            @Override
            public boolean useHeloEhloEnforcement() {
                throw new UnsupportedOperationException("Unimplemented Stub Method");
            }

            @Override
            public boolean useAddressBracketsEnforcement() {
                return true;
            }

            @Override
            public boolean isAuthRequired(String remoteIP) {
                throw new UnsupportedOperationException("Unimplemented Stub Method");
            }

            @Override
            public String getGreeting() {
                return null;
            }

            @Override
            public String getSoftwareName() {
                return null;
            }
        };
    }

    @Test
    public void testRejectInvalidUser() throws Exception {
        MailAddress mailAddress = new MailAddress(INVALID_USER + "@localhost");
        SMTPSession session = setupMockedSMTPSession(setupMockedSMTPConfiguration(), mailAddress, false);

        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertEquals("Rejected", rCode, HookReturnCode.DENY);
    }

    @Test
    public void testRejectInvalidUserRelay() throws Exception {
        MailAddress mailAddress = new MailAddress(INVALID_USER + "@localhost");
        SMTPSession session = setupMockedSMTPSession(setupMockedSMTPConfiguration(), mailAddress, true);

        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertEquals("Rejected", rCode, HookReturnCode.DENY);
    }

    @Test
    public void testNotRejectValidUser() throws Exception {
        MailAddress mailAddress = new MailAddress(VALID_USER + "@localhost");
        SMTPSession session = setupMockedSMTPSession(setupMockedSMTPConfiguration(), mailAddress, false);

        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertEquals("Not rejected", rCode, HookReturnCode.DECLINED);
    }

    @Test
    public void testHasAddressMapping() throws Exception {
        MailAddress mailAddress = new MailAddress(USER1 + "@localhost");
        SMTPSession session = setupMockedSMTPSession(setupMockedSMTPConfiguration(), mailAddress, false);

        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertEquals("Not rejected", rCode, HookReturnCode.DECLINED);
    }

    @Test
    public void testHasErrorMapping() throws Exception {
        MailAddress mailAddress = new MailAddress(USER2 + "@localhost");
        SMTPSession session = setupMockedSMTPSession(setupMockedSMTPConfiguration(), mailAddress, false);

        int rCode = handler.doRcpt(session, null, mailAddress).getResult();

        assertNull("Valid Error mapping", session.getAttachment("VALID_USER", State.Transaction));
        assertEquals("Error mapping", rCode, HookReturnCode.DENY);
    }
    
}
