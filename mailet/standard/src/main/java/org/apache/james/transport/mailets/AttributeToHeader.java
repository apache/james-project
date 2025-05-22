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

import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import jakarta.mail.MessagingException;

/**
 * Allows setting arbitrary attributes as header.
 *
 * Example set up in order to add true sender in the headers:
 *
 * <pre><code>
 * &lt;mailet match=&quot;All&quot; class=&quot;&lt;AttributeToHeader&gt;&quot;&gt;
 *   &lt;attributeName&gt;org.apache.james.TrueSender&lt;/attributeName&gt;
 *   &lt;headerName&gt;X-True-Sender&lt;/headerName&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class AttributeToHeader extends GenericMailet {
    private AttributeName attributeName;
    private String headerName;
    @Override
    public void service(Mail mail) throws MessagingException {
        mail.getAttribute(attributeName)
            .ifPresent(Throwing.consumer(attribute -> {
                mail.getMessage().addHeader(headerName, attribute.getValue().getValue().toString());
                mail.getMessage().saveChanges();
            }));
    }

    @Override
    public void init() throws MessagingException {
        attributeName = AttributeName.of(getInitParameter("attributeName"));
        headerName = getInitParameter("headerName");
    }

    @Override
    public String getMailetInfo() {
        return "AttributeToHeader Mailet";
    }
}
