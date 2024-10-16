/**
 * Copyright (C) 2016, The logback-contrib developers. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.classic.net;

import ch.qos.logback.core.LayoutBase;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Test for XmppAppender
 * @author szalik
 */
@RunWith(MockitoJUnitRunner.class)
public class XmppAppenderTest {
    private static final String SEND_TO = "user_to@host.com";
    private XmppAppender<Object> appender;
    @Mock
    private XMPPConnection connection;
    @Mock
    private ChatManager chatManager;
    @Mock
    private Roster roster;
    @Mock
    private Chat chat;

    @Before
    public void setUp() throws Exception {
        when(connection.getChatManager()).thenReturn(chatManager);
        when(connection.getRoster()).thenReturn(roster);
        when(chatManager.createChat(eq(SEND_TO), any(MessageListener.class))).thenReturn(chat);

        appender = new XmppAppender() {
            @Override
            protected XMPPConnection createXmppConnection() {
                return connection;
            }
        };
        appender.setPassword("password");
        appender.setUsername("user");
        appender.setXmmpAccount("account@xmppHost.com");
        appender.setSendToJid(SEND_TO);
        appender.setLayout(new LayoutBase<Object>() {
            public String doLayout(Object o) {
                return o == null ? null : o.toString();
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        if (appender.isStarted()) {
            appender.stop();
        }
    }

    @Test
    public void testStart() throws Exception {
        Assert.assertFalse(appender.isStarted());
        appender.start();
        verify(roster).contains(SEND_TO);
        verify(connection).connect();
        Assert.assertTrue(appender.isStarted());
    }

    @Test
    public void testAppendOn() throws Exception {
        appender.start();
        appender.doAppend("Test message");
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(chat).sendMessage(messageArgumentCaptor.capture());
        Message message = messageArgumentCaptor.getValue();
        Assert.assertEquals("Test message", message.getBody());
    }

    @Test
    public void testAppendOff() throws Exception {
        appender.doAppend("Test message");
        verify(chat, never()).sendMessage(any(Message.class));
    }

    @Test
    public void testStop() throws Exception {
        appender.start();
        appender.stop();
        Assert.assertFalse(appender.isStarted());
        appender.doAppend("Test message");
        verify(chat, never()).sendMessage(any(Message.class));
    }
}