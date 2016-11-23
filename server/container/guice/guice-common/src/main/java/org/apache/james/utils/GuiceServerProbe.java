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
package org.apache.james.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.adapter.mailbox.SerializableQuota;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class GuiceServerProbe implements ExtendedServerProbe, GuiceProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmapGuiceProbe.class);

    private final MailboxManager mailboxManager;
    private final MailboxMapperFactory mailboxMapperFactory;
    private final DomainList domainList;
    private final UsersRepository usersRepository;
    private final RecipientRewriteTable recipientRewriteTable;
    private final SieveRepository sieveRepository;

    @Inject
    private GuiceServerProbe(MailboxManager mailboxManager, MailboxMapperFactory mailboxMapperFactory,
                             DomainList domainList, UsersRepository usersRepository, SieveRepository sieveRepository,
                             RecipientRewriteTable recipientRewriteTable) {
        this.mailboxManager = mailboxManager;
        this.mailboxMapperFactory = mailboxMapperFactory;
        this.domainList = domainList;
        this.usersRepository = usersRepository;
        this.sieveRepository = sieveRepository;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void addUser(String userName, String password) throws Exception {
        usersRepository.addUser(userName, password);
    }

    @Override
    public void removeUser(String username) throws Exception {
        usersRepository.removeUser(username);
    }

    @Override
    public String[] listUsers() throws Exception {
        return Iterables.toArray(ImmutableList.copyOf(usersRepository.list()), String.class);
    }

    @Override
    public void setPassword(String userName, String password) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void addDomain(String domain) throws Exception {
        domainList.addDomain(domain);
    }

    @Override
    public boolean containsDomain(String domain) throws Exception {
        return domainList.containsDomain(domain);
    }

    @Override
    public String getDefaultDomain() throws Exception {
        return domainList.getDefaultDomain();
    }

    @Override
    public void removeDomain(String domain) throws Exception {
        domainList.removeDomain(domain);
    }

    @Override
    public List<String> listDomains() throws Exception {
        return domainList.getDomains();
    }

    @Override
    public Map<String, Mappings> listMappings() throws Exception {
        return recipientRewriteTable.getAllMappings();
    }

    @Override
    public void addAddressMapping(String user, String domain, String toAddress) throws Exception {
        recipientRewriteTable.addAddressMapping(user, domain, toAddress);
    }

    @Override
    public void removeAddressMapping(String user, String domain, String fromAddress) throws Exception {
        recipientRewriteTable.removeAddressMapping(user, domain, fromAddress);
    }

    @Override
    public Mappings listUserDomainMappings(String user, String domain) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        recipientRewriteTable.addRegexMapping(user, domain, regex);
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        recipientRewriteTable.removeRegexMapping(user, domain, regex);
    }

    @Override
    public void copyMailbox(String srcBean, String dstBean) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void deleteUserMailboxesNames(String user) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void createMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user, LOGGER);
            mailboxManager.startProcessingRequest(mailboxSession);
            mailboxManager.createMailbox(new MailboxPath(namespace, user, name), mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    @Override
    public Mailbox getMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user, LOGGER);
            MailboxMapper mailboxMapper = mailboxMapperFactory.getMailboxMapper(mailboxSession);
            return mailboxMapper.findMailboxByPath(new MailboxPath(namespace, user, name));
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private void closeSession(MailboxSession session) {
        if (session != null) {
            mailboxManager.endProcessingRequest(session);
            try {
                mailboxManager.logout(session, true);
            } catch (MailboxException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Collection<String> listUserMailboxes(String user) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user, LOGGER);
            mailboxManager.startProcessingRequest(mailboxSession);
            return searchUserMailboxes(user, mailboxSession)
                    .stream()
                    .map(MailboxMetaData::getPath)
                    .map(MailboxPath::getName)
                    .collect(Collectors.toList());
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    private List<MailboxMetaData> searchUserMailboxes(String username, MailboxSession session) throws MailboxException {
        return mailboxManager.search(
            new MailboxQuery(new MailboxPath(MailboxConstants.USER_NAMESPACE, username, ""),
                "*",
                session.getPathDelimiter()),
            session);
    }

    @Override
    public void deleteMailbox(String namespace, String user, String name) {
        MailboxSession mailboxSession = null;
        try {
            mailboxSession = mailboxManager.createSystemSession(user, LOGGER);
            mailboxManager.startProcessingRequest(mailboxSession);
            mailboxManager.deleteMailbox(new MailboxPath(namespace, user, name), mailboxSession);
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        } finally {
            closeSession(mailboxSession);
        }
    }

    @Override
    public String getQuotaRoot(String namespace, String user, String name) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public SerializableQuota getMessageCountQuota(String quotaRoot) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public SerializableQuota getStorageQuota(String quotaRoot) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public long getMaxMessageCount(String quotaRoot) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public long getMaxStorage(String quotaRoot) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public long getDefaultMaxMessageCount() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public long getDefaultMaxStorage() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public void setMaxMessageCount(String quotaRoot, long maxMessageCount) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public void setMaxStorage(String quotaRoot, long maxSize) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public void setDefaultMaxMessageCount(long maxDefaultMessageCount) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public void setDefaultMaxStorage(long maxDefaultSize) throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public void appendMessage(String username, MailboxPath mailboxPath, InputStream message, Date internalDate, boolean isRecent, Flags flags) 
            throws MailboxException {
        
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username, LOGGER);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, mailboxSession);
        messageManager.appendMessage(message, internalDate, mailboxSession, isRecent, flags);
    }

    @Override
    public void reIndexMailbox(String namespace, String user, String name) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void reIndexAll() throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public long getSieveQuota() throws Exception {
        return sieveRepository.getQuota();
    }

    @Override
    public void setSieveQuota(long quota) throws Exception {
        sieveRepository.setQuota(quota);
    }

    @Override
    public void removeSieveQuota() throws Exception {
        sieveRepository.removeQuota();
    }

    @Override
    public long getSieveQuota(String user) throws Exception {
        return sieveRepository.getQuota(user);
    }

    @Override
    public void setSieveQuota(String user, long quota) throws Exception {
        sieveRepository.setQuota(user, quota);
    }

    @Override
    public void removeSieveQuota(String user) throws Exception {
        sieveRepository.removeQuota(user);
    }

    @Override
    public void addActiveSieveScript(String user, String name, String script) throws Exception {
        sieveRepository.putScript(user, name, script);
        sieveRepository.setActive(user, name);
    }
}
