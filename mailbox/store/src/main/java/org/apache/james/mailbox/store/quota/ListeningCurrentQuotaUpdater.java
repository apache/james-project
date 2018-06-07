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

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListeningCurrentQuotaUpdater implements MailboxListener, QuotaUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListeningCurrentQuotaUpdater.class);

    private final StoreCurrentQuotaManager currentQuotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final MailboxEventDispatcher dispatcher;
    private final QuotaManager quotaManager;

    @Inject
    public ListeningCurrentQuotaUpdater(StoreCurrentQuotaManager currentQuotaManager, QuotaRootResolver quotaRootResolver, MailboxEventDispatcher dispatcher, QuotaManager quotaManager) {
        this.currentQuotaManager = currentQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.dispatcher = dispatcher;
        this.quotaManager = quotaManager;
    }

    @Override
    public ListenerType getType() {
        return currentQuotaManager.getAssociatedListenerType();
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNCHRONOUS;
    }

    @Override
    public void event(Event event) {
        try {
            if (event instanceof Added) {
                Added addedEvent = (Added) event;
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(addedEvent.getMailboxPath());
                handleAddedEvent(addedEvent, quotaRoot);
            } else if (event instanceof Expunged) {
                Expunged expungedEvent = (Expunged) event;
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(expungedEvent.getMailboxPath());
                handleExpungedEvent(expungedEvent, quotaRoot);
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
        dispatcher.quota(expunged.getSession(),
            quotaRoot,
            quotaManager.getMessageQuota(quotaRoot),
            quotaManager.getStorageQuota(quotaRoot));
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
        dispatcher.quota(added.getSession(),
            quotaRoot,
            quotaManager.getMessageQuota(quotaRoot),
            quotaManager.getStorageQuota(quotaRoot));
    }

}