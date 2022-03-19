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

import java.util.Map.Entry;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * <p>Convert attributes of type String to headers</p>
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

    private ImmutableMap<AttributeName, String> mappings;

    @Override
    public void init() throws MessagingException {
        String simpleMappings = getInitParameter("simplemapping");

        if (Strings.isNullOrEmpty(simpleMappings)) {
            throw new MessagingException("simplemapping is required");
        }

        mappings = MappingArgument
            .parse(simpleMappings)
            .entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> AttributeName.of(entry.getKey()),
                Entry::getValue));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        mappings
            .entrySet()
            .stream()
            .forEach(entry -> addHeader(entry, mail, message));
        message.saveChanges();
    }

    private void addHeader(Entry<AttributeName, String> entry, Mail mail, MimeMessage message) {
        AttributeUtils
            .getValueAndCastFromMail(mail, entry.getKey(), String.class)
            .ifPresent(Throwing.<String>consumer(value -> {
                String headerName = entry.getValue();
                message.addHeader(headerName, value);
            }).sneakyThrow());
    }

}
