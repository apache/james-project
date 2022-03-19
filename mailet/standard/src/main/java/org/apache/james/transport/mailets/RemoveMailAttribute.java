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

import org.apache.james.util.streams.Iterators;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * This mailet sets attributes on the Mail.
 * 
 * Sample configuration:
 * <pre><code>
 * &lt;mailet match="All" class="RemoveMailAttribute"&gt;
 *   &lt;name&gt;attribute_name1&lt;/name&gt;
 *   &lt;name&gt;attribute_name2&lt;/name&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class RemoveMailAttribute extends GenericMailet {

    private static final char ATTRIBUTE_SEPARATOR_CHAR = ',';
    protected static final String MAILET_NAME_PARAMETER = "name";

    private ImmutableList<AttributeName> attributesToRemove;

    @Override
    public String getMailetInfo() {
        return "Remove Mail Attribute Mailet";
    }

    @Override
    public void init() throws MailetException {
        String name = getInitParameter(MAILET_NAME_PARAMETER);

        if (Strings.isNullOrEmpty(name)) {
            throw new MailetException("Please configure at least one attribute to remove");
        }

        attributesToRemove = getAttributes(name);
    }

    private ImmutableList<AttributeName> getAttributes(String name) {
        return Iterators.toStream(Splitter.on(ATTRIBUTE_SEPARATOR_CHAR)
            .trimResults()
            .split(name)
            .iterator())
            .map(AttributeName::of)
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Preconditions.checkNotNull(mail);
        attributesToRemove.forEach(mail::removeAttribute);
    }
}
