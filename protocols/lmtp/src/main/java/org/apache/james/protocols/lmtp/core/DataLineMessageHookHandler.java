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
package org.apache.james.protocols.lmtp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.lmtp.LMTPMultiResponse;
import org.apache.james.protocols.lmtp.hook.DeliverToRecipientHook;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

/**
 * {@link DataLineMessageHookHandler} which will use the wired {@link DeliverToRecipientHook}'s to deliver the message to all the valid recipients.
 */
public class DataLineMessageHookHandler extends org.apache.james.protocols.smtp.core.DataLineMessageHookHandler {

    private final List<DeliverToRecipientHook> handlers = new ArrayList<>();

    
    @Override
    protected Response processExtensions(SMTPSession session, MailEnvelope mail) {
        LMTPMultiResponse mResponse = null;

        for (MailAddress recipient : mail.getRecipients()) {
            Response response = null;
            for (DeliverToRecipientHook handler : handlers) {
                response = AbstractHookableCmdHandler.calcDefaultSMTPResponse(handler.deliver(session, recipient, mail));
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
        List<Class<?>> markers = new ArrayList<>();
        markers.add(DeliverToRecipientHook.class);
        return markers;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (interfaceName.equals(DeliverToRecipientHook.class)) {
           handlers.addAll((Collection<? extends DeliverToRecipientHook>) extension);
        }
    }

}
