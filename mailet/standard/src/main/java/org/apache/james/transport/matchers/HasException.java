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
import org.apache.james.core.MailAddress;
import org.apache.mailet.base.GenericMatcher;

/**
 * <p>This Matcher determines if the exception specified in the condition or
 * the subclasses of it has occured during the processing of the mail.
 * If true, all recipients are returned, else null.
 * This matcher presupposes that the exception occured has set at the attribute
 * {@value org.apache.mailet.Mail#MAILET_ERROR_ATTRIBUTE_NAME} in the process.
 * </p>
 * 
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="HasException=org.apache.james.managesieve.api.ManageSieveException" class=&quot;&lt;any-class&gt;&quot;&gt;
 * </code></pre>
 *
 * @version CVS $Revision$ $Date$
 * @since 3.0.2
 **/
public class HasException extends GenericMatcher
{

    /**
     * The name of the exception class to match
     */    
    private String exceptionClassName;
    
    /**
     * The class of the specified exception class to match
     */
    private Class<? extends Throwable> exceptionClass;

    /**
     * <p>Answers the recipients of the mail if the specified exception or
     * the subclasses of it has occured.</p>
     * 
     * @param mail
     */
    public Collection<MailAddress> match(Mail mail) throws MessagingException
    {
        Object exceptionValue = mail.getAttribute(Mail.MAILET_ERROR_ATTRIBUTE_NAME);

        if (exceptionValue != null && exceptionClass.isAssignableFrom(exceptionValue.getClass())) {
            return mail.getRecipients();
        } else {
            return null;
        }
    }

    /**
     * Returns the exceptionClassName.
     * @return String
     */
    protected String getExceptionClassName()
    {
        return exceptionClassName;
    }

    /**
     * Sets the exceptionClassName.
     * @param exceptionClassName The exceptionClassName to set
     */
    protected void setExceptionClassName(String exceptionClassName)
    {
    	this.exceptionClassName = exceptionClassName;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mailet.base.GenericMatcher#init()
     */
    @SuppressWarnings("unchecked")
	public void init() throws MessagingException
    {
        String condition = getCondition().trim();
        setExceptionClassName(condition);
        
    	try {
			Class<?> exceptionClass = Class.forName(exceptionClassName);
			if (Throwable.class.isAssignableFrom(exceptionClass)) {
				this.exceptionClass = (Class<? extends Throwable>)exceptionClass;
			} else {
				throw new MessagingException("Specified class name is not a throwable.");
			}
		} catch (ClassNotFoundException e) {
			throw new MessagingException("Specified exception class not found.");
		}
    }
    
    /**
     * Return a string describing this matcher.
     *
     * @return a string describing this matcher
     */
    public String getMatcherInfo() {
        return "Specified Exception Has Occured Matcher";
    }
}
