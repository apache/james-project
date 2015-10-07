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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;

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
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class SetMailAttribute extends GenericMailet {

    private HashMap<String, String> attributesToSet = new HashMap<String, String>(2);
    
    private Set<Map.Entry<String, String>> entries;
    
    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Set Mail Attribute Mailet";
    }

    /**
     * Initialize the mailet
     *
     * @throws MailetException if the processor parameter is missing
     */
    public void init() throws MailetException
    {
        Iterator<String> iter = getInitParameterNames();
        while (iter.hasNext()) {
            String name = iter.next();
            String value = getInitParameter (name);
            attributesToSet.put (name,value);
        }
        entries = attributesToSet.entrySet();
    }

    /**
     * Sets the configured attributes
     *
     * @param mail the mail to process
     *
     * @throws MessagingException in all cases
     */
    public void service(Mail mail) throws MessagingException {
        if (entries != null) {
            for (Map.Entry<String, String> entry : entries) {
                mail.setAttribute(entry.getKey(), entry.getValue());
            }
        }
    }
    

}
