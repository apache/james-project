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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.core.MimeMessageInputStreamSource;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.core.DataLineFilter;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookResultHook;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.apache.james.smtpserver.model.MailetMailAddressAdapter;
import org.apache.james.smtpserver.model.ProtocolMailAddressAdapter;
import org.apache.mailet.Mail;

/**
 * Handles the calling of JamesMessageHooks
 */
public class DataLineJamesMessageHookHandler implements DataLineFilter, ExtensibleHandler {

    private List<JamesMessageHook> messageHandlers;

    private List<HookResultHook> rHooks;

    private List<MessageHook> mHandlers;

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    public Response onLine(SMTPSession session, ByteBuffer lineByteBuffer, LineHandler<SMTPSession> next) {

        byte[] line = new byte[lineByteBuffer.remaining()];
        lineByteBuffer.get(line, 0, line.length);

        MimeMessageInputStreamSource mmiss = (MimeMessageInputStreamSource) session.getAttachment(SMTPConstants.DATA_MIMEMESSAGE_STREAMSOURCE, State.Transaction);

        try {
            OutputStream out = mmiss.getWritableOutputStream();

            // 46 is "."
            // Stream terminated
            if (line.length == 3 && line[0] == 46) {
                out.flush();
                out.close();

                @SuppressWarnings("unchecked")
                List<MailAddress> recipientCollection = (List<MailAddress>) session.getAttachment(SMTPSession.RCPT_LIST, State.Transaction);
                MailAddress mailAddress = (MailAddress) session.getAttachment(SMTPSession.SENDER, State.Transaction);

                List<org.apache.mailet.MailAddress> rcpts = new ArrayList<>();
                for (MailAddress address : recipientCollection) {
                    rcpts.add(new MailetMailAddressAdapter(address));
                }

                MailetMailAddressAdapter mailetMailAddressAdapter = null;
                if (mailAddress != MailAddress.nullSender()) {
                    mailetMailAddressAdapter = new MailetMailAddressAdapter(mailAddress);
                }

                MailImpl mail = new MailImpl(MailImpl.getId(), mailetMailAddressAdapter, rcpts);

                // store mail in the session so we can be sure it get disposed later
                session.setAttachment(SMTPConstants.MAIL, mail, State.Transaction);

                MimeMessageCopyOnWriteProxy mimeMessageCopyOnWriteProxy = null;
                try {
                    mimeMessageCopyOnWriteProxy = new MimeMessageCopyOnWriteProxy(mmiss);
                    mail.setMessage(mimeMessageCopyOnWriteProxy);

                    Response response = processExtensions(session, mail);

                    session.popLineHandler();
                    return response;

                } catch (MessagingException e) {
                    // TODO probably return a temporary problem
                    session.getLogger().info("Unexpected error handling DATA stream", e);
                    return new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unexpected error handling DATA stream.");
                } finally {
                    LifecycleUtil.dispose(mimeMessageCopyOnWriteProxy);
                    LifecycleUtil.dispose(mmiss);
                    LifecycleUtil.dispose(mail);
                }

                // DotStuffing.
            } else if (line[0] == 46 && line[1] == 46) {
                out.write(line, 1, line.length - 1);
                // Standard write
            } else {
                // TODO: maybe we should handle the Header/Body recognition here
                // and if needed let a filter to cache the headers to apply some
                // transformation before writing them to output.
                out.write(line);
            }
        } catch (IOException e) {
            LifecycleUtil.dispose(mmiss);
            SMTPResponse response = new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Error processing message: " + e.getMessage());
            session.getLogger().error("Unknown error occurred while processing DATA.", e);
            return response;
        } catch (AddressException e) {
            LifecycleUtil.dispose(mmiss);
            SMTPResponse response = new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + " Error processing message: " + e.getMessage());
            session.getLogger().error("Invalid email address while processing DATA.", e);
            return response;
        }
        return null;
    }

    protected Response processExtensions(SMTPSession session, Mail mail) {
        if (mail != null && messageHandlers != null) {
            try {
                MimeMessageInputStreamSource mmiss = (MimeMessageInputStreamSource) session.getAttachment(SMTPConstants.DATA_MIMEMESSAGE_STREAMSOURCE, State.Transaction);
                OutputStream out;
                out = mmiss.getWritableOutputStream();
                for (MessageHook rawHandler : mHandlers) {
                    session.getLogger().debug("executing james message handler " + rawHandler);
                    long start = System.currentTimeMillis();

                    HookResult hRes = rawHandler.onMessage(session, new MailToMailEnvelopeWrapper(mail, out));
                    long executionTime = System.currentTimeMillis() - start;

                    if (rHooks != null) {
                        for (HookResultHook rHook : rHooks) {
                            session.getLogger().debug("executing hook " + rHook);
                            hRes = rHook.onHookResult(session, hRes, executionTime, rawHandler);
                        }
                    }

                    SMTPResponse response = AbstractHookableCmdHandler.calcDefaultSMTPResponse(hRes);

                    // if the response is received, stop processing of command
                    // handlers
                    if (response != null) {
                        return response;
                    }
                }

                for (JamesMessageHook messageHandler : messageHandlers) {
                    session.getLogger().debug("executing james message handler " + messageHandler);
                    long start = System.currentTimeMillis();
                    HookResult hRes = messageHandler.onMessage(session, mail);
                    long executionTime = System.currentTimeMillis() - start;
                    if (rHooks != null) {
                        for (HookResultHook rHook : rHooks) {
                            session.getLogger().debug("executing hook " + rHook);
                            hRes = rHook.onHookResult(session, hRes, executionTime, messageHandler);
                        }
                    }

                    SMTPResponse response = AbstractHookableCmdHandler.calcDefaultSMTPResponse(hRes);

                    // if the response is received, stop processing of command
                    // handlers
                    if (response != null) {
                        return response;
                    }
                }
            } finally {
                // Dispose the mail object and remove it
                if (mail != null) {
                    LifecycleUtil.dispose(mail);
                    mail = null;
                }
                // do the clean up
                session.resetState();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (JamesMessageHook.class.equals(interfaceName)) {
            this.messageHandlers = (List<JamesMessageHook>) extension;
            if (messageHandlers == null || messageHandlers.size() == 0) {
                throw new WiringException("No messageHandler configured");
            }
        } else if (MessageHook.class.equals(interfaceName)) {
            this.mHandlers = (List<MessageHook>) extension;
        } else if (HookResultHook.class.equals(interfaceName)) {

            this.rHooks = (List<HookResultHook>) extension;
        }
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = new LinkedList<>();
        classes.add(JamesMessageHook.class);
        classes.add(MessageHook.class);
        classes.add(HookResultHook.class);
        return classes;
    }

    protected class MailToMailEnvelopeWrapper implements MailEnvelope {
        private final Mail mail;
        private final OutputStream out;

        public MailToMailEnvelopeWrapper(Mail mail, OutputStream out) {
            this.mail = mail;
            this.out = out;
        }

        @Override
        public InputStream getMessageInputStream() throws IOException {
            try {
                return new MimeMessageInputStream(mail.getMessage());
            } catch (MessagingException e) {
                throw new IOException("Unable to get inputstream for message", e);
            }
        }

        @Override
        public OutputStream getMessageOutputStream() throws IOException {
            return out;
        }

        @Override
        public List<MailAddress> getRecipients() {
            //TODO: not sure this MailAddress transformation code does the right thing
            List<MailAddress> mailAddressList = new ArrayList<>();
            for (org.apache.mailet.MailAddress address : mail.getRecipients()) {
                try {
                    mailAddressList.add(new MailAddress(address.getLocalPart(), address.getDomain()));
                } catch (MailAddressException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return mailAddressList;
        }

        @Override
        public MailAddress getSender() {
            try {
                return new ProtocolMailAddressAdapter(mail.getSender());
            } catch (MailAddressException e) {
                // should not occur here, cause it should have happened before
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getSize() {
            try {
                return mail.getMessageSize();
            } catch (MessagingException e) {
                return -1;
            }
        }

    }
}