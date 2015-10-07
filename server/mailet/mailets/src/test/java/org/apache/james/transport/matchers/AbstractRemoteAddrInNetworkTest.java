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
package org.apache.james.transport.matchers;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;

public abstract class AbstractRemoteAddrInNetworkTest {

    protected Mail mockedMail;
    protected AbstractNetworkMatcher matcher;
    private String remoteAddr;
    private DNSService dnsServer;

    protected void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    protected void setupMockedMail() {
        mockedMail = new Mail() {

            private static final long serialVersionUID = 1L;

            @Override
            public String getName() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setName(String newName) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public MimeMessage getMessage() throws MessagingException {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Collection<MailAddress> getRecipients() {
                ArrayList<MailAddress> r = new ArrayList<MailAddress>();
                try {
                    r = new ArrayList<MailAddress>(Arrays.asList(new MailAddress[]{new MailAddress(
                                "test@james.apache.org")}));
                } catch (ParseException e) {
                }
                return r;
            }

            @Override
            public void setRecipients(Collection recipients) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public MailAddress getSender() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public String getState() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public String getRemoteHost() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public String getRemoteAddr() {
                return remoteAddr;
            }

            @Override
            public String getErrorMessage() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setErrorMessage(String msg) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setMessage(MimeMessage message) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setState(String state) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Serializable getAttribute(String name) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Iterator getAttributeNames() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public boolean hasAttributes() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Serializable removeAttribute(String name) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void removeAllAttributes() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Serializable setAttribute(String name, Serializable object) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public long getMessageSize() throws MessagingException {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public Date getLastUpdated() {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }

            @Override
            public void setLastUpdated(Date lastUpdated) {
                throw new UnsupportedOperationException("Unimplemented mock service");
            }
        };

    }

    protected void setupDNSServer() {
        dnsServer = new MockDNSService() {

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                if ("192.168.200.0".equals(host)
                        || "255.255.255.0".equals(host)
                        || "192.168.200.1".equals(host)
                        || "192.168.0.1".equals(host)
                        || "192.168.1.1".equals(host)) {
                    // called with an IP it only check formal validity
                    return InetAddress.getByName(host);
                }
                throw new UnsupportedOperationException(
                        "getByName(" + host + ") unimplemented in AbstractRemoteAddrInNetworkTest");
            }
        };
    }

    protected void setupMatcher() throws MessagingException {

        FakeMailContext mmc = new FakeMailContext();
        matcher = createMatcher();
        matcher.setDNSService(dnsServer);
        FakeMatcherConfig mci = new FakeMatcherConfig(getConfigOption() + getAllowedNetworks(), mmc);
        matcher.init(mci);
    }

    protected void setupAll() throws MessagingException {
        setupDNSServer();
        setupMockedMail();
        setupMatcher();
    }

    protected abstract String getConfigOption();

    protected abstract String getAllowedNetworks();

    protected abstract AbstractNetworkMatcher createMatcher();
}
