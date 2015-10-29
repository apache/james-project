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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.MAILBOX_BASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.PATH;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.UIDVALIDITY;

import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.MailboxBase;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

/**
 * Data access management for mailbox.
 */
public class CassandraMailboxMapper implements MailboxMapper<CassandraId> {

    public static final String WILDCARD = "%";
    private final Session session;
    private final int maxRetry;
    private final CassandraTypesProvider typesProvider;

    public CassandraMailboxMapper(Session session, CassandraTypesProvider typesProvider, int maxRetry) {
        this.session = session;
        this.maxRetry = maxRetry;
        this.typesProvider = typesProvider;
    }

    @Override
    public void delete(Mailbox<CassandraId> mailbox) throws MailboxException {
        session.execute(
            QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(ID, mailbox.getMailboxId().asUuid())));
    }

    @Override
    public Mailbox<CassandraId> findMailboxByPath(MailboxPath path) throws MailboxException {
        ResultSet resultSet = session.execute(select(FIELDS).from(TABLE_NAME).where(eq(PATH, path.toString())));
        if (resultSet.isExhausted()) {
            throw new MailboxNotFoundException(path);
        } else {
            return mailbox(resultSet.one());
        }
    }

    @Override
    public List<Mailbox<CassandraId>> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        Pattern regex = Pattern.compile(constructEscapedRegexForMailboxNameMatching(path));
        return getMailboxFilteredByNamespaceAndUserStream(path.getNamespace(), path.getUser())
            .filter((row) -> regex.matcher(row.getString(NAME)).matches())
            .map(this::mailbox)
            .collect(Collectors.toList());
    }

    @Override
    public void save(Mailbox<CassandraId> mailbox) throws MailboxException {
        Preconditions.checkArgument(mailbox instanceof SimpleMailbox);
        SimpleMailbox<CassandraId> cassandraMailbox = (SimpleMailbox<CassandraId>) mailbox;
        if (cassandraMailbox.getMailboxId() == null) {
            cassandraMailbox.setMailboxId(CassandraId.timeBased());
        }
        upsertMailbox(cassandraMailbox);
    }

    @Override
    public boolean hasChildren(Mailbox<CassandraId> mailbox, char delimiter) {
        final Pattern regex = Pattern.compile(Pattern.quote( mailbox.getName() + String.valueOf(delimiter)) + ".*");
        return getMailboxFilteredByNamespaceAndUserStream(mailbox.getNamespace(), mailbox.getUser())
            .anyMatch((row) -> regex.matcher(row.getString(NAME)).matches());
    }

    @Override
    public List<Mailbox<CassandraId>> list() throws MailboxException {
        return CassandraUtils.convertToStream(
            session.execute(
                select(FIELDS).from(TABLE_NAME)))
            .map(this::mailbox)
            .collect(Collectors.toList());
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public void updateACL(Mailbox<CassandraId> mailbox, MailboxACL.MailboxACLCommand mailboxACLCommand) throws MailboxException {
        new CassandraACLMapper(mailbox, session, maxRetry).updateACL(mailboxACLCommand);
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    private SimpleMailbox<CassandraId> mailbox(Row row) {
        SimpleMailbox<CassandraId> mailbox = new SimpleMailbox<>(
            new MailboxPath(
                row.getUDTValue(MAILBOX_BASE).getString(MailboxBase.NAMESPACE),
                row.getUDTValue(MAILBOX_BASE).getString(MailboxBase.USER),
                row.getString(NAME)),
            row.getLong(UIDVALIDITY));
        mailbox.setMailboxId(CassandraId.of(row.getUUID(ID)));
        mailbox.setACL(new CassandraACLMapper(mailbox, session, maxRetry).getACL());
        return mailbox;
    }

    private String constructEscapedRegexForMailboxNameMatching(MailboxPath path) {
        return Collections
            .list(new StringTokenizer(path.getName(), WILDCARD, true))
            .stream()
            .map((token) -> {
                    if (token.equals(WILDCARD)) {
                        return ".*";
                    } else {
                        return Pattern.quote((String) token);
                    }
                }
            ).collect(Collectors.joining());
    }

    private void upsertMailbox(SimpleMailbox<CassandraId> mailbox) throws MailboxException {
        session.execute(
            insertInto(TABLE_NAME)
                .value(ID, mailbox.getMailboxId().asUuid())
                .value(NAME, mailbox.getName())
                .value(UIDVALIDITY, mailbox.getUidValidity())
                .value(MAILBOX_BASE, typesProvider.getDefinedUserType(CassandraMailboxTable.MAILBOX_BASE)
                    .newValue()
                    .setString(MailboxBase.NAMESPACE, mailbox.getNamespace())
                    .setString(MailboxBase.USER, mailbox.getUser()))
                .value(PATH, path(mailbox).toString())
        );
    }

    private MailboxPath path(Mailbox<?> mailbox) {
        return new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName());
    }

    private Stream<Row> getMailboxFilteredByNamespaceAndUserStream (String namespace, String user) {
        return CassandraUtils.convertToStream(session.execute(
            select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_BASE, typesProvider.getDefinedUserType(CassandraMailboxTable.MAILBOX_BASE).newValue().setString(MailboxBase.NAMESPACE, namespace).setString(MailboxBase.USER, user)))));
    }

}
