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
package org.apache.james.pop3server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.pop3server.netty.POP3Server;
import org.apache.james.protocols.api.utils.ProtocolServerUtils;
import org.apache.james.protocols.lib.POP3BeforeSMTPHelper;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.jboss.netty.util.HashedWheelTimer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class POP3ServerTest {

    private POP3TestConfiguration pop3Configuration;
    private final MemoryUsersRepository usersRepository = MemoryUsersRepository.withoutVirtualHosting();
    private POP3Client pop3Client = null;
    protected MockFileSystem fileSystem;
    protected MockProtocolHandlerLoader protocolHandlerChain;
    private StoreMailboxManager mailboxManager;
    private final byte[] content = ("Return-path: return@test.com\r\n"
            + "Content-Transfer-Encoding: plain\r\n"
            + "Subject: test\r\n\r\n"
            + "Body Text POP3ServerTest.setupTestMails\r\n").getBytes();
    private POP3Server pop3Server;
    private HashedWheelTimer hashedWheelTimer;

    @Before
    public void setUp() throws Exception {
        hashedWheelTimer = new HashedWheelTimer();
        setUpServiceManager();
        setUpPOP3Server();
        pop3Configuration = new POP3TestConfiguration();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (pop3Client != null) {
                if (pop3Client.isConnected()) {
                    pop3Client.sendCommand("quit");
                    pop3Client.disconnect();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        protocolHandlerChain.dispose();
        pop3Server.destroy();
        hashedWheelTimer.stop();
    }

    @Test
    public void testAuthenticationFail() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("known", "test2");

        pop3Client.login("known", "test");
        assertEquals(0, pop3Client.getState());
        assertTrue(pop3Client.getReplyString().startsWith("-ERR"));
    }

    @Test
    public void testUnknownUser() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("unknown", "test");
        assertEquals(0, pop3Client.getState());
        assertTrue(pop3Client.getReplyString().startsWith("-ERR"));
    }

    @Test
    public void testKnownUserEmptyInbox() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("foo", "bar");

        // not authenticated
        POP3MessageInfo[] entries = pop3Client.listMessages();
        assertNull(entries);

        pop3Client.login("foo", "bar");
        System.err.println(pop3Client.getState());
        assertEquals(1, pop3Client.getState());

        entries = pop3Client.listMessages();
        assertEquals(1, pop3Client.getState());

        assertNotNull(entries);
        assertEquals(entries.length, 0);

        POP3MessageInfo p3i = pop3Client.listMessage(1);
        assertEquals(1, pop3Client.getState());
        assertNull(p3i);
    }

    // TODO: This currently fails with Async implementation because
    // it use Charset US-ASCII to decode / Encode the protocol
    // from the RFC I'm currently not understand if NON-ASCII chars
    // are allowed at all. So this needs to be checked
    /*
     * public void testNotAsciiCharsInPassword() throws Exception {
     * finishSetUp(m_testConfiguration);
     * 
     * m_pop3Protocol = new POP3Client();
     * m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
     * 
     * String pass = "bar" + (new String(new char[] { 200, 210 })) + "foo";
     * m_usersRepository.addUser("foo", pass); InMemorySpoolRepository
     * mockMailRepository = new InMemorySpoolRepository();
     * m_mailServer.setUserInbox("foo", mockMailRepository);
     * 
     * m_pop3Protocol.login("foo", pass); assertEquals(1,
     * m_pop3Protocol.getState()); ContainerUtil.dispose(mockMailRepository); }
     */

    @Test
    public void testUnknownCommand() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("unkn");
        assertEquals(0, pop3Client.getState());
        assertEquals("Expected -ERR as result for an unknown command", pop3Client.getReplyString().substring(0, 4),
                "-ERR");
    }

    @Test
    public void testUidlCommand() throws Exception {
        finishSetUp(pop3Configuration);

        usersRepository.addUser("foo", "bar");

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("uidl");
        assertEquals(0, pop3Client.getState());

        pop3Client.login("foo", "bar");

        POP3MessageInfo[] list = pop3Client.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        pop3Client.disconnect();
        MailboxPath mailboxPath = MailboxPath.forUser("foo", "INBOX");
        MailboxSession session = mailboxManager.login("foo", "bar");
        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }
        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Client.login("foo", "bar");

        list = pop3Client.listUniqueIdentifiers();
        assertEquals("Expected 2 messages, found: " + list.length, 2, list.length);

        POP3MessageInfo p3i = pop3Client.listUniqueIdentifier(1);
        assertNotNull(p3i);

        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    @Test
    public void testMiscCommandsWithWithoutAuth() throws Exception {
        finishSetUp(pop3Configuration);

        usersRepository.addUser("foo", "bar");

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.sendCommand("noop");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("stat");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("pass");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("auth");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("rset");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.login("foo", "bar");

        POP3MessageInfo[] list = pop3Client.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        pop3Client.sendCommand("noop");
        assertEquals(1, pop3Client.getState());

        pop3Client.sendCommand("pass");
        assertEquals(1, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("auth");
        assertEquals(1, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("user");
        assertEquals(1, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.sendCommand("rset");
        assertEquals(1, pop3Client.getState());
        
    }

    @Test
    public void testKnownUserInboxWithMessages() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("foo2", "bar2");

        MailboxPath mailboxPath = MailboxPath.forUser("foo2", "INBOX");
        MailboxSession session = mailboxManager.login("foo2", "bar2");

        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        pop3Client.sendCommand("retr", "1");
        assertEquals(0, pop3Client.getState());
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        pop3Client.login("foo2", "bar2");
        assertEquals(1, pop3Client.getState());

        POP3MessageInfo[] entries = pop3Client.listMessages();

        assertNotNull(entries);
        assertEquals(2, entries.length);
        assertEquals(1, pop3Client.getState());

        Reader r = pop3Client.retrieveMessageTop(entries[0].number, 0);

        assertNotNull(r);

        r.close();

        Reader r2 = pop3Client.retrieveMessage(entries[0].number);
        assertNotNull(r2);
        r2.close();

        // existing message
        boolean deleted = pop3Client.deleteMessage(entries[0].number);
        assertTrue(deleted);

        // already deleted message
        deleted = pop3Client.deleteMessage(entries[0].number);

        // TODO: Understand why this fails...
        assertFalse(deleted);

        // unexisting message
        deleted = pop3Client.deleteMessage(10);
        assertFalse(deleted);

        pop3Client.logout();
        //m_pop3Protocol.disconnect();

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("foo2", "bar2");
        assertEquals(1, pop3Client.getState());

        entries = null;

        POP3MessageInfo stats = pop3Client.status();
        assertEquals(1, stats.number);
        assertEquals(5, stats.size);

        entries = pop3Client.listMessages();

        assertNotNull(entries);
        assertEquals(1, entries.length);
        assertEquals(1, pop3Client.getState());

        // top without arguments
        pop3Client.sendCommand("top");
        assertEquals("-ERR", pop3Client.getReplyString().substring(0, 4));

        Reader r3 = pop3Client.retrieveMessageTop(entries[0].number, 0);
        assertNotNull(r3);
        r3.close();
        mailboxManager.deleteMailbox(mailboxPath, session);
    }

    /**
     * Test for JAMES-1202 -  Which shows that UIDL,STAT and LIST all show the same message numbers.
     */
    @Test
    public void testStatUidlList() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("foo2", "bar2");

        MailboxPath mailboxPath = MailboxPath.forUser("foo2", "INBOX");
        MailboxSession session = mailboxManager.login("foo2", "bar2");

        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        int msgCount = 100;
        for (int i = 0; i < msgCount; i++) {
            mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.builder()
                .build("Subject: test\r\n\r\n" + i), session);
        }

        pop3Client.login("foo2", "bar2");
        assertEquals(1, pop3Client.getState());

        POP3MessageInfo[] listEntries = pop3Client.listMessages();
        POP3MessageInfo[] uidlEntries = pop3Client.listUniqueIdentifiers();
        POP3MessageInfo statInfo = pop3Client.status();
        assertEquals(msgCount, listEntries.length);
        assertEquals(msgCount, uidlEntries.length);
        assertEquals(msgCount, statInfo.number);

        pop3Client.sendCommand("quit");
        pop3Client.disconnect();

        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        pop3Client.login("foo2", "bar2");
        assertEquals(1, pop3Client.getState());

        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    /**
     * Test for JAMES-1202 - This was failing before as the more then one connection to the same
     * mailbox was not handled the right way
     */
    @Test
    @Ignore
    public void testStatUidlListTwoConnections() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("foo2", "bar2");

        MailboxPath mailboxPath = MailboxPath.forUser("foo2", "INBOX");
        MailboxSession session = mailboxManager.login("foo2", "bar2");

        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        int msgCount = 100;
        for (int i = 0; i < msgCount; i++) {
            mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.builder()
                .build(("Subject: test\r\n\r\n" + i)), session);
        }

        pop3Client.login("foo2", "bar2");
        assertEquals(1, pop3Client.getState());

        POP3MessageInfo[] listEntries = pop3Client.listMessages();
        POP3MessageInfo[] uidlEntries = pop3Client.listUniqueIdentifiers();
        POP3MessageInfo statInfo = pop3Client.status();
        assertEquals(msgCount, listEntries.length);
        assertEquals(msgCount, uidlEntries.length);
        assertEquals(msgCount, statInfo.number);

        POP3Client pop3Protocol2 = new POP3Client();
        pop3Protocol2.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());
        pop3Protocol2.login("foo2", "bar2");
        assertEquals(1, pop3Protocol2.getState());

        POP3MessageInfo[] listEntries2 = pop3Protocol2.listMessages();
        POP3MessageInfo[] uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        POP3MessageInfo statInfo2 = pop3Protocol2.status();
        assertEquals(msgCount, listEntries2.length);
        assertEquals(msgCount, uidlEntries2.length);
        assertEquals(msgCount, statInfo2.number);

        pop3Client.deleteMessage(1);
        listEntries = pop3Client.listMessages();
        uidlEntries = pop3Client.listUniqueIdentifiers();
        statInfo = pop3Client.status();
        assertEquals(msgCount - 1, listEntries.length);
        assertEquals(msgCount - 1, uidlEntries.length);
        assertEquals(msgCount - 1, statInfo.number);

        // even after the message was deleted it should get displayed in the
        // second connection
        listEntries2 = pop3Protocol2.listMessages();
        uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        statInfo2 = pop3Protocol2.status();
        assertEquals(msgCount, listEntries2.length);
        assertEquals(msgCount, uidlEntries2.length);
        assertEquals(msgCount, statInfo2.number);

        assertTrue(pop3Client.logout());
        pop3Client.disconnect();

        // even after the message was deleted and the session was quit it should
        // get displayed in the second connection
        listEntries2 = pop3Protocol2.listMessages();
        uidlEntries2 = pop3Protocol2.listUniqueIdentifiers();
        statInfo2 = pop3Protocol2.status();
        assertEquals(msgCount, listEntries2.length);
        assertEquals(msgCount, uidlEntries2.length);
        assertEquals(msgCount, statInfo2.number);

        // This both should error and so return null
        assertNull(pop3Protocol2.retrieveMessageTop(1, 100));
        assertNull(pop3Protocol2.retrieveMessage(1));

        pop3Protocol2.sendCommand("quit");
        pop3Protocol2.disconnect();

        mailboxManager.deleteMailbox(mailboxPath, session);
        
    }

    /*
     * public void testTwoSimultaneousMails() throws Exception {
     * finishSetUp(m_testConfiguration);
     * 
     * // make two user/repositories, open both
     * m_usersRepository.addUser("foo1", "bar1"); InMemorySpoolRepository
     * mailRep1 = new InMemorySpoolRepository(); setupTestMails(mailRep1);
     * m_mailServer.setUserInbox("foo1", mailRep1);
     * 
     * m_usersRepository.addUser("foo2", "bar2"); InMemorySpoolRepository
     * mailRep2 = new InMemorySpoolRepository(); //do not setupTestMails, this
     * is done later m_mailServer.setUserInbox("foo2", mailRep2);
     * 
     * POP3Client pop3Protocol2 = null; try { // open two connections
     * m_pop3Protocol = new POP3Client(); m_pop3Protocol.connect("127.0.0.1",
     * m_pop3ListenerPort); pop3Protocol2 = new POP3Client();
     * pop3Protocol2.connect("127.0.0.1", m_pop3ListenerPort);
     * 
     * assertEquals("first connection taken", 0, m_pop3Protocol.getState());
     * assertEquals("second connection taken", 0, pop3Protocol2.getState());
     * 
     * // open two accounts m_pop3Protocol.login("foo1", "bar1");
     * 
     * pop3Protocol2.login("foo2", "bar2");
     * 
     * POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
     * assertEquals("foo1 has mails", 2, entries.length);
     * 
     * entries = pop3Protocol2.listMessages(); assertEquals("foo2 has no mails",
     * 0, entries.length);
     * 
     * } finally { // put both to rest, field var is handled by tearDown() if
     * (pop3Protocol2 != null) { pop3Protocol2.sendCommand("quit");
     * pop3Protocol2.disconnect(); } } }
     */
    
    @Test
    public void testIpStored() throws Exception {

        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        String pass = "password";
        usersRepository.addUser("foo", pass);

        pop3Client.login("foo", pass);
        assertEquals(1, pop3Client.getState());
        assertTrue(POP3BeforeSMTPHelper.isAuthorized("127.0.0.1"));

    }

    @Test
    public void testCapa() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        String pass = "password";
        usersRepository.addUser("foo", pass);

        assertEquals(POP3Reply.OK, pop3Client.sendCommand("CAPA"));

        pop3Client.getAdditionalReply();
        pop3Client.getReplyString();
        List<String> replies = Arrays.asList(pop3Client.getReplyStrings());

        assertTrue("contains USER", replies.contains("USER"));

        pop3Client.login("foo", pass);
        assertEquals(POP3Reply.OK, pop3Client.sendCommand("CAPA"));

        pop3Client.getAdditionalReply();
        pop3Client.getReplyString();
        replies = Arrays.asList(pop3Client.getReplyStrings());
        assertTrue("contains USER", replies.contains("USER"));
        assertTrue("contains UIDL", replies.contains("UIDL"));
        assertTrue("contains TOP", replies.contains("TOP"));
        
    }

    /*
     * See JAMES-649 The same happens when using RETR
     * 
     * Comment to not broke the builds!
     * 
     * public void testOOMTop() throws Exception {
     * finishSetUp(m_testConfiguration);
     * 
     * int messageCount = 30000; m_pop3Protocol = new POP3Client();
     * m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
     * 
     * m_usersRepository.addUser("foo", "bar"); InMemorySpoolRepository
     * mockMailRepository = new InMemorySpoolRepository();
     * 
     * Mail m = new MailImpl(); m.setMessage(Util.createMimeMessage("X-TEST",
     * "test")); for (int i = 1; i < messageCount+1; i++ ) { m.setName("test" +
     * i); mockMailRepository.store(m); }
     * 
     * m_mailServer.setUserInbox("foo", mockMailRepository);
     * 
     * // not authenticated POP3MessageInfo[] entries =
     * m_pop3Protocol.listMessages(); assertNull(entries);
     * 
     * m_pop3Protocol.login("foo", "bar");
     * System.err.println(m_pop3Protocol.getState()); assertEquals(1,
     * m_pop3Protocol.getState());
     * 
     * entries = m_pop3Protocol.listMessages(); assertEquals(1,
     * m_pop3Protocol.getState());
     * 
     * assertNotNull(entries); assertEquals(entries.length, messageCount);
     * 
     * for (int i = 1; i < messageCount+1; i++ ) { Reader r =
     * m_pop3Protocol.retrieveMessageTop(i, 100); assertNotNull(r); r.close(); }
     * 
     * ContainerUtil.dispose(mockMailRepository); }
     */
    // See JAMES-1136
    @Test
    public void testDeadlockOnRetr() throws Exception {
        finishSetUp(pop3Configuration);

        pop3Client = new POP3Client();
        InetSocketAddress bindedAddress = new ProtocolServerUtils(pop3Server).retrieveBindedAddress();
        pop3Client.connect(bindedAddress.getAddress().getHostAddress(), bindedAddress.getPort());

        usersRepository.addUser("foo6", "bar6");
        MailboxSession session = mailboxManager.login("foo6", "bar6");

        MailboxPath mailboxPath = MailboxPath.inbox(session);

        mailboxManager.startProcessingRequest(session);
        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(content);

        byte[] bigMail = new byte[1024 * 1024 * 10];
        int c = 0;
        for (int i = 0; i < bigMail.length; i++) {

            bigMail[i] = 'X';
            c++;
            if (c == 1000 || i + 3 == bigMail.length) {
                c = 0;
                bigMail[++i] = '\r';
                bigMail[++i] = '\n';
            }
        }
        out.write(bigMail);
        bigMail = null;

        mailboxManager.getMailbox(mailboxPath, session).appendMessage(MessageManager.AppendCommand.builder()
                .build(out.toByteArray()), session);
        mailboxManager.startProcessingRequest(session);

        pop3Client.login("foo6", "bar6");
        assertEquals(1, pop3Client.getState());

        POP3MessageInfo[] entries = pop3Client.listMessages();

        assertNotNull(entries);
        assertEquals(1, entries.length);
        assertEquals(1, pop3Client.getState());

        Reader r = pop3Client.retrieveMessage(entries[0].number);

        assertNotNull(r);
        r.close();
        mailboxManager.deleteMailbox(mailboxPath, session);

    }

    protected POP3Server createPOP3Server() {
        return new POP3Server();
    }

    protected void initPOP3Server(POP3TestConfiguration testConfiguration) throws Exception {
        pop3Server.configure(testConfiguration);
        pop3Server.init();
    }

    protected void setUpPOP3Server() {
        pop3Server = createPOP3Server();
        pop3Server.setFileSystem(fileSystem);
        pop3Server.setHashWheelTimer(hashedWheelTimer);
        pop3Server.setProtocolHandlerLoader(protocolHandlerChain);
    }

    protected void finishSetUp(POP3TestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        initPOP3Server(testConfiguration);
    }

    protected void setUpServiceManager() throws Exception {
        protocolHandlerChain = new MockProtocolHandlerLoader();
        protocolHandlerChain.put("usersrepository", UsersRepository.class, usersRepository);

        mailboxManager = new InMemoryIntegrationResources()
            .createMailboxManager(new SimpleGroupMembershipResolver(),
                (userid, passwd) -> {
                    try {
                        return usersRepository.test(userid, passwd.toString());
                    } catch (UsersRepositoryException e) {
                        e.printStackTrace();
                        return false;
                    }
                }, FakeAuthorizator.defaultReject());

        protocolHandlerChain.put("mailboxmanager", MailboxManager.class, mailboxManager);
    
        fileSystem = new MockFileSystem();
        protocolHandlerChain.put("fileSystem", FileSystem.class, fileSystem);
    
    }

    private void setupTestMails(MailboxSession session, MessageManager mailbox) throws MailboxException {
        mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .build(content), session);
        byte[] content2 = ("EMPTY").getBytes();
        mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .build(content2), session);
    }

}
