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

import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;

/**
 * <p>This mailet sets attributes on the Mail.</p>
 * 
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="All" class="SetMailAttribute"&gt;
 *   &lt;name1&gt;value1&lt;/name1&gt;
 *   &lt;name2&gt;value2&lt;/name2&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * @since 2.2.0
 */
public class SetMailAttribute extends GenericMailet {

    private ImmutableList<Attribute> entries;
    
    @Override
    public String getMailetInfo() {
        return "Set Mail Attribute Mailet";
    }

    @Override
    public void init() throws MailetException {
        entries = Streams.stream(getInitParameterNames())
            .map(name -> Attribute.convertToAttribute(name, getInitParameter(name)))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        entries.forEach(mail::setAttribute);
    }
    

}
