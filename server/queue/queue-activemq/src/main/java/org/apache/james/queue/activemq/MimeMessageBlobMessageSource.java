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
package org.apache.james.queue.activemq;

import java.io.IOException;
import java.io.InputStream;

import jakarta.jms.JMSException;

import org.apache.activemq.BlobMessage;
import org.apache.james.server.core.MimeMessageSource;

/**
 *
 */
public class MimeMessageBlobMessageSource implements MimeMessageSource, ActiveMQSupport {

    private final String sourceId;
    private final BlobMessage message;

    public MimeMessageBlobMessageSource(BlobMessage message) throws JMSException {
        this.message = message;
        this.sourceId = message.getJMSMessageID();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return message.getInputStream();
        } catch (JMSException e) {
            throw new IOException("Unable to open stream", e);
        }
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public long getMessageSize() throws IOException {
        try {
            long size = message.getLongProperty(JAMES_MAIL_MESSAGE_SIZE);

            // if the size is < 1 we seems to not had it stored in the property, so
            // fallback to super implementation
            if (size == -1) {
                MimeMessageSource.super.getMessageSize();
            }
            return size;
        } catch (JMSException e) {
            throw new IOException("Unable to get message size", e);
        }

    }
}
