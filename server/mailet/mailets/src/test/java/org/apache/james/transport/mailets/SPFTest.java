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

package org.apache.james.transport.mailets;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static org.apache.james.transport.mailets.SPF.RESULT_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.jspf.core.DNSRequest;
import org.apache.james.jspf.core.exceptions.TimeoutException;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

public class SPFTest {
    private static final String MAILET_NAME = "spf-mail";

    @Test
    public void serviceShouldSkipSPFCheck() throws MessagingException {
        FakeMail mail = fakeMail().build();
        Mailet mailet = testMailet(false, false, "10.0.0.0/8");

        mailet.service(mail);
        assertThat(mail.getAttribute(RESULT_ATTRIBUTE).isEmpty()).isTrue();
    }
    @Test
    public void serviceShouldPerformSPFCheckWithResultNone() throws MessagingException {
        FakeMail mail = fakeMail().build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("none");
    }

    @Test
    public void serviceShouldPerformSPFCheckWithResultPass() throws MessagingException {
        FakeMail mail = fakeMail().sender("hello@spf1.james.apache.org").build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("pass");
    }

    @Test
    public void serviceShouldPerformSPFCheckWithResultFail() throws MessagingException {
        FakeMail mail = fakeMail().sender("hello@spf2.james.apache.org").build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("fail");
    }

    @Test
    public void serviceShouldPerformSPFCheckWithResultSoftFail() throws MessagingException {
        FakeMail mail = fakeMail().sender("hello@spf3.james.apache.org").build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("softfail");
    }

    @Test
    public void serviceShouldPerformSPFCheckWithResultPermError() throws MessagingException {
        FakeMail mail = fakeMail().sender("hello@spf4.james.apache.org").build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("permerror");
    }

    @Test
    public void serviceShouldPerformSPFCheckWithResultTempError() throws MessagingException {
        FakeMail mail = fakeMail().sender("hello@spf5.james.apache.org").build();
        Mailet mailet = testMailet();

        mailet.service(mail);
        assertThat(AttributeUtils.getValueAndCastFromMail(mail, RESULT_ATTRIBUTE, String.class)
            .orElse(null)).isEqualTo("temperror");
    }

    private Mailet testMailet() throws MessagingException {
        return testMailet(false, false, "127.0.0.0/8");
    }

    private Mailet testMailet(boolean debug, boolean addHeader, String ignoreNetworks)
        throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(FakeMailContext.defaultContext())
            .setProperty("debug", String.valueOf(debug))
            .setProperty("addHeader", String.valueOf(addHeader))
            .setProperty("ignoreNetworks", ignoreNetworks)
            .build();

        SPF mailet = new SPF(mockedDnsService());
        mailet.setSPFDnsService(mockedSPFDnsService());
        mailet.init(mailetConfig);
        return mailet;
    }

    private FakeMail.Builder fakeMail() {
        return FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddress.nullSender())
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .remoteHost("some.host.local")
            .remoteAddr("10.11.12.13");
    }

    private DNSService mockedDnsService() {
        return new DNSService() {

            @Override
            public Collection<String> findMXRecords(String hostname) {
                throw new UnsupportedOperationException("not supported");
            }

            @Override
            public Collection<String> findTXTRecords(String hostname) {
                throw new UnsupportedOperationException("not supported");
            }

            @Override
            public Collection<InetAddress> getAllByName(String host) {
                throw new UnsupportedOperationException("not supported");
            }

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                return InetAddress.getByName(host);
            }

            @Override
            public InetAddress getLocalHost() {
                throw new UnsupportedOperationException("not supported");
            }

            @Override
            public String getHostName(InetAddress addr) {
                throw new UnsupportedOperationException("not supported");
            }
        };
    }

    private org.apache.james.jspf.core.DNSService mockedSPFDnsService() {
        return new org.apache.james.jspf.core.DNSService() {

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

}
