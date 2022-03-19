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

package org.apache.james.core.builder;

import java.util.Collections;
import java.util.Properties;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;

public class MimeMessageWrapper extends MimeMessage {

    public static MimeMessageWrapper wrap(MimeMessage mimeMessage) throws MessagingException {
        try {
            return new MimeMessageWrapper(mimeMessage);
        } catch (MessagingException e) {
            // Copying a mime message fails when the body is empty
            // Copying manually the headers is the best alternative...

            MimeMessageWrapper result = new MimeMessageWrapper();
            ThrowingConsumer<Header> consumer = header -> result.addHeader(header.getName(), header.getValue());
            Collections.list(mimeMessage.getAllHeaders())
                .forEach(Throwing.consumer(consumer).sneakyThrow());
            result.setText(""); // Avoid future body reads to fail
            return result;
        }
    }

    private MimeMessageWrapper() {
        super(Session.getDefaultInstance(new Properties()));
    }

    private MimeMessageWrapper(MimeMessage mimeMessage) throws MessagingException {
        super(mimeMessage);
    }

    /**
     * Overrides default javamail behaviour by not altering the Message-ID by
     * default, see <a href="https://issues.apache.org/jira/browse/JAMES-875">JAMES-875</a> and
     * <a href="https://issues.apache.org/jira/browse/JAMES-1010">JAMES-1010</a>
     */
    @Override
    protected void updateMessageID() throws MessagingException {
        if (getMessageID() == null) {
            super.updateMessageID();
        }
    }

}
