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
import java.util.Optional;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * This Matcher determines if the exception specified in the condition or
 * the subclasses of it has occured during the processing of the mail.
 * If true, all recipients are returned, else null. This matcher presupposes
 * that the exception has been captured as a Mail attribute
 * {@value org.apache.mailet.Mail#MAILET_ERROR_ATTRIBUTE_NAME} in the process.
 * </p>
 * 
 * <p>
 * Sample configuration:
 * </p>
 * 
 * <pre>
 * <code>
 * &lt;mailet match="HasException=org.apache.james.managesieve.api.ManageSieveException" class=&quot;&lt;any-class&gt;&quot;&gt;
 * </code>
 * </pre>
 *
 * @since 3.0.2
 **/
public class HasException extends GenericMatcher {

    /**
     * The class of the specified exception class to match
     */
    private Class<? extends Throwable> exceptionClass;

    /**
     * <p>
     * Returns the recipients of the mail if the specified exception or the
     * subclasses of it has occured.
     * </p>
     * 
     * @param mail
     */
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Optional<Attribute> exceptionValue = mail.getAttribute(Mail.MAILET_ERROR);

        return exceptionValue
            .map(Attribute::getValue)
            .map(AttributeValue::value)
            .map(Object::getClass)
            .filter(exceptionClass::isAssignableFrom)
            .map(ignored -> mail.getRecipients())
            .orElse(ImmutableList.of());
    }

    @Override
    public void init() throws MessagingException {
        try {
            String exceptionClassName = getCondition().trim();
            this.exceptionClass = castToThrowable(Class.forName(exceptionClassName));
        } catch (ClassNotFoundException e) {
            throw new MessagingException("Specified exception class not found.", e);
        }
    }
    
    @Override
    public String getMatcherInfo() {
        return "Specified Exception Has Occured Matcher";
    }
    
    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable> castToThrowable(Class<?> cls) throws MessagingException {
        if (Throwable.class.isAssignableFrom(cls)) {
            return (Class<? extends Throwable>)cls;
        } else {
            throw new MessagingException("Specified class name is not a Throwable.");
        }
    }
}
