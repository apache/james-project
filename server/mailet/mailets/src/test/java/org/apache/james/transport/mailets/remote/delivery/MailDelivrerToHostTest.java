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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.SSLSocketFactory;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.eclipse.angus.mail.smtp.SMTPMessage;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.junit.jupiter.api.Test;

class MailDelivrerToHostTest {
    public static class TlsSessionProvider extends Provider {
        public TlsSessionProvider() {
            super(Type.TRANSPORT, "smtp-tls-session", TlsSessionTransport.class.getName(), "Apache James tests", null);
        }
    }

    /**
     * A TLS session with a server following RFC 3207: once STARTTLS succeeded, STARTTLS is no longer announced.
     * Registered for the smtp-tls-session protocol by META-INF/services/jakarta.mail.Provider.
     */
    public static class TlsSessionTransport extends SMTPTransport {
        static final List<String> MAIL_EXTENSIONS = new CopyOnWriteArrayList<>();
        static final List<String> CONNECTED_HOSTS = new CopyOnWriteArrayList<>();
        static final Map<String, Object> TLS_SETTINGS = new ConcurrentHashMap<>();

        static void reset() {
            MAIL_EXTENSIONS.clear();
            CONNECTED_HOSTS.clear();
            TLS_SETTINGS.clear();
        }

        public TlsSessionTransport(Session session, URLName urlName) {
            super(session, urlName);
        }

        @Override
        protected synchronized boolean protocolConnect(String host, int port, String user, String password) {
            CONNECTED_HOSTS.add(host);
            Properties properties = session.getProperties();
            for (String name : List.of(STARTTLS_ENABLE, CHECK_SERVER_IDENTITY)) {
                Optional.ofNullable(properties.getProperty(name)).ifPresent(value -> TLS_SETTINGS.put(name, value));
            }
            // Read as Angus reads it: without the defaults of the properties
            Optional.ofNullable(properties.get(SOCKET_FACTORY)).ifPresent(value -> TLS_SETTINGS.put(SOCKET_FACTORY, value));
            return true;
        }

        @Override
        public synchronized boolean isSSL() {
            return true;
        }

        @Override
        public boolean supportsExtension(String extension) {
            return extension.equalsIgnoreCase(MailDelivrerToHost.REQUIRE_TLS);
        }

        @Override
        public synchronized void sendMessage(Message message, Address[] addresses) {
            MAIL_EXTENSIONS.add(message instanceof SMTPMessage smtpMessage ? smtpMessage.getMailExtension() : "");
        }

        @Override
        public synchronized void close() {

        }
    }

    private static final String STARTTLS_ENABLE = "mail.smtp.starttls.enable";
    private static final String CHECK_SERVER_IDENTITY = "mail.smtp.ssl.checkserveridentity";
    private static final String SOCKET_FACTORY = "mail.smtp.ssl.socketFactory";

