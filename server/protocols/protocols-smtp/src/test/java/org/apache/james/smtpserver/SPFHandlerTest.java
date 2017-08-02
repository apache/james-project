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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.james.jspf.core.DNSRequest;
import org.apache.james.jspf.core.DNSService;
import org.apache.james.jspf.core.exceptions.TimeoutException;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.james.smtpserver.fastfail.SPFHandler;
import org.junit.Before;
import org.junit.Test;

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
                        if (req.getHostname().equals("spf1.james.apache.org")) {
                            // pass
                            l.add("v=spf1 +all");
                            return l;
                        } else if (req.getHostname().equals("spf2.james.apache.org")) {
                            // fail
                            l.add("v=spf1 -all");
                            return l;
                        } else if (req.getHostname().equals("spf3.james.apache.org")) {
                            // softfail
                            l.add("v=spf1 ~all");
                            return l;
                        } else if (req.getHostname().equals("spf4.james.apache.org")) {
                            // permerror
                            l.add("v=spf1 badcontent!");
                            return l;
                        } else if (req.getHostname().equals("spf5.james.apache.org")) {
                            // temperror
                            throw new TimeoutException("TIMEOUT");
                        } else {
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
                sstate.put(SMTPSession.CURRENT_HELO_NAME, helo);

                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sstate.get(key);
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
        MailAddress sender = new MailAddress("test@spf1.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf1.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("declined", HookReturnCode.DECLINED, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFfail() throws Exception {
        MailAddress sender = new MailAddress("test@spf2.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf2.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("fail", HookReturnCode.DENY, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFsoftFail() throws Exception {
        MailAddress sender = new MailAddress("test@spf3.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf3.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("softfail declined", HookReturnCode.DECLINED,
                spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFsoftFailRejectEnabled() throws Exception {
        MailAddress sender = new MailAddress("test@spf3.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf3.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockSoftFail(true);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("softfail reject", HookReturnCode.DENY, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFpermError() throws Exception {
        MailAddress sender = new MailAddress("test@spf4.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf4.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockSoftFail(true);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("permerror reject", HookReturnCode.DENY, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFtempError() throws Exception {
        MailAddress sender = new MailAddress("test@spf5.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf5.james.apache.org");

        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("temperror denysoft", HookReturnCode.DENYSOFT, spf.doRcpt(mockedSMTPSession, sender, rcpt).
                getResult());
    }

    @Test
    public void testSPFNoRecord() throws Exception {
        MailAddress sender = new MailAddress("test@spf6.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");

        setupMockedSMTPSession("192.168.100.1", "spf6.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("declined", HookReturnCode.DECLINED, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }

    @Test
    public void testSPFpermErrorRejectDisabled() throws Exception {
        MailAddress sender = new MailAddress("test@spf4.james.apache.org");
        MailAddress rcpt = new MailAddress("test@localhost");
        setupMockedSMTPSession("192.168.100.1", "spf4.james.apache.org");
        SPFHandler spf = new SPFHandler();

        spf.setDNSService(mockedDnsService);

        spf.setBlockPermError(false);

        assertEquals("declined", HookReturnCode.DECLINED, spf.doMail(mockedSMTPSession, sender).getResult());
        assertEquals("declined", HookReturnCode.DECLINED, spf.doRcpt(mockedSMTPSession, sender, rcpt).getResult());
    }
}
