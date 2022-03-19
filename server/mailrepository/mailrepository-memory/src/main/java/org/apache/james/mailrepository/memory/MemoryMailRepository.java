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

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

public class MemoryMailRepository implements MailRepository {

    private final ConcurrentHashMap<MailKey, Mail> mails;

    public MemoryMailRepository() {
        mails = new ConcurrentHashMap<>();
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey mailKey = MailKey.forMail(mail);
        mails.put(mailKey, cloneMail(mail));

        AuditTrail.entry()
            .protocol("mailrepository")
            .action("store")
            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", mail.getMaybeSender().asString(),
                "recipients", StringUtils.join(mail.getRecipients()))))
            .log("MemoryMailRepository stored mail.");

        return mailKey;
    }

    @Override
    public Iterator<MailKey> list() {
        return mails.keySet().iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return Optional.ofNullable(mails.get(key))
            .map(this::cloneMail)
            .orElse(null);
    }

    @Override
    public void remove(MailKey key) {
        mails.remove(key);
    }

    @Override
    public long size() {
        return mails.size();
    }

    @Override
    public void removeAll() {
        mails.clear();
    }

    private Mail cloneMail(Mail mail) {
        try {
            Mail newMail = mail.duplicate();
            newMail.setName(mail.getName());
            newMail.setState(mail.getState());
            return newMail;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
