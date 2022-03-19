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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.transport.mailets.utils.MimeMessageModifier;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Strings;

/**
 * Add an prefix (tag) to the subject of a message <br>
 * <br>
 * <p/>
 * Sample Configuration: <br>
 * <pre><code>
 * &lt;mailet match="RecipientIs=robot@james.apache.org" class="TagMessage"&gt;
 * &lt;subjectPrefix&gt;[robot]&lt;/subjectPrefix&gt; &lt;/mailet&gt; <br>
 * </code></pre>
 */
public class AddSubjectPrefix extends GenericMailet {

    private String subjectPrefix;

    @Override
    public void init() throws MessagingException {
        subjectPrefix = getInitParameter("subjectPrefix");

        if (Strings.isNullOrEmpty(subjectPrefix)) {
            throw new MessagingException("Please configure a valid subjectPrefix");
        }
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        new MimeMessageModifier(message)
            .replaceSubject(new MimeMessageUtils(message).subjectWithPrefix(subjectPrefix));
    }

    @Override
    public String getMailetInfo() {
        return "AddSubjectPrefix Mailet";
    }
}
