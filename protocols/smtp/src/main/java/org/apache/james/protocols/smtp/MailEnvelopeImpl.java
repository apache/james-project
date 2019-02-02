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


package org.apache.james.protocols.smtp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;

/**
 * MailEnvelope implementation which stores everything in memory
 * 
 *
 */
public class MailEnvelopeImpl implements MailEnvelope {

    private List<MailAddress> recipients;

    private MaybeSender sender;

    private ByteArrayOutputStream outputStream;

    @Override
    public long getSize() {
        if (outputStream == null) {
            return -1;
        }
        return outputStream.size();
    }

    @Override
    public List<MailAddress> getRecipients() {
        return recipients;
    }

    @Override
    public MaybeSender getMaybeSender() {
        return sender;
    }

    /**
     * Set the recipients of the mail
     * 
     * @param recipientCollection
     */
    public void setRecipients(List<MailAddress> recipientCollection) {
        this.recipients = recipientCollection;
    }

    /**
     * Set the sender of the mail
     * 
     * @param sender
     */
    public void setSender(MaybeSender sender) {
        this.sender = sender;
    }

    @Override
    public OutputStream getMessageOutputStream() {
        if (outputStream == null) {
            // use 100kb as default which should be enough for most emails
            this.outputStream = new ByteArrayOutputStream(100 * 1024);
        }
        return outputStream;
    }

    @Override
    public InputStream getMessageInputStream() {
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
}


