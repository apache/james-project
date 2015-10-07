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


import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MatcherConfig;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.mail.MessagingException;
import java.io.Serializable;

/**
 * <P>This Matcher determines if the mail contains the attribute specified in the
 * condition and that attribute matches the supplied regular expression,
 * it returns all recipients if that is the case.</P>
 * <P>Sample configuration:</P>
 * <PRE><CODE>
 * &lt;mailet match="HasMailAttributeWithValueRegex=whatever,<regex>" class=&quot;&lt;any-class&gt;&quot;&gt;
 * </CODE></PRE>
 * Note: as it is not possible to put arbitrary objects in the configuration,
 * toString() is called on the attribute value, and that is the value matched against.
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 **/
public class HasMailAttributeWithValueRegex extends GenericMatcher 
{
    
    private String attributeName;
    private Pattern pattern   = null;

    /**
     * Return a string describing this matcher.
     *
     * @return a string describing this matcher
     */
    public String getMatcherInfo() {
        return "Has Mail Attribute Value Matcher";
    }

    public void init (MatcherConfig conf) throws MessagingException
    {
        String condition = conf.getCondition();
        int idx = condition.indexOf(',');
        if (idx != -1) {
            attributeName = condition.substring(0,idx).trim();
            String pattern_string = condition.substring (idx+1, condition.length()).trim();
            try {
                pattern = Pattern.compile(pattern_string);
            } catch(PatternSyntaxException mpe) {
                throw new MessagingException("Malformed pattern: " + pattern_string, mpe);
            }
        } else {
            throw new MessagingException ("malformed condition for HasMailAttributeWithValueRegex. must be of the form: attr,regex");
        }
    }

    /**
     * @param mail the mail to check.
     * @return all recipients if the part of the condition prior to the first equalsign
     * is the name of an attribute set on the mail and the part of the condition after
     * interpreted as a regular expression matches the toString value of the
     * corresponding attributes value.
     **/
    public Collection<MailAddress> match (Mail mail) throws MessagingException
    {
        Serializable obj = mail.getAttribute (attributeName);
        //to be a little more generic the toString of the value is what is matched against
        if ( obj != null && pattern.matcher(obj.toString()).matches()) {
            return mail.getRecipients();
        } 
        return null;
    }
    
}
