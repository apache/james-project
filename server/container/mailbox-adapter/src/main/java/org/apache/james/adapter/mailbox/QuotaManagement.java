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

import java.io.Closeable;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.mail.model.SerializableQuota;
import org.apache.james.mailbox.store.mail.model.SerializableQuotaValue;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;

public class QuotaManagement implements QuotaManagementMBean {

    private final QuotaManager quotaManager;
    private final MaxQuotaManager maxQuotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public QuotaManagement(QuotaManager quotaManager, MaxQuotaManager maxQuotaManager, QuotaRootResolver quotaRootResolver) {
        this.quotaManager = quotaManager;
        this.maxQuotaManager = maxQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getQuotaRoot")
                     .build()) {
            return quotaRootResolver.getQuotaRoot(new MailboxPath(namespace, user, name)).getValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaValue<QuotaCount> getMaxMessageCount(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMaxMessageCount")
                     .build()) {
            return SerializableQuotaValue.valueOf(maxQuotaManager.getMaxMessage(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaValue<QuotaSize> getMaxStorage(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMaxStorage")
                     .build()) {
            return SerializableQuotaValue.valueOf(maxQuotaManager.getMaxStorage(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaValue<QuotaCount> getGlobalMaxMessageCount() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getGlobalMaxMessageCount")
                     .build()) {
            return SerializableQuotaValue.valueOf(maxQuotaManager.getGlobalMaxMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaValue<QuotaSize> getGlobalMaxStorage() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getGlobalMaxStorage")
                     .build()) {
            return SerializableQuotaValue.valueOf(maxQuotaManager.getGlobalMaxStorage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, SerializableQuotaValue<QuotaCount> maxMessageCount) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setMaxMessageCount")
                     .build()) {
            maxMessageCount.toValue(QuotaCount::count, QuotaCount.unlimited())
                .ifPresent(
                    Throwing.consumer((QuotaCount value) ->
                        maxQuotaManager.setMaxMessage(quotaRootResolver.fromString(quotaRoot), value))
                        .sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxStorage(String quotaRoot, SerializableQuotaValue<QuotaSize> maxSize) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setMaxStorage")
                     .build()) {
            maxSize.toValue(QuotaSize::size, QuotaSize.unlimited())
                .ifPresent(
                    Throwing.consumer((QuotaSize value) ->
                        maxQuotaManager.setMaxStorage(quotaRootResolver.fromString(quotaRoot), value))
                        .sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalMaxMessageCount(SerializableQuotaValue<QuotaCount> maxGlobalMessageCount) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setGlobalMaxMessageCount")
                     .build()) {
            maxGlobalMessageCount
                .toValue(QuotaCount::count, QuotaCount.unlimited())
                .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxMessage).sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalMaxStorage(SerializableQuotaValue<QuotaSize> maxGlobalSize) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setGlobalMaxStorage")
                     .build()) {
            maxGlobalSize
                .toValue(QuotaSize::size, QuotaSize.unlimited())
                .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxStorage).sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuota<QuotaCount> getMessageCountQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMessageCountQuota")
                     .build()) {
            return SerializableQuota.newInstance(quotaManager.getMessageQuota(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuota<QuotaSize> getStorageQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getStorageQuota")
                     .build()) {
            return SerializableQuota.newInstance(quotaManager.getStorageQuota(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
