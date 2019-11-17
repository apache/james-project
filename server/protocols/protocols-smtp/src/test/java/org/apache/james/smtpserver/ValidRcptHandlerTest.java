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

import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.Before;
import org.junit.Test;

public class ValidRcptHandlerTest {
    private static final Username VALID_USER = Username.of("postmaster");
    private static final String INVALID_USER = "invalid";
    private static final String USER1 = "user1";
    private static final String USER2 = "user2";
    private static final String PASSWORD = "xxx";
    private static final boolean RELAYING_ALLOWED = true;
    private static final MaybeSender MAYBE_SENDER = MaybeSender.of(SENDER);

    private ValidRcptHandler handler;
    private MemoryRecipientRewriteTable memoryRecipientRewriteTable;
    private MailAddress validUserEmail;
    private MailAddress user1mail;
    private MailAddress invalidUserEmail;

    @Before
    public void setUp() throws Exception {
        MemoryDomainList memoryDomainList = new MemoryDomainList(mock(DNSService.class));
        memoryDomainList.configure(DomainListConfiguration.builder()
            .defaultDomain(Domain.LOCALHOST)
            .build());
        UsersRepository users = MemoryUsersRepository.withoutVirtualHosting(memoryDomainList);
        users.addUser(VALID_USER, PASSWORD);

        memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
        memoryRecipientRewriteTable.setDomainList(memoryDomainList);

        handler = new ValidRcptHandler(users, memoryRecipientRewriteTable, memoryDomainList);

        validUserEmail = new MailAddress(VALID_USER.asString() + "@localhost");
        user1mail = new MailAddress(USER1 + "@localhost");
        invalidUserEmail = new MailAddress(INVALID_USER + "@localhost");
    }

    private SMTPSession setupMockedSMTPSession(boolean relayingAllowed) {
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

    @Test
    public void doRcptShouldRejectNotExistingLocalUsersWhenNoRelay() {
        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, invalidUserEmail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.deny());
    }

    @Test
    public void doRcptShouldDenyNotExistingLocalUsersWhenRelay() {
        SMTPSession session = setupMockedSMTPSession(RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, invalidUserEmail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.deny());
    }

    @Test
    public void doRcptShouldDeclineNonLocalUsersWhenRelay() throws Exception {
        MailAddress mailAddress = new MailAddress(INVALID_USER + "@otherdomain");
        SMTPSession session = setupMockedSMTPSession(RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, mailAddress).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDeclineNonLocalUsersWhenNoRelay() throws Exception {
        MailAddress mailAddress = new MailAddress(INVALID_USER + "@otherdomain");
        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, mailAddress).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDeclineValidUsersWhenNoRelay() throws Exception {
        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, validUserEmail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDeclineValidUsersWhenRelay() throws Exception {
        SMTPSession session = setupMockedSMTPSession(RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, validUserEmail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDeclineWhenHasAddressMapping() throws Exception {
        memoryRecipientRewriteTable.addAddressMapping(MappingSource.fromUser(USER1, Domain.LOCALHOST), "address");

        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, validUserEmail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDenyWhenHasMappingLoop() throws Exception {
        memoryRecipientRewriteTable.addAddressMapping(MappingSource.fromUser(USER1, Domain.LOCALHOST), USER2 + "@localhost");
        memoryRecipientRewriteTable.addAddressMapping(MappingSource.fromUser(USER2, Domain.LOCALHOST), USER1 + "@localhost");

        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, user1mail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void doRcptShouldDeclineWhenHasErrorMapping() throws Exception {
        memoryRecipientRewriteTable.addErrorMapping(MappingSource.fromUser(USER1, Domain.LOCALHOST), "554 BOUNCE");

        SMTPSession session = setupMockedSMTPSession(!RELAYING_ALLOWED);

        HookReturnCode rCode = handler.doRcpt(session, MAYBE_SENDER, user1mail).getResult();

        assertThat(rCode).isEqualTo(HookReturnCode.declined());
    }
    
}
