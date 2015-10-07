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
package org.apache.james.lmtpserver;

import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.lmtp.LMTPMultiResponse;
import org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook;
import org.apache.james.protocols.smtp.MailAddress;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.smtpserver.DataLineJamesMessageHookHandler;
import org.apache.mailet.Mail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handler which takes care of deliver the mail to the recipients INBOX
 */
public class DataLineLMTPHandler extends DataLineJamesMessageHookHandler {

    private final List<DeliverToRecipientHook> handlers = new ArrayList<DeliverToRecipientHook>();


    @Override
    protected Response processExtensions(SMTPSession session, final Mail mail) {
        LMTPMultiResponse mResponse = null;

        // build a wrapper around the Mail
        final ReadOnlyMailEnvelope env = new ReadOnlyMailEnvelope(mail);

        for (org.apache.mailet.MailAddress recipient : mail.getRecipients()) {
            // TODO: the transformation code between MailAddress is purely to compile. No idea if it does what it's supposed
            MailAddress recipientAddress;
            try {
                recipientAddress = new MailAddress(recipient.getLocalPart(), recipient.getDomain());
            } catch (MailAddressException e) {
                throw new RuntimeException(e);
            }
            Response response = null;
            for (DeliverToRecipientHook handler : handlers) {
                response = AbstractHookableCmdHandler.calcDefaultSMTPResponse(handler.deliver(session, recipientAddress, env));
                if (response != null) {
                    break;
                }
            }
            if (response == null) {
                // Add some default response for not handled responses
                response = new SMTPResponse(SMTPRetCode.LOCAL_ERROR, DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.UNDEFINED_STATUS) + "Temporary error deliver message to " + recipient);
            }
            if (mResponse == null) {
                mResponse = new LMTPMultiResponse(response);
            } else {
                mResponse.addResponse(response);
            }
        }
        return mResponse;
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> markers = new ArrayList<Class<?>>();
        markers.add(DeliverToRecipientHook.class);
        return markers;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (interfaceName.equals(DeliverToRecipientHook.class)) {
            handlers.addAll((Collection<? extends DeliverToRecipientHook>) extension);
        }
    }

    private final class ReadOnlyMailEnvelope extends MailToMailEnvelopeWrapper {

        public ReadOnlyMailEnvelope(Mail mail) {
            super(mail, null);
        }

        @Override
        public OutputStream getMessageOutputStream() throws IOException {
            throw new IOException("Read-only envelope");
        }
    }
}
