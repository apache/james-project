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

import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.Converter7Bit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.mail.smtp.SMTPTransport;

@SuppressWarnings("deprecation")
public class MailDelivrerToHost {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDelivrerToHost.class);
    public static final String BIT_MIME_8 = "8BITMIME";

    private final RemoteDeliveryConfiguration configuration;
    private final Converter7Bit converter7Bit;
    private final Session session;

    public MailDelivrerToHost(RemoteDeliveryConfiguration remoteDeliveryConfiguration, MailetContext mailetContext) {
        this.configuration = remoteDeliveryConfiguration;
        this.converter7Bit = new Converter7Bit(mailetContext);
        this.session = Session.getInstance(configuration.createFinalJavaxProperties());
    }

    public ExecutionResult tryDeliveryToHost(Mail mail, Collection<InternetAddress> addr, HostAddress outgoingMailServer) throws MessagingException {
        Properties props = getPropertiesForMail(mail);
        LOGGER.debug("Attempting delivery of {} to host {} at {} from {}",
            mail.getName(), outgoingMailServer.getHostName(), outgoingMailServer.getHost(), props.get("mail.smtp.from"));

        // Many of these properties are only in later JavaMail versions
        // "mail.smtp.ehlo"           //default true
        // "mail.smtp.auth"           //default false
        // "mail.smtp.dsn.ret"        //default to nothing... appended as RET= after MAIL FROM line.
        // "mail.smtp.dsn.notify"     //default to nothing... appended as NOTIFY= after RCPT TO line.

        SMTPTransport transport = null;
        try {
            transport = (SMTPTransport) session.getTransport(outgoingMailServer);
            transport.setLocalHost(props.getProperty("mail.smtp.localhost", configuration.getHeloNameProvider().getHeloName()));
            connect(outgoingMailServer, transport);
            transport.sendMessage(adaptToTransport(mail.getMessage(), transport), addr.toArray(InternetAddress[]::new));
            LOGGER.debug("Mail ({})  sent successfully to {} at {} from {} for {}", mail.getName(), outgoingMailServer.getHostName(),
                outgoingMailServer.getHost(), props.get("mail.smtp.from"), mail.getRecipients());
        } finally {
            closeTransport(mail, outgoingMailServer, transport);
        }
        return ExecutionResult.success();
    }

    private Properties getPropertiesForMail(Mail mail) {
        Properties props = session.getProperties();
        props.put("mail.smtp.from", mail.getMaybeSender().asString());
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
