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

import java.util.Map;
import java.util.Map.Entry;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * <p>Convert attributes to headers</p>
 * 
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="All" class="MailAttributesToMimeHeaders"&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute1;
 * headerName1&lt;/simplemapping&gt;
 * &lt;simplemapping&gt;org.apache.james.attribute2;
 * headerName2&lt;/simplemapping&gt; &lt;/mailet&gt;
 * </code></pre>
 */
public class MailAttributesToMimeHeaders extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailAttributesToMimeHeaders.class);

    private Map<String, String> mappings;

    @Override
    public void init() throws MessagingException {
        String simpleMappings = getInitParameter("simplemapping");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(simpleMappings), "simplemapping is required");

        mappings = MappingArgument.parse(simpleMappings);
    }

    @Override
    public void service(Mail mail) {
        try {
            MimeMessage message = mail.getMessage();
            for (Entry<String, String> entry : mappings.entrySet()) {
                String value = (String) mail.getAttribute(entry.getKey());
                if (value != null) {
                    String headerName = entry.getValue();
                    message.addHeader(headerName, value);
                }
            }
            message.saveChanges();
        } catch (MessagingException e) {
            LOGGER.error("Encountered exception", e);
        }
    }

}
