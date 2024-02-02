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
package org.apache.james.adapter.mailbox;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.copier.MailboxCopier;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MailboxCopier} support via JMX
 */
public class MailboxCopierManagement implements MailboxCopierManagementMBean {

    /**
     * The Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(MailboxCopierManagement.class.getName());

    private MailboxCopier copier;
    private MailboxManagerResolver resolver;

    @Inject
    public void setMailboxCopier(@Named("mailboxcopier") MailboxCopier copier) {
        this.copier = copier;
    }

    @Inject
    public void setMailboxManagerResolver(MailboxManagerResolver resolver) {
        this.resolver = resolver;
    }
    
    @Override
    public Map<String, String> getMailboxManagerBeans() {
        Map<String, String> bMap = new HashMap<>();
        Map<String, MailboxManager> beans = resolver.getMailboxManagerBeans();

        for (Map.Entry<String, MailboxManager> entry : beans.entrySet()) {
            String name = entry.getValue().getClass().getName();
            bMap.put(entry.getKey(), name);
        }

        return bMap;
    }

    @Override
    public void copy(String srcBean, String dstBean) throws Exception {
        if (srcBean.equals(dstBean)) {
            throw new IllegalArgumentException("srcBean and dstBean can not have the same name!");
        }
        try {
            copier.copyMailboxes(resolver.resolveMailboxManager(srcBean), resolver.resolveMailboxManager(dstBean));
        } catch (OverQuotaException e) {
            log.error("An over quota occured during the copy process", e);
            throw new Exception(e.getMessage());
        } catch (MailboxManagerResolverException | MailboxException | IOException e) {
            log.error("An exception occured during the copy process", e);
            throw new Exception(e.getMessage());
        }
    }
    
}
