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

import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.layout.EchoLayout;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;


/**
 * XMMP (Jabber / GoogleHangout) async appender
 * <p>It is recommended to wrap it with AsyncAppender</p>
 * @see ch.qos.logback.classic.AsyncAppender
 * @author szalik
 */
public class XmppAppender<E> extends AppenderBase<E> {
    private ConnectionConfiguration connectionConfiguration;
    private XMPPConnection conn;
    private String password;
    private String username;
    private String resourceName = getClass().getSimpleName();
    private String sendToJid;
    private Chat chat;
    private int xmmpPort = 5222;
    private String xmmpServer;
    private Layout<E> layout = new EchoLayout<E>();


    public void setSendToJid(String sendToJid) {
        this.sendToJid = sendToJid;
    }

    public void setLayout(Layout<E> layout) {
        this.layout = layout;
    }

    /**
     * @param xmmpAccount jid@server.org[:port]
     */
    public void setXmmpAccount(String xmmpAccount) {
        String[] parts = xmmpAccount.split(":", 2);
        if (parts.length == 2) {
            xmmpPort = Integer.parseInt(parts[1].trim());
        }
        parts = parts[0].split("@", 2);
        username = parts[0];
        xmmpServer = parts[1];
    }


    public void setUsername(String username) {
        this.username = username;
    }


    public void setPassword(String password) {
        this.password = password;
    }


    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }


    @Override
    public void stop() {
        boolean doStop = isStarted();
        super.stop();
        if (doStop && conn != null && conn.isConnected()) {
            conn.disconnect();
            chat = null;
        }
    }

    @Override
    public void start() {
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);
        connectionConfiguration = new ConnectionConfiguration(xmmpServer, xmmpPort);
        xmmpConnect();
        super.start();
    }


    @Override
    protected void append(E event) {
        if (chat == null) {
            addInfo(formatLogMessage("Connecting to xmmp server - " + xmmpServer + ":" + xmmpPort));
            xmmpConnect();
        }
        if (chat != null) {
            Message message = new Message(sendToJid);
            message.setBody(layout.doLayout(event));
            try {
                chat.sendMessage(message);
            } catch (XMPPException e) {
                addError(formatLogMessage("Error sending message to " + sendToJid + "."), e);
            }
        }
    }

    XMPPConnection createXmppConnection() {
        return new XMPPConnection(connectionConfiguration);
    }

    private synchronized void xmmpConnect() {
        try {
            XMPPConnection conn = createXmppConnection();
            conn.connect();
            conn.login(username, password, resourceName);
            ChatManager chatmanager = conn.getChatManager();
            Chat chat = chatmanager.createChat(sendToJid, new MessageListener() {
                public void processMessage(Chat chat, Message msg) { /* ignore incoming messages */ }
            });
            Roster roster = conn.getRoster();
            if (!roster.contains(sendToJid)) {
                addInfo(formatLogMessage("Adding '" + sendToJid + "' to roster."));
                roster.createEntry(sendToJid, sendToJid, new String[] {});
            }
            this.chat = chat;
            this.conn = conn;
        } catch (XMPPException e) {
            this.chat = null;
            this.conn = null;
            addError(formatLogMessage("Error connecting to " + xmmpServer + ':' + xmmpPort), e);
        }
    }

    private String formatLogMessage(String logMessage) {
        return "Appender " + getName() + ": " + logMessage;
    }

}