    private static Mail requireTlsMail() throws Exception {
        return FakeMail.builder()
            .name("mail")
            .sender("sender@example.com")
            .recipient("recipient@example.org")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().setText("body"))
            .attribute(new Attribute(AttributeName.of(MailDelivrerToHost.REQUIRE_TLS), AttributeValue.of(true)))
            .build();
    }

    private static SMTPTransport transport(boolean ssl, boolean requireTlsAnnounced) {
        SMTPTransport transport = mock(SMTPTransport.class);
        when(transport.isSSL()).thenReturn(ssl);
        when(transport.supportsExtension(MailDelivrerToHost.REQUIRE_TLS)).thenReturn(requireTlsAnnounced);
        return transport;
    }

    @Test
    void requireTlsMailShouldNotLeaveOverACleartextConnection() throws Exception {
        assertThat(MailDelivrerToHost.requireTlsNotSatisfied(requireTlsMail(), transport(false, true))).isTrue();
    }

    @Test
    void requireTlsMailShouldNotLeaveToAServerWithoutRequireTls() throws Exception {
        assertThat(MailDelivrerToHost.requireTlsNotSatisfied(requireTlsMail(), transport(true, false))).isTrue();
    }

    @Test
    void requireTlsMailShouldLeaveOverTlsToAServerWithRequireTls() throws Exception {
        assertThat(MailDelivrerToHost.requireTlsNotSatisfied(requireTlsMail(), transport(true, true))).isFalse();
    }

    @Test
    void otherMailsShouldLeaveOverACleartextConnection() throws Exception {
        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().setText("body"))
            .build();

        assertThat(MailDelivrerToHost.requireTlsNotSatisfied(mail, transport(false, false))).isFalse();
    }

    @Test
    void requireTlsParameterShouldBeSentWhenTheTlsSessionNoLongerAnnouncesStartTls() throws Exception {
        TlsSessionTransport.MAIL_EXTENSIONS.clear();
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, "james.example.com")
            .build(),
            mock(DomainList.class));
        MailDelivrerToHost testee = new MailDelivrerToHost(configuration, mock(MailetContext.class));

        testee.tryDeliveryToHost(requireTlsMail(),
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.MAIL_EXTENSIONS).containsExactly(MailDelivrerToHost.REQUIRE_TLS);
    }

    @Test
    void mtPriorityParameterShouldNotBeSentToAServerNotAnnouncingIt() throws Exception {
        TlsSessionTransport.MAIL_EXTENSIONS.clear();
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, "james.example.com")
            .build(),
            mock(DomainList.class));
        MailDelivrerToHost testee = new MailDelivrerToHost(configuration, mock(MailetContext.class));
        Mail mail = requireTlsMail();
        mail.setAttribute(new Attribute(AttributeName.of(MailDelivrerToHost.MAIL_PRIORITY_ATTRIBUTE_NAME), AttributeValue.of(5)));

        testee.tryDeliveryToHost(mail,
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.MAIL_EXTENSIONS).containsExactly(MailDelivrerToHost.REQUIRE_TLS);
    }

    private static MailDelivrerToHost opportunisticTlsDelivrer(String trustedHosts) {
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, "james.example.com")
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .setProperty(RemoteDeliveryConfiguration.VERIFY_SERVER_IDENTITY, "false")
            .setProperty(RemoteDeliveryConfiguration.SSL_TRUST_ALL_CERTS, "true")
            .setProperty("mail.smtp.ssl.trust", trustedHosts)
            .build(),
            mock(DomainList.class));
        return new MailDelivrerToHost(configuration, mock(MailetContext.class));
    }

    @Test
    void requireTlsMailShouldValidateTheCertificateEvenWhenAllHostsAreTrusted() throws Exception {
        TlsSessionTransport.reset();

        opportunisticTlsDelivrer("*").tryDeliveryToHost(requireTlsMail(),
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org.", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.TLS_SETTINGS)
            .containsEntry(STARTTLS_ENABLE, "true")
            .containsEntry(CHECK_SERVER_IDENTITY, "true");
        assertThat(TlsSessionTransport.TLS_SETTINGS.get(SOCKET_FACTORY))
            .isInstanceOfSatisfying(ServerNameSSLSocketFactory.class,
                factory -> assertThat(factory.getServerName()).isEqualTo("mx.example.org"));
    }

    @Test
    void requireTlsMailShouldStillConnectToTheResolvedAddress() throws Exception {
        TlsSessionTransport.reset();

        opportunisticTlsDelivrer("*").tryDeliveryToHost(requireTlsMail(),
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org.", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.CONNECTED_HOSTS).containsExactly("192.0.2.1");
    }

    @Test
    void requireTlsMailShouldConnectByNameWhenTheOperatorConfiguredItsSocketFactory() throws Exception {
        TlsSessionTransport.reset();
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.HELO_NAME, "james.example.com")
            .setProperty(RemoteDeliveryConfiguration.START_TLS, "true")
            .setProperty("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            .build(),
            mock(DomainList.class));

        new MailDelivrerToHost(configuration, mock(MailetContext.class)).tryDeliveryToHost(requireTlsMail(),
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.CONNECTED_HOSTS).containsExactly("mx.example.org");
        assertThat(TlsSessionTransport.TLS_SETTINGS)
            .containsEntry(CHECK_SERVER_IDENTITY, "true")
            .doesNotContainKey(SOCKET_FACTORY);
    }

    @Test
    void requireTlsMailShouldKeepTheTrustOfAnExplicitlyTrustedHost() throws Exception {
        TlsSessionTransport.reset();

        opportunisticTlsDelivrer("smarthost.example.org mx.example.org").tryDeliveryToHost(requireTlsMail(),
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.TLS_SETTINGS)
            .containsEntry(STARTTLS_ENABLE, "true")
            .containsEntry(CHECK_SERVER_IDENTITY, "false")
            .doesNotContainKey(SOCKET_FACTORY);
    }

    @Test
    void otherMailsShouldKeepTheOpportunisticSettings() throws Exception {
        TlsSessionTransport.reset();
        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("sender@example.com")
            .recipient("recipient@example.org")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder().setText("body"))
            .build();

        opportunisticTlsDelivrer("*").tryDeliveryToHost(mail,
            List.of(new InternetAddress("recipient@example.org")),
            new HostAddress("mx.example.org", "smtp-tls-session://192.0.2.1:25"));

        assertThat(TlsSessionTransport.TLS_SETTINGS)
            .containsEntry(CHECK_SERVER_IDENTITY, "false")
            .doesNotContainKey(SOCKET_FACTORY);
        assertThat(TlsSessionTransport.CONNECTED_HOSTS).containsExactly("192.0.2.1");
    }

    @Test
    void aWildcardShouldNotTrustAHostExplicitly() {
        assertThat(MailDelivrerToHost.isExplicitlyTrusted("*", "mx.example.org")).isFalse();
    }

    @Test
    void theServerNameShouldBeTheOneTlsIsLayeredWith() throws Exception {
        List<String> layeredHosts = new CopyOnWriteArrayList<>();
        SSLSocketFactory delegate = mock(SSLSocketFactory.class);
        when(delegate.createSocket(any(Socket.class), anyString(), anyInt(), anyBoolean()))
            .thenAnswer(invocation -> {
                layeredHosts.add(invocation.getArgument(1));
                return null;
            });

        new ServerNameSSLSocketFactory(delegate, "mx.example.org").createSocket(new Socket(), "192.0.2.1", 25, true);

        assertThat(layeredHosts).containsExactly("mx.example.org");
    }
}
