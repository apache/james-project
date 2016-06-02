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

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraAnnotationTable;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class CassandraAnnotationMapper extends NonTransactionalMapper implements AnnotationMapper {

    private final CassandraId mailboxId;
    private final Session session;

    public CassandraAnnotationMapper(CassandraId mailboxId, Session session) {
        this.mailboxId = mailboxId;
        this.session = session;
    }

    public List<MailboxAnnotation> getAllAnnotations() {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQuery()))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    public List<MailboxAnnotation> getAnnotationsByKeys(Set<String> keys) {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryForKeys(keys)))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    public void deleteAnnotation(String key) {
        session.execute(delete().from(CassandraAnnotationTable.TABLE_NAME)
            .where(eq(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid()))
            .and(eq(CassandraAnnotationTable.KEY, key)));
    }

    public void insertAnnotation(MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());
        session.execute(insertInto(CassandraAnnotationTable.TABLE_NAME)
            .value(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid())
            .value(CassandraAnnotationTable.KEY, mailboxAnnotation.getKey())
            .value(CassandraAnnotationTable.VALUE, mailboxAnnotation.getValue().get()));
    }

    private MailboxAnnotation toAnnotation(Row row) {
        return MailboxAnnotation.newInstance(row.getString(CassandraAnnotationTable.KEY), row.getString(CassandraAnnotationTable.VALUE));
    }

    private Select.Where getStoredAnnotationsQuery() {
        return select(CassandraAnnotationTable.SELECT_FIELDS)
                .from(CassandraAnnotationTable.TABLE_NAME)
                .where(eq(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid()));
    }

    private Select.Where getStoredAnnotationsQueryForKeys(Set<String> keys) {
        return getStoredAnnotationsQuery().and(in(CassandraAnnotationTable.KEY, Lists.newArrayList(keys)));
    }

    @Override
    public void endRequest() {
    }
}
