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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailrepository.mock.MockMailRepositoryStore;
import org.apache.james.protocols.lib.PortUtil;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.queue.api.mock.MockMailQueue;
import org.apache.james.queue.api.mock.MockMailQueueFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.user.lib.mock.MockUsersRepository;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SMTPServerTest {

    final class AlterableDNSServer implements DNSService {

        private InetAddress localhostByName = null;

        @Override
        public Collection<String> findMXRecords(String hostname) {
            List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            }
            if ("james.apache.org".equals(hostname)) {
                res.add("nagoya.apache.org");
            }
            return res;
        }

        public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
            throw new UnsupportedOperationException("Unimplemented mock service");
        }

        @Override
        public InetAddress[] getAllByName(String host) throws UnknownHostException {
            return new InetAddress[]{getByName(host)};
        }

        @Override
        public InetAddress getByName(String host) throws UnknownHostException {
            if (getLocalhostByName() != null) {
                if ("127.0.0.1".equals(host)) {
                    return getLocalhostByName();
                }
            }

            if ("1.0.0.127.bl.spamcop.net.".equals(host)) {
                return InetAddress.getByName("localhost");
            }

            if ("james.apache.org".equals(host)) {
                return InetAddress.getByName("james.apache.org");
            }

            if ("abgsfe3rsf.de".equals(host)) {
                throw new UnknownHostException();
            }

            if ("128.0.0.1".equals(host) || "192.168.0.1".equals(host) || "127.0.0.1".equals(host) || "127.0.0.0".equals(
                    host) || "255.0.0.0".equals(host) || "255.255.255.255".equals(host)) {
                return InetAddress.getByName(host);
            }

            throw new UnsupportedOperationException("getByName not implemented in mock for host: " + host);
        }

        @Override
        public Collection<String> findTXTRecords(String hostname) {
            List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            }

            if ("2.0.0.127.bl.spamcop.net.".equals(hostname)) {
                res.add("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
            }
            return res;
        }

        public InetAddress getLocalhostByName() {
            return localhostByName;
        }

        public void setLocalhostByName(InetAddress localhostByName) {
            this.localhostByName = localhostByName;
        }

        @Override
        public String getHostName(InetAddress addr) {
            return addr.getHostName();
        }

        @Override
        public InetAddress getLocalHost() throws UnknownHostException {
            return InetAddress.getLocalHost();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SMTPServerTest.class.getName());

    protected final int smtpListenerPort;
    
    protected SMTPTestConfiguration smtpConfiguration;
    protected final MockUsersRepository usersRepository = new MockUsersRepository();
    protected AlterableDNSServer dnsServer;
    protected MockMailRepositoryStore store;
    protected MockFileSystem fileSystem;
    protected DNSService dnsService;
    protected MockProtocolHandlerLoader chain;
    protected MockMailQueueFactory queueFactory;
    protected MockMailQueue queue;

    private SMTPServer smtpServer;

    public SMTPServerTest() {
        smtpListenerPort = PortUtil.getNonPrivilegedPort();
    }

    @Before
    public void setUp() throws Exception {
        setUpFakeLoader();
        // slf4j can't set programmatically any log level. It's just a facade
        // log.setLevel(SimpleLog.LOG_LEVEL_ALL);
        smtpConfiguration = new SMTPTestConfiguration(smtpListenerPort);
        setUpSMTPServer();
    }

    protected SMTPServer createSMTPServer() {
        return new SMTPServer();
    }

    protected void setUpSMTPServer() {
        
        Logger log = LoggerFactory.getLogger("SMTP");
        // slf4j can't set programmatically any log level. It's just a facade
        // log.setLevel(SimpleLog.LOG_LEVEL_ALL);
        smtpServer = createSMTPServer();
        smtpServer.setDnsService(dnsServer);
        smtpServer.setFileSystem(fileSystem);
        smtpServer.setProtocolHandlerLoader(chain);
        smtpServer.setLog(log);

    }

    protected void init(SMTPTestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        initSMTPServer(testConfiguration);
        // m_mailServer.setMaxMessageSizeBytes(m_testConfiguration.getMaxMessageSize() * 1024);
    }

    protected void initSMTPServer(SMTPTestConfiguration testConfiguration) throws Exception {
        smtpServer.configure(testConfiguration);
        smtpServer.init();
    }

    protected void setUpFakeLoader() {

        chain = new MockProtocolHandlerLoader();
    
        chain.put("usersrepository", usersRepository);
    
        dnsServer = new AlterableDNSServer();
        chain.put("dnsservice", dnsServer);
    
        store = new MockMailRepositoryStore();
        chain.put("mailStore", store);
        fileSystem = new MockFileSystem();
    
        chain.put("fileSystem", fileSystem);
        chain.put("org.apache.james.smtpserver.protocol.DNSService", dnsService);
    
        chain.put("recipientrewritetable", new RecipientRewriteTable() {
    
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
    
            @Override
            public Mappings getMappings(String user, String domain) throws ErrorMappingException,
                    RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }
        });
    
        chain.put("org.apache.james.smtpserver.protocol.DNSService", dnsService);
        queueFactory = new MockMailQueueFactory();
        queue = (MockMailQueue) queueFactory.getQueue(MockMailQueueFactory.SPOOL);
        chain.put("mailqueuefactory", queueFactory);
        chain.put("domainlist", new SimpleDomainList() {
    
            @Override
            public boolean containsDomain(String serverName) {
                return "localhost".equals(serverName);
            }
        });
        
    }

    @Test
    public void testMaxLineLength() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < AbstractChannelPipelineFactory.MAX_LINE_LENGTH; i++) {
            sb.append("A");
        }
        smtpProtocol.sendCommand("EHLO " + sb.toString());
        System.out.println(smtpProtocol.getReplyString());
        assertEquals("Line length exceed", 500, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("EHLO test");
        assertEquals("Line length ok", 250, smtpProtocol.getReplyCode());

        smtpProtocol.quit();
        smtpProtocol.disconnect();
    }

    @Test
    public void testConnectionLimit() throws Exception {
        smtpConfiguration.setConnectionLimit(2);
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);
        SMTPClient smtpProtocol2 = new SMTPClient();
        smtpProtocol2.connect("127.0.0.1", smtpListenerPort);

        SMTPClient smtpProtocol3 = new SMTPClient();

        try {
            smtpProtocol3.connect("127.0.0.1", smtpListenerPort);
            Thread.sleep(3000);
            fail("Shold disconnect connection 3");
        } catch (Exception e) {
        }

        smtpProtocol.quit();
        smtpProtocol.disconnect();
        smtpProtocol2.quit();
        smtpProtocol2.disconnect();

        smtpProtocol3.connect("127.0.0.1", smtpListenerPort);
        Thread.sleep(3000);

    }

    @After
    public void tearDown() throws Exception {
        queue.clear();
        smtpServer.destroy();
    }

    public void verifyLastMail(String sender, String recipient, MimeMessage msg) throws IOException, MessagingException {
        Mail mailData = queue.getLastMail();
        assertNotNull("mail received by mail server", mailData);

        if (sender == null && recipient == null && msg == null) {
            fail("no verification can be done with all arguments null");
        }

        if (sender != null) {
            assertEquals("sender verfication", sender, mailData.getSender().toString());
        }
        if (recipient != null) {
            assertTrue("recipient verfication", mailData.getRecipients().contains(new MailAddress(recipient)));
        }
        if (msg != null) {
            ByteArrayOutputStream bo1 = new ByteArrayOutputStream();
            msg.writeTo(bo1);
            ByteArrayOutputStream bo2 = new ByteArrayOutputStream();
            mailData.getMessage().writeTo(bo2);
            assertEquals(bo1.toString(), bo2.toString());
            assertEquals("message verification", msg, mailData.getMessage());
        }
    }

    @Test
    public void testSimpleMailSendWithEHLO() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol.sendCommand("EHLO " + InetAddress.getLocalHost());
        String[] capabilityRes = smtpProtocol.getReplyStrings();

        List<String> capabilitieslist = new ArrayList<String>();
        for (int i = 1; i < capabilityRes.length; i++) {
            capabilitieslist.add(capabilityRes[i].substring(4));
        }

        assertEquals("capabilities", 3, capabilitieslist.size());
        assertTrue("capabilities present PIPELINING", capabilitieslist.contains("PIPELINING"));
        assertTrue("capabilities present ENHANCEDSTATUSCODES", capabilitieslist.contains("ENHANCEDSTATUSCODES"));
        assertTrue("capabilities present 8BITMIME", capabilitieslist.contains("8BITMIME"));

        smtpProtocol.setSender("mail@localhost");
        smtpProtocol.addRecipient("mail@localhost");

        smtpProtocol.sendShortMessageData("Subject: test\r\n\r\nBody\r\n\r\n.\r\n");
        smtpProtocol.quit();
        smtpProtocol.disconnect();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testStartTLSInEHLO() throws Exception {
        smtpConfiguration.setStartTLS();
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol.sendCommand("EHLO " + InetAddress.getLocalHost());
        String[] capabilityRes = smtpProtocol.getReplyStrings();

        List<String> capabilitieslist = new ArrayList<String>();
        for (int i = 1; i < capabilityRes.length; i++) {
            capabilitieslist.add(capabilityRes[i].substring(4));
        }

        assertEquals("capabilities", 4, capabilitieslist.size());
        assertTrue("capabilities present PIPELINING", capabilitieslist.contains("PIPELINING"));
        assertTrue("capabilities present ENHANCEDSTATUSCODES", capabilitieslist.contains("ENHANCEDSTATUSCODES"));
        assertTrue("capabilities present 8BITMIME", capabilitieslist.contains("8BITMIME"));
        assertTrue("capabilities present STARTTLS", capabilitieslist.contains("STARTTLS"));

        smtpProtocol.quit();
        smtpProtocol.disconnect();

    }

    protected SMTPClient newSMTPClient() throws IOException {
        SMTPClient smtp = new SMTPClient();
        smtp.connect("127.0.0.1", smtpListenerPort);
        if (log.isDebugEnabled()) {
            smtp.addProtocolCommandListener(new ProtocolCommandListener() {

                @Override
                public void protocolCommandSent(ProtocolCommandEvent event) {
                    log.debug("> " + event.getMessage().trim());
                }

                @Override
                public void protocolReplyReceived(ProtocolCommandEvent event) {
                    log.debug("< " + event.getMessage().trim());
                }
            });
        }
        return smtp;
    }

    @Test
    public void testReceivedHeader() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtp = newSMTPClient();

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtp.helo(InetAddress.getLocalHost().toString());
        smtp.setSender("mail@localhost");
        smtp.addRecipient("mail@localhost");
        smtp.sendShortMessageData("Subject: test\r\n\r\n");

        smtp.quit();
        smtp.disconnect();

        assertNotNull("spooled mail has Received header",
                queue.getLastMail().getMessage().getHeader("Received"));
    }

    // FIXME
    @Ignore
    @Test
    public void testEmptyMessageReceivedHeader() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtp = newSMTPClient();

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtp.helo(InetAddress.getLocalHost().toString());
        smtp.setSender("mail@localhost");
        smtp.addRecipient("mail@localhost");
        smtp.sendShortMessageData("");

        smtp.quit();
        smtp.disconnect();

        assertNotNull("spooled mail has Received header",
                queue.getLastMail().getMessage().getHeader("Received"));
        // TODO: test body size
    }

    @Test
    public void testSimpleMailSendWithHELO() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol.helo(InetAddress.getLocalHost().toString());

        smtpProtocol.setSender("mail@localhost");

        smtpProtocol.addRecipient("mail@localhost");

        smtpProtocol.sendShortMessageData("Subject: test mail\r\n\r\nTest body testSimpleMailSendWithHELO\r\n.\r\n");

        smtpProtocol.quit();
        smtpProtocol.disconnect();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testTwoSimultaneousMails() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);
        SMTPClient smtpProtocol2 = new SMTPClient();
        smtpProtocol2.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());
        assertTrue("second connection taken", smtpProtocol2.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());
        smtpProtocol2.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@localhost";
        String recipient1 = "mail_recipient1@localhost";
        smtpProtocol1.setSender(sender1);
        smtpProtocol1.addRecipient(recipient1);

        String sender2 = "mail_sender2@localhost";
        String recipient2 = "mail_recipient2@localhost";
        smtpProtocol2.setSender(sender2);
        smtpProtocol2.addRecipient(recipient2);

        smtpProtocol1.sendShortMessageData("Subject: test\r\n\r\nTest body testTwoSimultaneousMails1\r\n.\r\n");
        verifyLastMail(sender1, recipient1, null);

        smtpProtocol2.sendShortMessageData("Subject: test\r\n\r\nTest body testTwoSimultaneousMails2\r\n.\r\n");
        verifyLastMail(sender2, recipient2, null);

        smtpProtocol1.quit();
        smtpProtocol2.quit();

        smtpProtocol1.disconnect();
        smtpProtocol2.disconnect();
    }

    @Test
    public void testTwoMailsInSequence() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@localhost";
        String recipient1 = "mail_recipient1@localhost";
        smtpProtocol1.setSender(sender1);
        smtpProtocol1.addRecipient(recipient1);

        smtpProtocol1.sendShortMessageData("Subject: test\r\n\r\nTest body testTwoMailsInSequence1\r\n");
        verifyLastMail(sender1, recipient1, null);

        String sender2 = "mail_sender2@localhost";
        String recipient2 = "mail_recipient2@localhost";
        smtpProtocol1.setSender(sender2);
        smtpProtocol1.addRecipient(recipient2);

        smtpProtocol1.sendShortMessageData("Subject: test2\r\n\r\nTest body2 testTwoMailsInSequence2\r\n");
        verifyLastMail(sender2, recipient2, null);

        smtpProtocol1.quit();
        smtpProtocol1.disconnect();
    }

    @Test
    public void testHeloResolv() throws Exception {
        smtpConfiguration.setHeloResolv();
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1");
        init(smtpConfiguration);

        doTestHeloEhloResolv("helo");
    }

    private void doTestHeloEhloResolv(String heloCommand) throws IOException {
        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        String fictionalDomain = "abgsfe3rsf.de";
        String existingDomain = "james.apache.org";
        String mail = "sender@james.apache.org";
        String rcpt = "rcpt@localhost";

        smtpProtocol.sendCommand(heloCommand, fictionalDomain);
        smtpProtocol.setSender(mail);
        smtpProtocol.addRecipient(rcpt);

        // this should give a 501 code cause the helo/ehlo could not resolved
        assertEquals("expected error: " + heloCommand + " could not resolved", 501, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand(heloCommand, existingDomain);
        smtpProtocol.setSender(mail);
        smtpProtocol.addRecipient(rcpt);

        if (smtpProtocol.getReplyCode() == 501) {
            fail(existingDomain + " domain currently cannot be resolved (check your DNS/internet connection/proxy settings to make test pass)");
        }
        // helo/ehlo is resolvable. so this should give a 250 code
        assertEquals(heloCommand + " accepted", 250, smtpProtocol.getReplyCode());

        smtpProtocol.quit();
    }

    @Test
    public void testHeloResolvDefault() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol1.helo("abgsfe3rsf.de");
        // helo should not be checked. so this should give a 250 code
        assertEquals("Helo accepted", 250, smtpProtocol1.getReplyCode());

        smtpProtocol1.quit();
    }

    @Test
    public void testReverseEqualsHelo() throws Exception {
        smtpConfiguration.setReverseEqualsHelo();
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1");
        // temporary alter the loopback resolution
        try {
            dnsServer.setLocalhostByName(InetAddress.getByName("james.apache.org"));
        } catch (UnknownHostException e) {
            fail("james.apache.org currently cannot be resolved (check your DNS/internet connection/proxy settings to make test pass)");
        }
        try {
            init(smtpConfiguration);

            SMTPClient smtpProtocol1 = new SMTPClient();
            smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

            assertTrue("first connection taken", smtpProtocol1.isConnected());

            // no message there, yet
            assertNull("no mail received by mail server", queue.getLastMail());

            String helo1 = "abgsfe3rsf.de";
            String helo2 = "james.apache.org";
            String mail = "sender";
            String rcpt = "recipient";

            smtpProtocol1.sendCommand("helo", helo1);
            smtpProtocol1.setSender(mail);
            smtpProtocol1.addRecipient(rcpt);

            // this should give a 501 code cause the helo not equal reverse of
            // ip
            assertEquals("expected error: helo not equals reverse of ip", 501, smtpProtocol1.getReplyCode());

            smtpProtocol1.sendCommand("helo", helo2);
            smtpProtocol1.setSender(mail);
            smtpProtocol1.addRecipient(rcpt);

            // helo is resolvable. so this should give a 250 code
            assertEquals("Helo accepted", 250, smtpProtocol1.getReplyCode());

            smtpProtocol1.quit();
        } finally {
            dnsServer.setLocalhostByName(null);
        }
    }

    @Test
    public void testSenderDomainResolv() throws Exception {
        smtpConfiguration.setSenderDomainResolv();
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1/32");
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@xfwrqqfgfe.de";

        smtpProtocol1.setSender(sender1);
        assertEquals("expected 501 error", 501, smtpProtocol1.getReplyCode());

        smtpProtocol1.addRecipient("test@localhost");
        assertEquals("Recipient not accepted cause no valid sender", 503, smtpProtocol1.getReplyCode());
        smtpProtocol1.quit();

    }

    @Test
    public void testSenderDomainResolvDefault() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@xfwrqqfgfe.de";

        smtpProtocol1.setSender(sender1);

        smtpProtocol1.quit();
    }

    @Test
    public void testSenderDomainResolvRelayClientDefault() throws Exception {
        smtpConfiguration.setSenderDomainResolv();
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@xfwrqqfgfe.de";

        // Both mail shold
        smtpProtocol1.setSender(sender1);

        smtpProtocol1.quit();

    }

    @Test
    public void testSenderDomainResolvRelayClient() throws Exception {
        smtpConfiguration.setSenderDomainResolv();
        smtpConfiguration.setCheckAuthNetworks(true);
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@xfwrqqfgfe.de";
        String sender2 = "mail_sender2@james.apache.org";

        smtpProtocol1.setSender(sender1);
        assertEquals("expected 501 error", 501, smtpProtocol1.getReplyCode());

        smtpProtocol1.setSender(sender2);

        smtpProtocol1.quit();

    }

    @Test
    public void testMaxRcpt() throws Exception {
        smtpConfiguration.setMaxRcpt(1);
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@james.apache.org";
        String rcpt1 = "test@localhost";
        String rcpt2 = "test2@localhost";

        smtpProtocol1.setSender(sender1);
        smtpProtocol1.addRecipient(rcpt1);

        smtpProtocol1.addRecipient(rcpt2);
        assertEquals("expected 452 error", 452, smtpProtocol1.getReplyCode());

        smtpProtocol1.sendShortMessageData("Subject: test\r\n\r\nTest body testMaxRcpt1\r\n");

        // After the data is send the rcpt count is set back to 0.. So a new
        // mail with rcpt should be accepted

        smtpProtocol1.setSender(sender1);

        smtpProtocol1.addRecipient(rcpt1);

        smtpProtocol1.sendShortMessageData("Subject: test\r\n\r\nTest body testMaxRcpt2\r\n");

        smtpProtocol1.quit();

    }

    @Test
    public void testMaxRcptDefault() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        String sender1 = "mail_sender1@james.apache.org";
        String rcpt1 = "test@localhost";

        smtpProtocol1.setSender(sender1);

        smtpProtocol1.addRecipient(rcpt1);

        smtpProtocol1.sendShortMessageData("Subject: test\r\n\r\nTest body testMaxRcptDefault\r\n");

        smtpProtocol1.quit();
    }

    @Test
    public void testEhloResolv() throws Exception {
        smtpConfiguration.setEhloResolv();
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1");
        init(smtpConfiguration);

        doTestHeloEhloResolv("ehlo");
    }

    @Test
    public void testEhloResolvDefault() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol1.sendCommand("ehlo", "abgsfe3rsf.de");
        // ehlo should not be checked. so this should give a 250 code
        assertEquals("ehlo accepted", 250, smtpProtocol1.getReplyCode());

        smtpProtocol1.quit();
    }

    @Test
    public void testEhloResolvIgnoreClientDisabled() throws Exception {
        smtpConfiguration.setEhloResolv();
        smtpConfiguration.setCheckAuthNetworks(true);
        init(smtpConfiguration);

        doTestHeloEhloResolv("ehlo");
    }

    @Test
    public void testReverseEqualsEhlo() throws Exception {
        smtpConfiguration.setReverseEqualsEhlo();
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1");
        // temporary alter the loopback resolution
        InetAddress jamesDomain = null;
        try {
            jamesDomain = dnsServer.getByName("james.apache.org");
        } catch (UnknownHostException e) {
            fail("james.apache.org currently cannot be resolved (check your DNS/internet connection/proxy settings to make test pass)");
        }
        dnsServer.setLocalhostByName(jamesDomain);
        try {
            init(smtpConfiguration);

            SMTPClient smtpProtocol1 = new SMTPClient();
            smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

            assertTrue("first connection taken", smtpProtocol1.isConnected());

            // no message there, yet
            assertNull("no mail received by mail server", queue.getLastMail());

            String ehlo1 = "abgsfe3rsf.de";
            String ehlo2 = "james.apache.org";
            String mail = "sender";
            String rcpt = "recipient";

            smtpProtocol1.sendCommand("ehlo", ehlo1);
            smtpProtocol1.setSender(mail);
            smtpProtocol1.addRecipient(rcpt);

            // this should give a 501 code cause the ehlo not equals reverse of
            // ip
            assertEquals("expected error: ehlo not equals reverse of ip", 501, smtpProtocol1.getReplyCode());

            smtpProtocol1.sendCommand("ehlo", ehlo2);
            smtpProtocol1.setSender(mail);
            smtpProtocol1.addRecipient(rcpt);

            // ehlo is resolvable. so this should give a 250 code
            assertEquals("ehlo accepted", 250, smtpProtocol1.getReplyCode());

            smtpProtocol1.quit();
        } finally {
            dnsServer.setLocalhostByName(null);
        }
    }

    @Test
    public void testHeloEnforcement() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        String sender1 = "mail_sender1@localhost";
        smtpProtocol1.setSender(sender1);
        assertEquals("expected 503 error", 503, smtpProtocol1.getReplyCode());

        smtpProtocol1.helo(InetAddress.getLocalHost().toString());

        smtpProtocol1.setSender(sender1);

        smtpProtocol1.quit();
    }

    @Test
    public void testHeloEnforcementDisabled() throws Exception {
        smtpConfiguration.setHeloEhloEnforcement(false);
        init(smtpConfiguration);

        SMTPClient smtpProtocol1 = new SMTPClient();
        smtpProtocol1.connect("127.0.0.1", smtpListenerPort);

        assertTrue("first connection taken", smtpProtocol1.isConnected());

        // no message there, yet
        assertNull("no mail received by mail server", queue.getLastMail());

        String sender1 = "mail_sender1@localhost";

        smtpProtocol1.setSender(sender1);

        smtpProtocol1.quit();
    }

    @Test
    public void testAuthCancel() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("127.0.0.1/8");
        smtpConfiguration.setAuthorizingAnnounce();
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());

        smtpProtocol.sendCommand("AUTH PLAIN");

        assertEquals("start auth.", 334, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("*");

        assertEquals("cancel auth.", 501, smtpProtocol.getReplyCode());

        smtpProtocol.quit();

    }

    // Test for JAMES-939
    @Test
    public void testAuth() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("128.0.0.1/8");
        smtpConfiguration.setAuthorizingAnnounce();
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());
        String[] capabilityRes = smtpProtocol.getReplyStrings();

        List<String> capabilitieslist = new ArrayList<String>();
        for (int i = 1; i < capabilityRes.length; i++) {
            capabilitieslist.add(capabilityRes[i].substring(4));
        }

        assertTrue("anouncing auth required", capabilitieslist.contains("AUTH LOGIN PLAIN"));
        // is this required or just for compatibility?
        // assertTrue("anouncing auth required",
        // capabilitieslist.contains("AUTH=LOGIN PLAIN"));

        String userName = "test_user_smtp";
        String noexistUserName = "noexist_test_user_smtp";
        String sender = "test_user_smtp@localhost";
        smtpProtocol.sendCommand("AUTH FOO", null);
        assertEquals("expected error: unrecognized authentication type", 504, smtpProtocol.getReplyCode());

        smtpProtocol.setSender(sender);

        smtpProtocol.addRecipient("mail@sample.com");
        assertEquals("expected 530 error", 530, smtpProtocol.getReplyCode());

        assertFalse("user not existing", usersRepository.contains(noexistUserName));

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.encodeAsString("\0" + noexistUserName + "\0pwd\0"));
        // smtpProtocol.sendCommand(noexistUserName+"pwd".toCharArray());
        assertEquals("expected error", 535, smtpProtocol.getReplyCode());

        usersRepository.addUser(userName, "pwd");

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.encodeAsString("\0" + userName + "\0wrongpwd\0"));
        assertEquals("expected error", 535, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.encodeAsString("\0" + userName + "\0pwd\0"));
        assertEquals("authenticated", 235, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("AUTH PLAIN");
        assertEquals("expected error: User has previously authenticated.", 503, smtpProtocol.getReplyCode());

        smtpProtocol.addRecipient("mail@sample.com");
        smtpProtocol.sendShortMessageData("Subject: test\r\n\r\nTest body testAuth\r\n");

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testAuthWithEmptySender() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("128.0.0.1/8");
        smtpConfiguration.setAuthorizingAnnounce();
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        String userName = "test_user_smtp";
        usersRepository.addUser(userName, "pwd");

        smtpProtocol.setSender("");

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.encodeAsString("\0" + userName + "\0pwd\0"));
        assertEquals("authenticated", 235, smtpProtocol.getReplyCode());

        smtpProtocol.addRecipient("mail@sample.com");
        assertEquals("expected error", 503, smtpProtocol.getReplyCode());

        smtpProtocol.quit();
    }

    @Test
    public void testNoRecepientSpecified() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.setSender("mail@sample.com");

        // left out for test smtpProtocol.rcpt(new Address("mail@localhost"));

        smtpProtocol.sendShortMessageData("Subject: test\r\n\r\nTest body testNoRecepientSpecified\r\n");
        assertTrue("sending succeeded without recepient", SMTPReply.isNegativePermanent(smtpProtocol.getReplyCode()));

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNull("no mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testMultipleMailsAndRset() throws Exception {
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.setSender("mail@sample.com");

        smtpProtocol.reset();

        smtpProtocol.setSender("mail@sample.com");

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNull("no mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testRelayingDenied() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("128.0.0.1/8");
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.setSender("mail@sample.com");

        smtpProtocol.addRecipient("maila@sample.com");
        assertEquals("expected 550 error", 550, smtpProtocol.getReplyCode());
    }

    @Test
    public void testHandleAnnouncedMessageSizeLimitExceeded() throws Exception {
        smtpConfiguration.setMaxMessageSize(1); // set message limit to 1kb
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.sendCommand("MAIL FROM:<mail@localhost> SIZE=1025", null);
        assertEquals("expected error: max msg size exceeded", 552, smtpProtocol.getReplyCode());

        smtpProtocol.addRecipient("mail@localhost");
        assertEquals("expected error", 503, smtpProtocol.getReplyCode());
    }

    public void testHandleMessageSizeLimitExceeded() throws Exception {
        smtpConfiguration.setMaxMessageSize(1); // set message limit to 1kb
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.setSender("mail@localhost");
        smtpProtocol.addRecipient("mail@localhost");

        Writer wr = smtpProtocol.sendMessageData();
        // create Body with more than 1kb . 502
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write(
                "1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100\r\n");
        // second line
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("123456781012345678201\r\n"); // 521 + CRLF = 523 + 502 => 1025
        wr.close();

        assertFalse(smtpProtocol.completePendingCommand());

        assertEquals("expected 552 error", 552, smtpProtocol.getReplyCode());

    }

    @Test
    public void testHandleMessageSizeLimitRespected() throws Exception {
        smtpConfiguration.setMaxMessageSize(1); // set message limit to 1kb
        init(smtpConfiguration);

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo " + InetAddress.getLocalHost());

        smtpProtocol.setSender("mail@localhost");
        smtpProtocol.addRecipient("mail@localhost");

        Writer wr = smtpProtocol.sendMessageData();
        // create Body with less than 1kb
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        wr.write("1234567810123456782012\r\n"); // 1022 + CRLF = 1024
        wr.close();

        assertTrue(smtpProtocol.completePendingCommand());

        assertEquals("expected 250 ok", 250, smtpProtocol.getReplyCode());

    }
    // Check if auth users get not rejected cause rbl. See JAMES-566

    @Test
    public void testDNSRBLNotRejectAuthUser() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1/32");
        smtpConfiguration.setAuthorizingAnnounce();
        smtpConfiguration.useRBL(true);
        init(smtpConfiguration);

        dnsServer.setLocalhostByName(InetAddress.getByName("127.0.0.1"));

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());
        String[] capabilityRes = smtpProtocol.getReplyStrings();

        List<String> capabilitieslist = new ArrayList<String>();
        for (int i = 1; i < capabilityRes.length; i++) {
            capabilitieslist.add(capabilityRes[i].substring(4));
        }

        assertTrue("anouncing auth required", capabilitieslist.contains("AUTH LOGIN PLAIN"));
        // is this required or just for compatibility? assertTrue("anouncing
        // auth required", capabilitieslist.contains("AUTH=LOGIN PLAIN"));

        String userName = "test_user_smtp";
        String sender = "test_user_smtp@localhost";

        smtpProtocol.setSender(sender);

        usersRepository.addUser(userName, "pwd");

        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.encodeAsString("\0" + userName + "\0pwd\0"));
        assertEquals("authenticated", 235, smtpProtocol.getReplyCode());

        smtpProtocol.addRecipient("mail@sample.com");
        assertEquals("authenticated.. not reject", 250, smtpProtocol.getReplyCode());

        smtpProtocol.sendShortMessageData("Subject: test\r\n\r\nTest body testDNSRBLNotRejectAuthUser\r\n");

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", queue.getLastMail());
    }

    @Test
    public void testDNSRBLRejectWorks() throws Exception {
        smtpConfiguration.setAuthorizedAddresses("192.168.0.1/32");
        smtpConfiguration.useRBL(true);
        init(smtpConfiguration);

        dnsServer.setLocalhostByName(InetAddress.getByName("127.0.0.1"));

        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());

        String sender = "test_user_smtp@localhost";

        smtpProtocol.setSender(sender);

        smtpProtocol.addRecipient("mail@sample.com");
        assertEquals("reject", 550, smtpProtocol.getReplyCode());

        smtpProtocol.sendShortMessageData("Subject: test\r\n\r\nTest body testDNSRBLRejectWorks\r\n");

        smtpProtocol.quit();

        // mail was rejected by SMTPServer
        assertNull("mail reject by mail server", queue.getLastMail());
    }

    @Test
    public void testAddressBracketsEnforcementDisabled() throws Exception {
        smtpConfiguration.setAddressBracketsEnforcement(false);
        init(smtpConfiguration);
        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());

        smtpProtocol.sendCommand("mail from:", "test@localhost");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("rcpt to:", "mail@sample.com");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.quit();

        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());

        smtpProtocol.sendCommand("mail from:", "<test@localhost>");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("rcpt to:", "<mail@sample.com>");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.quit();
    }

    @Test
    public void testAddressBracketsEnforcementEnabled() throws Exception {
        init(smtpConfiguration);
        SMTPClient smtpProtocol = new SMTPClient();
        smtpProtocol.connect("127.0.0.1", smtpListenerPort);

        smtpProtocol.sendCommand("ehlo", InetAddress.getLocalHost().toString());

        smtpProtocol.sendCommand("mail from:", "test@localhost");
        assertEquals("reject", 501, smtpProtocol.getReplyCode());
        smtpProtocol.sendCommand("mail from:", "<test@localhost>");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.sendCommand("rcpt to:", "mail@sample.com");
        assertEquals("reject", 501, smtpProtocol.getReplyCode());
        smtpProtocol.sendCommand("rcpt to:", "<mail@sample.com>");
        assertEquals("accept", 250, smtpProtocol.getReplyCode());

        smtpProtocol.quit();
    }

    // See http://www.ietf.org/rfc/rfc2920.txt 4: Examples
    @Test
    public void testPipelining() throws Exception {
        StringBuilder buf = new StringBuilder();
        init(smtpConfiguration);
        Socket client = new Socket("127.0.0.1", smtpListenerPort);

        buf.append("HELO TEST");
        buf.append("\r\n");
        buf.append("MAIL FROM: <test@localhost>");
        buf.append("\r\n");
        buf.append("RCPT TO: <test2@localhost>");
        buf.append("\r\n");
        buf.append("DATA");
        buf.append("\r\n");
        buf.append("Subject: test");
        buf.append("\r\n");

        buf.append("\r\n");
        buf.append("content");
        buf.append("\r\n");
        buf.append(".");
        buf.append("\r\n");
        buf.append("quit");
        buf.append("\r\n");

        OutputStream out = client.getOutputStream();

        out.write(buf.toString().getBytes());
        out.flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

        assertEquals("Connection made", 220, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("HELO accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("MAIL FROM accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("RCPT TO accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("DATA accepted", 354, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("Message accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        in.close();
        out.close();
        client.close();
    }

    // See http://www.ietf.org/rfc/rfc2920.txt 4: Examples
    @Test
    public void testRejectAllRCPTPipelining() throws Exception {
        StringBuilder buf = new StringBuilder();
        smtpConfiguration.setAuthorizedAddresses("");
        init(smtpConfiguration);
        Socket client = new Socket("127.0.0.1", smtpListenerPort);

        buf.append("HELO TEST");
        buf.append("\r\n");
        buf.append("MAIL FROM: <test@localhost>");
        buf.append("\r\n");
        buf.append("RCPT TO: <test@invalid>");
        buf.append("\r\n");
        buf.append("RCPT TO: <test2@invalid>");
        buf.append("\r\n");
        buf.append("DATA");
        buf.append("\r\n");
        buf.append("Subject: test");
        buf.append("\r\n");

        buf.append("\r\n");
        buf.append("content");
        buf.append("\r\n");
        buf.append(".");
        buf.append("\r\n");
        buf.append("quit");
        buf.append("\r\n");

        OutputStream out = client.getOutputStream();

        out.write(buf.toString().getBytes());
        out.flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

        assertEquals("Connection made", 220, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("HELO accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("MAIL FROM accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("RCPT TO rejected", 550, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("RCPT TO rejected", 550, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("DATA not accepted", 503, Integer.parseInt(in.readLine().split(" ")[0]));
        in.close();
        out.close();
        client.close();
    }

    @Test
    public void testRejectOneRCPTPipelining() throws Exception {
        StringBuilder buf = new StringBuilder();
        smtpConfiguration.setAuthorizedAddresses("");
        init(smtpConfiguration);
        Socket client = new Socket("127.0.0.1", smtpListenerPort);

        buf.append("HELO TEST");
        buf.append("\r\n");
        buf.append("MAIL FROM: <test@localhost>");
        buf.append("\r\n");
        buf.append("RCPT TO: <test@invalid>");
        buf.append("\r\n");
        buf.append("RCPT TO: <test2@localhost>");
        buf.append("\r\n");
        buf.append("DATA");
        buf.append("\r\n");
        buf.append("Subject: test");
        buf.append("\r\n");

        buf.append("\r\n");
        buf.append("content");
        buf.append("\r\n");
        buf.append(".");
        buf.append("\r\n");
        buf.append("quit");
        buf.append("\r\n");

        OutputStream out = client.getOutputStream();

        out.write(buf.toString().getBytes());
        out.flush();

        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

        assertEquals("Connection made", 220, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("HELO accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("MAIL FROM accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("RCPT TO rejected", 550, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("RCPT accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("DATA accepted", 354, Integer.parseInt(in.readLine().split(" ")[0]));
        assertEquals("Message accepted", 250, Integer.parseInt(in.readLine().split(" ")[0]));
        in.close();
        out.close();
        client.close();
    }
}
