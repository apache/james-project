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
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.model.SerializableQuota;
import org.apache.james.util.MDCBuilder;

import com.google.common.base.Throwables;

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
            throw Throwables.propagate(e);
        }
    }

    @Override
    public long getMaxMessageCount(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMaxMessageCount")
                     .build()) {
            return maxQuotaManager.getMaxMessage(quotaRootResolver.createQuotaRoot(quotaRoot));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public long getMaxStorage(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMaxStorage")
                     .build()) {
            return maxQuotaManager.getMaxStorage(quotaRootResolver.createQuotaRoot(quotaRoot));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public long getDefaultMaxMessageCount() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getDefaultMaxMessageCount")
                     .build()) {
            return maxQuotaManager.getDefaultMaxMessage();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getDefaultMaxStorage")
                     .build()) {
            return maxQuotaManager.getDefaultMaxStorage();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, long maxMessageCount) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setMaxMessageCount")
                     .build()) {
            maxQuotaManager.setMaxMessage(quotaRootResolver.createQuotaRoot(quotaRoot), maxMessageCount);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void setMaxStorage(String quotaRoot, long maxSize) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setMaxStorage")
                     .build()) {
            maxQuotaManager.setMaxStorage(quotaRootResolver.createQuotaRoot(quotaRoot), maxSize);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void setDefaultMaxMessageCount(long maxDefaultMessageCount) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setDefaultMaxMessageCount")
                     .build()) {
            maxQuotaManager.setDefaultMaxMessage(maxDefaultMessageCount);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void setDefaultMaxStorage(long maxDefaultSize) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setDefaultMaxStorage")
                     .build()) {
            maxQuotaManager.setDefaultMaxStorage(maxDefaultSize);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SerializableQuota getMessageCountQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getMessageCountQuota")
                     .build()) {
            return new SerializableQuota(quotaManager.getMessageQuota(quotaRootResolver.createQuotaRoot(quotaRoot)));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SerializableQuota getStorageQuota(String quotaRoot) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getStorageQuota")
                     .build()) {
            return new SerializableQuota(quotaManager.getStorageQuota(quotaRootResolver.createQuotaRoot(quotaRoot)));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
