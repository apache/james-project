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

import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import javax.mail.MessagingException;

/**
 * This mailet sets removes all attributes set on the Mail instance
 * 
 * Sample configuration:
 * <pre><code>
 * &lt;mailet match="All" class="RemoveAllMailAttributes"/&gt;
 * </code></pre>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 */
public class RemoveAllMailAttributes extends GenericMailet {
    
    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Remove All Mail Attributes Mailet";
    }

    /**
     * Removes all attributes on the Mail
     *
     * @param mail the mail to process
     *
     * @throws MessagingException in all cases
     */
    public void service(Mail mail) throws MessagingException {
        mail.removeAllAttributes ();
    }
    

}
