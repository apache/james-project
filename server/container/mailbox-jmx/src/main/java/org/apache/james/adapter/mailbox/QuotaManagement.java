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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SerializableQuota;
import org.apache.james.mailbox.model.SerializableQuotaLimitValue;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
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
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getQuotaRoot")
                     .build()) {
            return quotaRootResolver.getQuotaRoot(new MailboxPath(namespace, Username.of(user), name)).getValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getMaxMessageCount(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getMaxMessageCount")
                     .build()) {
            return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getMaxMessage(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getMaxStorage(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getMaxStorage")
                     .build()) {
            return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getMaxStorage(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaLimitValue<QuotaCountLimit> getGlobalMaxMessageCount() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getGlobalMaxMessageCount")
                     .build()) {
            return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getGlobalMaxMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuotaLimitValue<QuotaSizeLimit> getGlobalMaxStorage() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getGlobalMaxStorage")
                     .build()) {
            return SerializableQuotaLimitValue.valueOf(maxQuotaManager.getGlobalMaxStorage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, SerializableQuotaLimitValue<QuotaCountLimit> maxMessageCount) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "setMaxMessageCount")
                     .build()) {
            maxMessageCount.toValue(QuotaCountLimit::count, QuotaCountLimit.unlimited())
                .ifPresent(
                    Throwing.consumer((QuotaCountLimit value) ->
                        maxQuotaManager.setMaxMessage(quotaRootResolver.fromString(quotaRoot), value))
                        .sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxStorage(String quotaRoot, SerializableQuotaLimitValue<QuotaSizeLimit> maxSize) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "setMaxStorage")
                     .build()) {
            maxSize.toValue(QuotaSizeLimit::size, QuotaSizeLimit.unlimited())
                .ifPresent(
                    Throwing.consumer((QuotaSizeLimit value) ->
                        maxQuotaManager.setMaxStorage(quotaRootResolver.fromString(quotaRoot), value))
                        .sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalMaxMessageCount(SerializableQuotaLimitValue<QuotaCountLimit> maxGlobalMessageCount) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "setGlobalMaxMessageCount")
                     .build()) {
            maxGlobalMessageCount
                .toValue(QuotaCountLimit::count, QuotaCountLimit.unlimited())
                .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxMessage).sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setGlobalMaxStorage(SerializableQuotaLimitValue<QuotaSizeLimit> maxGlobalSize) {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "setGlobalMaxStorage")
                     .build()) {
            maxGlobalSize
                .toValue(QuotaSizeLimit::size, QuotaSizeLimit.unlimited())
                .ifPresent(Throwing.consumer(maxQuotaManager::setGlobalMaxStorage).sneakyThrow());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuota<QuotaCountLimit, QuotaCountUsage> getMessageCountQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getMessageCountQuota")
                     .build()) {
            return SerializableQuota.newInstance(quotaManager.getMessageQuota(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SerializableQuota<QuotaSizeLimit, QuotaSizeUsage> getStorageQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "getStorageQuota")
                     .build()) {
            return SerializableQuota.newInstance(quotaManager.getStorageQuota(quotaRootResolver.fromString(quotaRoot)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
