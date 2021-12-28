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

import static com.sun.mail.smtp.SMTPMessage.NOTIFY_DELAY;
import static com.sun.mail.smtp.SMTPMessage.NOTIFY_FAILURE;
import static com.sun.mail.smtp.SMTPMessage.NOTIFY_NEVER;
import static com.sun.mail.smtp.SMTPMessage.NOTIFY_SUCCESS;
import static com.sun.mail.smtp.SMTPMessage.RETURN_FULL;
import static com.sun.mail.smtp.SMTPMessage.RETURN_HDRS;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.Converter7Bit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableListMultimap;
import com.sun.mail.smtp.SMTPMessage;
import com.sun.mail.smtp.SMTPTransport;

@SuppressWarnings("deprecation")
public class MailDelivrerToHost {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDelivrerToHost.class);
    public static final String BIT_MIME_8 = "8BITMIME";

    private final RemoteDeliveryConfiguration configuration;
    private final Converter7Bit converter7Bit;
    private final Session smtpSession;
    private final Session smtpsSession;

    public MailDelivrerToHost(RemoteDeliveryConfiguration remoteDeliveryConfiguration, MailetContext mailetContext) {
        this.configuration = remoteDeliveryConfiguration;
        this.converter7Bit = new Converter7Bit(mailetContext);
        if (configuration.isSSLEnable()) {
            this.smtpSession = Session.getInstance(configuration.createFinalJavaxProperties());
            this.smtpsSession = Session.getInstance(configuration.createFinalJavaxPropertiesWithSSL());
        } else {
            this.smtpSession = Session.getInstance(configuration.createFinalJavaxProperties());
            this.smtpsSession = this.smtpSession;
        }
    }

    public ExecutionResult tryDeliveryToHost(Mail mail, Collection<InternetAddress> addr, HostAddress outgoingMailServer) throws MessagingException {
        Session session = selectSession(outgoingMailServer);
        Properties props = getPropertiesForMail(mail, session);
        LOGGER.debug("Attempting delivery of {} to host {} at {} from {}",
            mail.getName(), outgoingMailServer.getHostName(), outgoingMailServer.getHost(), props.get(inContext(session, "mail.smtp.from")));

        // Many of these properties are only in later JavaMail versions
        // "mail.smtp.ehlo"           //default true
        // "mail.smtp.auth"           //default false
        // "mail.smtp.dsn.ret"        //default to nothing... appended as RET= after MAIL FROM line.
        // "mail.smtp.dsn.notify"     //default to nothing... appended as NOTIFY= after RCPT TO line.

        SMTPTransport transport = null;
        try {
            transport = (SMTPTransport) session.getTransport(outgoingMailServer);
            transport.setLocalHost(props.getProperty(inContext(session, "mail.smtp.localhost"), configuration.getHeloNameProvider().getHeloName()));
            connect(outgoingMailServer, transport);
            if (mail.dsnParameters().isPresent()) {
                sendDSNAwareEmail(mail, transport, addr);
            } else {
                transport.sendMessage(adaptToTransport(mail.getMessage(), transport), addr.toArray(InternetAddress[]::new));
            }
            LOGGER.debug("Mail ({})  sent successfully to {} at {} from {} for {}", mail.getName(), outgoingMailServer.getHostName(),
                outgoingMailServer.getHost(), props.get(inContext(session, "mail.smtp.from")), mail.getRecipients());
        } finally {
            closeTransport(mail, outgoingMailServer, transport);
        }
        return ExecutionResult.success();
    }

    private Session selectSession(HostAddress host) {
        if (host.getProtocol().equalsIgnoreCase("smtps")) {
            return smtpsSession;
        } else {
            return smtpSession;
        }
    }

    private String inContext(Session session, String name) {
        if (session.getProperties().containsKey("mail.smtps.ssl.enable")) {
            return name.replace("smtp", "smtps");
        } else {
            return name;
        }
    }

    private void sendDSNAwareEmail(Mail mail, SMTPTransport transport, Collection<InternetAddress> addresses) {
        addresses.stream()
            .map(address -> Pair.of(
                mail.dsnParameters()
                    .flatMap(Throwing.<DsnParameters, Optional<DsnParameters.RecipientDsnParameters>>function(
                        dsn -> Optional.ofNullable(dsn.getRcptParameters().get(new MailAddress(address.toString()))))
                        .sneakyThrow())
                    .flatMap(DsnParameters.RecipientDsnParameters::getNotifyParameter)
                    .map(this::toJavaxNotify),
                address))
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                Pair::getKey,
                Pair::getValue))
            .asMap()
            .forEach(Throwing.<Optional<Integer>, Collection<InternetAddress>>biConsumer((maybeNotify, recipients) -> {
                SMTPMessage smtpMessage = asSmtpMessage(mail, transport);
                maybeNotify.ifPresent(smtpMessage::setNotifyOptions);
                transport.sendMessage(smtpMessage, recipients.toArray(InternetAddress[]::new));
            }).sneakyThrow());
    }

    private SMTPMessage asSmtpMessage(Mail mail, SMTPTransport transport) throws MessagingException {
        SMTPMessage smtpMessage = new SMTPMessage(adaptToTransport(mail.getMessage(), transport));
        mail.dsnParameters().flatMap(DsnParameters::getRetParameter)
            .map(this::toJavaxRet)
            .ifPresent(smtpMessage::setReturnOption);
        mail.dsnParameters().flatMap(DsnParameters::getEnvIdParameter)
            .ifPresent(envId -> {
                if (transport.supportsExtension("DSN")) {
                    smtpMessage.setMailExtension("ENVID=" + envId.asString());
                }
            });
        return smtpMessage;
    }

    private int toJavaxRet(DsnParameters.Ret ret) {
        switch (ret) {
            case FULL:
                return RETURN_FULL;
            case HDRS:
                return RETURN_HDRS;
            default:
                throw new NotImplementedException(ret + " cannot be converted to javax.mail parameters");
        }
    }

    private int toJavaxNotify(EnumSet<DsnParameters.Notify> notifies) {
        return notifies.stream()
            .mapToInt(this::toJavaxNotify)
            .sum();
    }

    private int toJavaxNotify(DsnParameters.Notify notify) {
        switch (notify) {
            case NEVER:
                return NOTIFY_NEVER;
            case SUCCESS:
                return NOTIFY_SUCCESS;
            case FAILURE:
                return NOTIFY_FAILURE;
            case DELAY:
                return NOTIFY_DELAY;
            default:
                throw new NotImplementedException(notify + " cannot be converted to javax.mail parameters");
        }
    }

    private Properties getPropertiesForMail(Mail mail, Session session) {
        Properties props = session.getProperties();
        props.put(inContext(session, "mail.smtp.from"), mail.getMaybeSender().asString());
        return props;
    }

    private void connect(HostAddress outgoingMailServer, SMTPTransport transport) throws MessagingException {
        if (configuration.getAuthUser() != null) {
            transport.connect(outgoingMailServer.getHostName(), configuration.getAuthUser(), configuration.getAuthPass());
        } else {
            transport.connect();
        }
    }

    private MimeMessage adaptToTransport(MimeMessage message, SMTPTransport transport) throws MessagingException {
        if (shouldAdapt(transport)) {
            try {
                converter7Bit.convertTo7Bit(message);
            } catch (IOException e) {
                LOGGER.error("Error during the conversion to 7 bit.", e);
            }
        }
        return message;
    }

    private boolean shouldAdapt(SMTPTransport transport) {
        // If the transport is a SMTPTransport (from sun) some performance enhancement can be done.
        // If the transport is not the one developed by Sun we are not sure of how it handles the 8 bit mime stuff, so I
        // convert the message to 7bit.
        return !transport.getClass().getName().endsWith(".SMTPTransport")
            || !transport.supportsExtension(BIT_MIME_8);
        // if the message is already 8bit or binary and the server doesn't support the 8bit extension it has to be converted
        // to 7bit. Javamail api doesn't perform that conversion, but it is required to be a rfc-compliant smtp server.
    }

    private void closeTransport(Mail mail, HostAddress outgoingMailServer, SMTPTransport transport) {
        if (transport != null) {
            try {
                // James-899: transport.close() sends QUIT to the server; if that fails
                // (e.g. because the server has already closed the connection) the message
                // should be considered to be delivered because the error happened outside
                // of the mail transaction (MAIL, RCPT, DATA).
                transport.close();
            } catch (MessagingException e) {
                LOGGER.error("Warning: could not close the SMTP transport after sending mail ({}) to {} at {} for {}; " +
                        "probably the server has already closed the connection. Message is considered to be delivered. Exception: {}",
                    mail.getName(), outgoingMailServer.getHostName(), outgoingMailServer.getHost(), mail.getRecipients(), e.getMessage());
            }
            transport = null;
        }
    }


}
