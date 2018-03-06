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

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListeningCurrentQuotaUpdater implements MailboxListener, QuotaUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListeningCurrentQuotaUpdater.class);

    private final StoreCurrentQuotaManager currentQuotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public ListeningCurrentQuotaUpdater(StoreCurrentQuotaManager currentQuotaManager, QuotaRootResolver quotaRootResolver) {
        this.currentQuotaManager = currentQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public ListenerType getType() {
        return currentQuotaManager.getAssociatedListenerType();
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.ASYNCHRONOUS;
    }

    @Override
    public void event(MailboxEvent event) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(event.getMailboxPath());
            if (event instanceof Added) {
                handleAddedEvent((Added) event, quotaRoot);
            } else if (event instanceof Expunged) {
                handleExpungedEvent((Expunged) event, quotaRoot);
            }
        } catch (MailboxException e) {
            LOGGER.error("Error while updating quotas", e);
        }
    }

    private void handleExpungedEvent(Expunged expunged, QuotaRoot quotaRoot) throws MailboxException {
        long addedSize = 0;
        long addedCount = 0;
        List<MessageUid> uids = expunged.getUids();
        for (MessageUid uid : uids) {
            addedSize += expunged.getMetaData(uid).getSize();
            addedCount++;
        }
        // Expunge event can contain no data (expunge performed while no messages marked \Deleted)
        if (addedCount != 0 && addedSize != 0) {
            currentQuotaManager.decrease(quotaRoot, addedCount, addedSize);
        }
    }

    private void handleAddedEvent(Added added, QuotaRoot quotaRoot) throws MailboxException {
        long addedSize = 0;
        long addedCount = 0;
        List<MessageUid> uids = added.getUids();
        for (MessageUid uid : uids) {
            addedSize += added.getMetaData(uid).getSize();
            addedCount++;
        }
        if (addedCount != 0 && addedSize != 0) {
            currentQuotaManager.increase(quotaRoot, addedCount, addedSize);
        }
    }

}