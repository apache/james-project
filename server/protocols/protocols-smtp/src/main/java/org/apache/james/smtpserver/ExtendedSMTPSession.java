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

import java.util.Set;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.server.core.MimeMessageInputStreamSource;
import org.apache.james.smtpserver.netty.SMTPServer.SMTPHandlerConfigurationDataImpl;

/**
 * {@link SMTPSession} implementation for use with Netty
 */
public class ExtendedSMTPSession extends org.apache.james.protocols.smtp.SMTPSessionImpl {
   
    private final SMTPConfiguration smtpConfiguration;
    private MimeMessageInputStreamSource mimeMessageInputStreamSource;

    public ExtendedSMTPSession(SMTPConfiguration smtpConfiguration, ProtocolTransport transport) {
        super(transport, smtpConfiguration);
        this.smtpConfiguration = smtpConfiguration;
    }

    public boolean verifyIdentity() {
        return !(smtpConfiguration instanceof SMTPHandlerConfigurationDataImpl) || ((SMTPHandlerConfigurationDataImpl) smtpConfiguration).verifyIdentity();
    }

    @Override
    public Set<String> disabledFeatures() {
        return smtpConfiguration.disabledFeatures();
    }

    public MimeMessageInputStreamSource getMimeMessageWriter() {
        return mimeMessageInputStreamSource;
    }

    public void setMimeMessageInputStreamSource(MimeMessageInputStreamSource mimeMessageInputStreamSource) {
        this.mimeMessageInputStreamSource = mimeMessageInputStreamSource;
    }

    @Override
    public void resetState() {
        super.resetState();
        LifecycleUtil.dispose(mimeMessageInputStreamSource);
        mimeMessageInputStreamSource = null;
    }
}
