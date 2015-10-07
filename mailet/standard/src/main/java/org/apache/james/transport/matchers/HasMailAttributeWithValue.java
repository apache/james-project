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
 * <p>This Matcher determines if the mail contains the attribute specified in
 * the condition and if the value answered when the method toString() is 
 * invoked on the attribute is equal to the String value specified in the
 * condition. If both tests are true, all recipients are returned, else null.
 * </p>
 * 
 * <p>Notes:</p>
 * <p>The current matcher implementation expects a single String value to match
 * on. This matcher requires two values, the attribute name and attribute
 * value. This requires some implicit rules to govern how the single value
 * supplied to the matcher is parsed into two values.</p> 
 * <ul>
 * <li>In the match condition, the split between the attribute name and the
 * attribute value is made at the first comma. Attribute names that include
 * a comma will parse incorrectly and therefore are not supported by this
 * matcher.
 * </li>
 * <li>Leading and trailing spaces are removed from both the attribute name and
 * attribute value specified in the condition and the tested attribute value in
 * the mail prior to matching. Therefore, "abc" , " abc", "abc " and " abc " 
 * are considered equivalent.
 * </li>
 * <li>To test for an empty string, do not specify an attribute value after the
 * comma.
 * </li>
 * </ul>
 * 
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="HasMailAttributeWithValue=name, value" class=&quot;&lt;any-class&gt;&quot;&gt;
 * </code></pre>
 *
 * @version CVS $Revision$ $Date$
 * @since 2.2.0
 **/
public class HasMailAttributeWithValue extends GenericMatcher
{

    /**
     * The name of the attribute to match
     */    
    private String fieldAttributeName;

    /**
     * The value of the attribute to match
     */        
    private String fieldAttributeValue;
    

    /**
     * <p>Answers the recipients of the mail if the attribute is present,
     * and has a toString() value equal to the configured value.</p>
     * 
     * @param mail
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException
    {
        Object attributeValue = mail.getAttribute(getAttributeName());

        if (attributeValue != null
            && attributeValue.toString().trim().equals(getAttributeValue()))
            return mail.getRecipients();
        return null;
    }

    /**
     * Returns the attributeName.
     * @return String
     */
    protected String getAttributeName()
    {
        return fieldAttributeName;
    }

    /**
     * Returns the attributeValue.
     * @return String
     */
    protected String getAttributeValue()
    {
        return fieldAttributeValue;
    }

    /**
     * Sets the attributeName.
     * @param attributeName The attributeName to set
     */
    protected void setAttributeName(String attributeName)
    {
        fieldAttributeName = attributeName;
    }

    /**
     * Sets the attributeValue.
     * @param attributeValue The attributeValue to set
     */
    protected void setAttributeValue(String attributeValue)
    {
        fieldAttributeValue = attributeValue;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    public void init() throws MessagingException
    {
        String condition = getCondition().trim();
        int commaPosition = condition.indexOf(',');

        if (-1 == commaPosition)
            throw new MessagingException("Syntax Error. Missing ','.");

        if (0 == commaPosition)
            throw new MessagingException("Syntax Error. Missing attribute name.");

        setAttributeName(condition.substring(0, commaPosition).trim());
        setAttributeValue(condition.substring(commaPosition + 1).trim());
    }
    
    /**
     * Return a string describing this matcher.
     *
     * @return a string describing this matcher
     */
    public String getMatcherInfo() {
        return "Has Mail Attribute With Value Matcher";
    }
}
