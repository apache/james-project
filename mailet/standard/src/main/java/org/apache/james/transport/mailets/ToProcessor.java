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



package org.apache.james.transport.mailets;

import java.util.Collection;
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * <p>This mailet redirects the mail to the named processor</p>
 *
 * <p>Sample configuration:</p>
 * <pre><code
 * >
 * &lt;mailet match="All" class="ToProcessor"&gt;
 *   &lt;processor&gt;spam&lt;/processor&gt;
 *   &lt;notice&gt;Notice attached to the message (optional)&lt;/notice&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 */
public class ToProcessor extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToProcessor.class);

    private boolean debug;
    private ProcessingState processor;
    private Optional<String> noticeText;

    @Override
    public void init() throws MailetException {
        debug = isDebug();
        processor = new ProcessingState(Optional.ofNullable(getInitParameter("processor"))
            .orElseThrow(() -> new MailetException("processor parameter is required")));
        noticeText = Optional.ofNullable(getInitParameter("notice"));
    }

    private boolean isDebug() {
        return getInitParameter("debug", false);
    }

    @Override
    public String getMailetInfo() {
        return "ToProcessor Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (debug) {
            LOGGER.debug("Sending mail {} to {}", mail, processor);
        }
        mail.setState(processor.getValue());
        if (noticeText.isPresent()) {
            setNoticeInErrorMessage(mail);
        }
    }

    private void setNoticeInErrorMessage(Mail mail) {
        if (mail.getErrorMessage() == null) {
            mail.setErrorMessage(noticeText.get());
        } else {
            mail.setErrorMessage(String.format("%s\r\n%s", mail.getErrorMessage(), noticeText.get()));
        }
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(processor);
    }
}
