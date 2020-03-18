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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.jspf.core.DNSRequest;
import org.apache.james.jspf.core.DNSService;
import org.apache.james.jspf.core.exceptions.TimeoutException;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.SPFHandler;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Preconditions;

public class SPFHandlerTest {

    private DNSService mockedDnsService;
    private SMTPSession mockedSMTPSession;

    private boolean relaying = false;

    @Before
    public void setUp() throws Exception {
        setupMockedDnsService();
        setRelayingAllowed(false);
    }

    /**
     * Set relayingAllowed
     * 
     * @param relaying
     *            true or false
     */
    private void setRelayingAllowed(boolean relaying) {
        this.relaying = relaying;
    }

    /**
     * Setup the mocked dnsserver
     * 
     */
    private void setupMockedDnsService() {
        mockedDnsService = new DNSService() {

            @Override
            public List<String> getLocalDomainNames() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setTimeOut(int arg0) {
                // do nothing
            }

            @Override
            public int getRecordLimit() {
                return 0;
            }

            @Override
            public void setRecordLimit(int arg0) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public List<String> getRecords(DNSRequest req) throws TimeoutException {
                switch (req.getRecordType()) {
                    case DNSRequest.TXT:
                    case DNSRequest.SPF:
                        List<String> l = new ArrayList<>();
                        switch (req.getHostname()) {
                            case "spf1.james.apache.org":
                                // pass
                                l.add("v=spf1 +all");
                                return l;
                            case "spf2.james.apache.org":
                                // fail
                                l.add("v=spf1 -all");
                                return l;
                            case "spf3.james.apache.org":
                                // softfail
                                l.add("v=spf1 ~all");
                                return l;
                            case "spf4.james.apache.org":
                                // permerror
                                l.add("v=spf1 badcontent!");
                                return l;
                            case "spf5.james.apache.org":
                                // temperror
                                throw new TimeoutException("TIMEOUT");
                            default:
                                return null;
                        }
                    default:
                        throw new UnsupportedOperationException("Unimplemented mock service");
                }
            }
        };
    }

    /**
     * Setup mocked smtpsession
     */
    private void setupMockedSMTPSession(String ip, final String helo) {
        mockedSMTPSession = new BaseFakeSMTPSession() {

            private final HashMap<AttachmentKey<?>, Object> sessionState = new HashMap<>();
            private final HashMap<AttachmentKey<?>, Object> connectionState = new HashMap<>();

            @Override
            public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");
                Preconditions.checkNotNull(value, "value cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.put(key, value));
                } else {
                    return key.convert(sessionState.put(key, value));
                }
            }

            @Override
            public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
                Preconditions.checkNotNull(key, "key cannot be null");

                if (state == State.Connection) {
                    return key.convert(connectionState.remove(key));
                } else {
                    return key.convert(sessionState.remove(key));
                }
            }

            @Override
            public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
                sessionState.put(SMTPSession.CURRENT_HELO_NAME, helo);

                if (state == State.Connection) {
                    return key.convert(connectionState.get(key));
                } else {
                    return key.convert(sessionState.get(key));
                }
            }

            @Override
            public boolean isRelayingAllowed() {
                return relaying;
            }

            @Override
            public int getRcptCount() {
                return 0;
            }
        };
    }

    @Test
    public void testSPFpass() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf1.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf1.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void testSPFfail() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf2.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf2.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("fail").isEqualTo(HookReturnCode.deny());
    }

    @Test
    public void testSPFsoftFail() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf3.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf3.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("softfail declined").isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void testSPFsoftFailRejectEnabled() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf3.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf3.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockSoftFail(true);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("softfail reject").isEqualTo(HookReturnCode.deny());
    }

    @Test
    public void testSPFpermError() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf4.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf4.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockSoftFail(true);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("permerror reject").isEqualTo(HookReturnCode.deny());
    }

    @Test
    public void testSPFtempError() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf5.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf5.james.apache.org");

        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("temperror denysoft").isEqualTo(HookReturnCode.denySoft());
    }

    @Test
    public void testSPFNoRecord() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf6.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf6.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
    }

    @Test
    public void testSPFpermErrorRejectDisabled() throws Exception {
        MaybeSender sender = MaybeSender.of(new MailAddress("test@spf4.james.apache.org"));
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf4.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockPermError(false);

        assertThat(spf.doMail(mockedSMTPSession, sender).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
        assertThat(spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult()).describedAs("declined").isEqualTo(HookReturnCode.declined());
    }
}
