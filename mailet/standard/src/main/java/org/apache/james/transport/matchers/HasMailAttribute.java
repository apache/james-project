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



package org.apache.james.transport.matchers;


import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

/**
 * <P>This Matcher determines if the mail contains the attribute specified in the
 * condition, and returns all recipients if it is the case.</P>
 * <P>Sample configuration:</P>
 * <PRE><CODE>
 * &lt;mailet match="HasMailAttribute=whatever" class=&quot;&lt;any-class&gt;&quot;&gt;
 * </CODE></PRE>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 **/
public class HasMailAttribute extends GenericMatcher 
{
    
    private String attributeName;

    /**
     * Return a string describing this matcher.
     *
     * @return a string describing this matcher
     */
    public String getMatcherInfo() {
        return "Has Mail Attribute Matcher";
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init () throws MessagingException
    {
        attributeName = getCondition();
    }

    /**
     * @param mail the mail to check.
     * @return all recipients if the condition is the name of an attribute
     * set on the mail
     *
     **/
    public Collection<MailAddress> match (Mail mail) throws MessagingException
    {
        if (mail.getAttribute (attributeName) != null) {
            return mail.getRecipients();
        } 
        return null;
    }
    
}
