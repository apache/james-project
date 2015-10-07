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
package org.apache.james.container.spring.mailbox;

import java.util.Map;

import org.apache.james.adapter.mailbox.MailboxManagerResolver;
import org.apache.james.adapter.mailbox.MailboxManagerResolverException;
import org.apache.james.mailbox.MailboxManager;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class SpringResolver implements MailboxManagerResolver, ApplicationContextAware {
    
    private ApplicationContext context;

    /**
     * @see
     * org.springframework.context.ApplicationContextAware#setApplicationContext
     * (org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
    

    @Override
    public MailboxManager resolveMailboxManager(String mailboxManagerClassName) {
        try {
            return context.getBean(mailboxManagerClassName, MailboxManager.class);
        } catch (BeansException e) {
            throw new MailboxManagerResolverException(e);
        }
    }
    
    @Override
    public Map<String, MailboxManager> getMailboxManagerBeans() {
        try {
            return context.getBeansOfType(MailboxManager.class);
        } catch (BeansException e) {
            throw new MailboxManagerResolverException(e);
        }
    }
    
}