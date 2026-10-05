package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.Test;

public interface JamesServerConcreteContract {
    String JAMES_SERVER_HOST = "127.0.0.1";

    default int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    default int imapsPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
    }

    default int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }

    @Test
    default void connectIMAPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, imapPort(jamesServer)));
            assertThat(JamesServerContract.getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
        }
    }

    @Test
    default void connectOnSecondaryIMAPServerIMAPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, imapsPort(jamesServer)));
            assertThat(JamesServerContract.getServerConnectionResponse(socketChannel)).startsWith("* OK JAMES IMAP4rev1 Server");
        }
    }

    @Test
    default void connectSMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(JAMES_SERVER_HOST, smtpPort(jamesServer)));
            assertThat(JamesServerContract.getServerConnectionResponse(socketChannel)).startsWith("220 Apache JAMES awesome SMTP Server");
        }
    }
}
