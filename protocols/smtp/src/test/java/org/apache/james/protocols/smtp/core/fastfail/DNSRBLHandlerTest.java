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

import static org.apache.james.protocols.api.ProtocolSession.State.Connection;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.Before;
import org.junit.Test;

public class DNSRBLHandlerTest {

    private SMTPSession mockedSMTPSession;

    private String remoteIp = "127.0.0.2";

    private boolean relaying = false;   
    
    public static final String RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.blocklisted";
    
    public static final String RBL_DETAIL_MAIL_ATTRIBUTE_NAME = "org.apache.james.smtpserver.rbl.detail";

    @Before
    public void setUp() throws Exception {
        setRelayingAllowed(false);
    }

    /**
     * Set the remoteIp
     * 
     * @param remoteIp The remoteIP to set
     */
    private void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    /**
     * Set relayingAllowed
     * 
     * @param relaying true or false
     */
    private void setRelayingAllowed(boolean relaying) {
        this.relaying = relaying;
    }

    /**
     * Setup the mocked dnsserver
     *
     */
    private DNSRBLHandler createHandler() {
        return new DNSRBLHandler() {

            @Override
            protected boolean resolve(String host) {
                if ("2.0.0.127.bl.spamcop.net.".equals(host)) {
                    return true;
                } else if ("3.0.0.127.bl.spamcop.net.".equals(host)) {
                    return true;
                } else if ("1.0.168.192.bl.spamcop.net.".equals(host)) {
                    return false;
                }
                throw new UnsupportedOperationException("getByName(" + host + ") not implemented in DNSRBLHandlerTest mock");
            }

            @Override
            protected Collection<String> resolveTXTRecords(String hostname) {
                List<String> res = new ArrayList<>();
                if (hostname == null) {
                    return res;
                }
                if ("2.0.0.127.bl.spamcop.net.".equals(hostname)) {
                    res.add("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
                }
                return res;
            }
            
        };
       
        
    }

    /**
     * Setup mocked smtpsession
     */
    private void setupMockedSMTPSession(MailAddress rcpt) {
        mockedSMTPSession = new BaseFakeSMTPSession() {
            HashMap<String,Object> sessionState = new HashMap<>();
            HashMap<String,Object> connectionState = new HashMap<>();
            
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(getRemoteIPAddress(), 10000);
            }

            public String getRemoteIPAddress() {
                return remoteIp;
            }

            @Override
            public Map<String,Object> getState() {
                return sessionState;
            }

            @Override
            public boolean isRelayingAllowed() {
                return relaying;
            }

            @Override
            public boolean isAuthSupported() {
                return false;
            }

            @Override
            public int getRcptCount() {
                return 0;
            }
            
            @Override
            public Object setAttachment(String key, Object value, State state) {
                if (state == State.Connection) {
                    if (value == null) {
                        return connectionState.remove(key);
                    } else {
                        return connectionState.put(key, value);
                    }
                } else {
                    if (value == null) {
                        return sessionState.remove(key);
                    } else {
                        return sessionState.put(key, value);
                    }
                }
            }

            @Override
            public Object getAttachment(String key, State state) {
                if (state == State.Connection) {
                    return connectionState.get(key);
                } else {
                    return sessionState.get(key);
                }
            }

        };
    }

    // ip is blacklisted and has txt details
    @Test
    public void testBlackListedTextPresent() throws Exception {
        DNSRBLHandler rbl = createHandler();
       
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, State.Connection)).describedAs("Details").isEqualTo("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Blocked").isNotNull();
    }

    // ip is blacklisted and has txt details but we don'T want to retrieve the txt record
    @Test
    public void testGetNoDetail() throws Exception {
        DNSRBLHandler rbl = createHandler();
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(false);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("No details").isNull();
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Blocked").isNotNull();
    }

    // ip is allowed to relay
    @Test
    public void testRelayAllowed() throws Exception {
        DNSRBLHandler rbl = createHandler();
        setRelayingAllowed(true);
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("No details").isNull();
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Not blocked").isNull();
    }

    // ip not on blacklist
    @Test
    public void testNotBlackListed() throws Exception {
        DNSRBLHandler rbl = createHandler();

        setRemoteIp("192.168.0.1");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("No details").isNull();
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Not blocked").isNull();
    }

    // ip on blacklist without txt details
    @Test
    public void testBlackListedNoTxt() throws Exception {
        DNSRBLHandler rbl = createHandler();

        setRemoteIp("127.0.0.3");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setBlacklist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, Connection)).isNull();
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Blocked").isNotNull();
    }

    // ip on whitelist
    @Test
    public void testWhiteListed() throws Exception {
        DNSRBLHandler rbl = createHandler();

        setRemoteIp("127.0.0.2");
        setupMockedSMTPSession(new MailAddress("any@domain"));

        rbl.setWhitelist(new String[] { "bl.spamcop.net." });
        rbl.setGetDetail(true);
        rbl.doRcpt(mockedSMTPSession, MaybeSender.nullSender(), new MailAddress("test@localhost"));
        assertThat(mockedSMTPSession.getAttachment(RBL_DETAIL_MAIL_ATTRIBUTE_NAME, Connection)).isNull();
        assertThat(mockedSMTPSession.getAttachment(RBL_BLOCKLISTED_MAIL_ATTRIBUTE_NAME, Connection)).withFailMessage("Not blocked").isNull();
    }
   

}
