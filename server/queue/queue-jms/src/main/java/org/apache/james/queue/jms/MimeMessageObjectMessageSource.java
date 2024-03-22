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
package org.apache.james.queue.jms;

import java.io.IOException;
import java.io.InputStream;

import jakarta.jms.JMSException;
import jakarta.jms.ObjectMessage;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.server.core.MimeMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MimeMessageSource} implementation which reads the data from the
 * payload of an {@link ObjectMessage}. Its important that the payload is a byte
 * array otherwise it will throw an {@link ClassCastException}
 */
public class MimeMessageObjectMessageSource implements MimeMessageSource, Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MimeMessageObjectMessageSource.class);

    private final ObjectMessage message;
    private final SharedByteArrayInputStream in;
    private final String id;
    private byte[] content;

    public MimeMessageObjectMessageSource(ObjectMessage message) throws JMSException {
        this.message = message;
        this.id = message.getJMSMessageID();
        this.content = (byte[]) message.getObject();
        in = new SharedByteArrayInputStream(content);
    }

    @Override
    public long getMessageSize() throws IOException {
        return content.length;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return in.newStream(0, -1);
    }

    @Override
    public String getSourceId() {
        return id;
    }

    @Override
    public void dispose() {
        try {
            in.close();
        } catch (IOException e) {
            //ignore exception during close
        }
        LifecycleUtil.dispose(in);

        try {
            message.clearBody();
        } catch (JMSException e) {
            LOGGER.error("Error clearing JMS message body", e);
        }
        try {
            message.clearProperties();
        } catch (JMSException e) {
            LOGGER.error("Error clearing JMS message properties", e);
        }
        content = null;
    }

}
