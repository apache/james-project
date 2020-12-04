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
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.Test;

import com.google.common.base.Preconditions;

public class ResolvableEhloHeloHandlerTest {

    public static final String INVALID_HOST = "foo.bar";
    
    public static final String VALID_HOST = "james.apache.org";


    private SMTPSession setupMockSession(String argument,
             final boolean relaying, final boolean authRequired, final Username username, MailAddress recipient) {

        return new BaseFakeSMTPSession() {

            HashMap<AttachmentKey<?>, Object> connectionMap = new HashMap<>();
            HashMap<AttachmentKey<?>, Object> map = new HashMap<>();

            @Override
            public boolean isAuthSupported() {
                return authRequired;
            }

            @Override
            public Username getUsername() {
                return username;
            }

            @Override
            public Map<AttachmentKey<?>, Object> getConnectionState() {
                return connectionMap;
            }

            @Override
            public boolean isRelayingAllowed() {
                return relaying;
            }

            @Override
            public Map<AttachmentKey<?>, Object> getState() {
                return map;
            }

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionMap.put(key, value));
                } else {
                    return key.convert(map.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionMap.remove(key));
                } else {
                    return key.convert(map.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                if (state == State.Connection) {
                    return key.convert(connectionMap.get(key));
                } else {
                    return key.convert(map.get(key));
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
    void testRejectInvalidHelo() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST, false, false, null, mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();
        
        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Invalid HELO").isPresent();

        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
    
    @Test
    void testNotRejectValidHelo() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(VALID_HOST, false, false, null, mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();

  
        handler.doHelo(session, VALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Valid HELO").isEmpty();

        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.declined()).describedAs("Not reject").isEqualTo(result);
    }
   
    @Test
    void testRejectInvalidHeloAuthUser() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST, false, true, Username.of("valid@user"), mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Value stored").isPresent();


        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
    
   
    @Test
    void testRejectRelay() throws Exception {
        MailAddress mailAddress = new MailAddress("test@localhost");
        SMTPSession session = setupMockSession(INVALID_HOST, true, false, null, mailAddress);
        ResolvableEhloHeloHandler handler = createHandler();


        handler.doHelo(session, INVALID_HOST);
        assertThat(session.getAttachment(BAD_EHLO_HELO, Transaction)).withFailMessage("Value stored").isPresent();


        HookReturnCode result = handler.doRcpt(session, MaybeSender.nullSender(), mailAddress).getResult();
        assertThat(HookReturnCode.deny()).describedAs("Reject").isEqualTo(result);
    }
}
