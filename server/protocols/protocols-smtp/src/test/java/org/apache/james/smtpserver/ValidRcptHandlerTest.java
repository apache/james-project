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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.mock.MockUsersRepository;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

public class ValidRcptHandlerTest {

    private final static String VALID_DOMAIN = "localhost";
    private final static String VALID_USER = "postmaster";
    private final static String INVALID_USER = "invalid";
    private final static String USER1 = "user1";
    private final static String USER2 = "user2";
    UsersRepository users;
    ValidRcptHandler handler;

    @Before
    public void setUp() throws Exception {

        users = new MockUsersRepository();
        users.addUser(VALID_USER, "xxx");
        
        handler = new ValidRcptHandler();
        handler.setUsersRepository(users);
        handler.setRecipientRewriteTable(setUpRecipientRewriteTable());

        handler.setDomainList(new SimpleDomainList() {

            @Override
            public boolean containsDomain(String domain) {
                return domain.equals(VALID_DOMAIN);
            }
        });
    }

    private SMTPSession setupMockedSMTPSession(final SMTPConfiguration conf, final MailAddress rcpt,
                                               final boolean relayingAllowed) {
        SMTPSession session = new BaseFakeSMTPSession() {

            @Override
            public boolean isRelayingAllowed() {
                return relayingAllowed;
            }
            private final HashMap<String, Object> sstate = new HashMap<String, Object>();
            private final HashMap<String, Object> connectionState = new HashMap<String, Object>();

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

        return session;
    }

    private RecipientRewriteTable setUpRecipientRewriteTable() {
        final RecipientRewriteTable table = new RecipientRewriteTable() {

            @Override
            public Collection<String> getMappings(String user, String domain) throws ErrorMappingException,
                    RecipientRewriteTableException {
                Collection<String> mappings = new ArrayList<String>();
                if (user.equals(USER1)) {
                    mappings.add("address@localhost");
                } else if (user.equals(USER2)) {
                    throw new ErrorMappingException("554 BOUNCE");
                }
                return mappings;
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
            public Collection<String> getUserDomainMappings(String user, String domain) throws
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
            public Map<String, Collection<String>> getAllMappings() throws RecipientRewriteTableException {
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
        return table;
    }

    private SMTPConfiguration setupMockedSMTPConfiguration() {
        SMTPConfiguration conf = new SMTPConfiguration() {

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
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String getSoftwareName() {
                // TODO Auto-generated method stub
                return null;
            }
        };

        return conf;
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
