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

package org.apache.james.mailrepository.memory;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.mailet.Mail;

public class MemoryMailRepository implements MailRepository {

    private final ConcurrentHashMap<MailKey, Mail> mails;

    public MemoryMailRepository() {
        mails = new ConcurrentHashMap<>();
    }

    @Override
    public MailKey store(Mail mail) {
        MailKey mailKey = MailKey.forMail(mail);
        mails.put(mailKey, mail);
        return mailKey;
    }

    @Override
    public Iterator<MailKey> list() {
        return mails.keySet().iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return mails.get(key);
    }

    @Override
    public void remove(Mail mail) {
        mails.remove(MailKey.forMail(mail));
    }

    @Override
    public void remove(Collection<Mail> toRemove) {
        toRemove.stream().map(MailKey::forMail).forEach(this::remove);
    }

    @Override
    public void remove(MailKey key) {
        mails.remove(key);
    }

    @Override
    public boolean lock(MailKey key) {
        return false;
    }

    @Override
    public boolean unlock(MailKey key) {
        return false;
    }

    @Override
    public long size() {
        return mails.size();
    }

    @Override
    public void removeAll() {
        mails.clear();
    }
}
