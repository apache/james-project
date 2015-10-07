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
package org.apache.james.mailbox.store.quota;

import java.util.List;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ListeningCurrentQuotaUpdater implements MailboxListener, QuotaUpdater {

    private StoreCurrentQuotaManager currentQuotaManager;
    private QuotaRootResolver quotaRootResolver;

    @Inject
    public void setQuotaRootResolver(QuotaRootResolver quotaRootResolver) {
        this.quotaRootResolver = quotaRootResolver;
    }

    @Inject
    public void setCurrentQuotaManager(StoreCurrentQuotaManager currentQuotaManager) {
        this.currentQuotaManager = currentQuotaManager;
    }

    @Override
    public void event(Event event) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(event.getMailboxPath());
            if (event instanceof Added) {
                handleAddedEvent((Added) event, quotaRoot);
            } else if (event instanceof Expunged) {
                handleExpungedEvent((Expunged) event, quotaRoot);
            }
        } catch(MailboxException e) {
            event.getSession().getLog().error("Error while updating quotas", e);
        }
    }

    private void handleExpungedEvent(Expunged event, QuotaRoot quotaRoot) throws MailboxException {
        Expunged expunged = event;
        long addedSize = 0;
        long addedCount = 0;
        List<Long> uids = expunged.getUids();
        for (Long uid : uids) {
            addedSize += expunged.getMetaData(uid).getSize();
            addedCount++;
        }
        // Expunge event can contain no data (expunge performed while no messages marked \Deleted)
        if (addedCount != 0 && addedSize != 0) {
            currentQuotaManager.decrease(quotaRoot, addedCount, addedSize);
        }
    }

    private void handleAddedEvent(Added event, QuotaRoot quotaRoot) throws MailboxException {
        Added added = event;
        long addedSize = 0;
        long addedCount = 0;
        List<Long> uids = added.getUids();
        for (Long uid : uids) {
            addedSize += added.getMetaData(uid).getSize();
            addedCount++;
        }
        if (addedCount != 0 && addedSize != 0) {
            currentQuotaManager.increase(quotaRoot, addedCount, addedSize);
        }
    }

}