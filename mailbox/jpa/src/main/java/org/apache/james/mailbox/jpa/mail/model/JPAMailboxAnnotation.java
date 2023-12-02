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

package org.apache.james.mailbox.jpa.mail.model;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.google.common.base.Objects;

@Entity(name = "MailboxAnnotation")
@Table(name = "JAMES_MAILBOX_ANNOTATION")
@NamedQueries({
    @NamedQuery(name = "retrieveAllAnnotations", query = "SELECT annotation FROM MailboxAnnotation annotation WHERE annotation.mailboxId = :idParam"),
    @NamedQuery(name = "retrieveByKey", query = "SELECT annotation FROM MailboxAnnotation annotation WHERE annotation.mailboxId = :idParam AND annotation.key = :keyParam"),
    @NamedQuery(name = "countAnnotationsInMailbox", query = "SELECT COUNT(annotation) FROM MailboxAnnotation annotation WHERE annotation.mailboxId = :idParam"),
    @NamedQuery(name = "retrieveByKeyLike", query = "SELECT annotation FROM MailboxAnnotation annotation WHERE annotation.mailboxId = :idParam AND annotation.key LIKE :keyParam")})
@IdClass(JPAMailboxAnnotationId.class)
public class JPAMailboxAnnotation {

    public static final String MAILBOX_ID = "MAILBOX_ID";
    public static final String ANNOTATION_KEY = "ANNOTATION_KEY";
    public static final String VALUE = "VALUE";

    @Id
    @Column(name = MAILBOX_ID)
    private long mailboxId;

    @Id
    @Column(name = ANNOTATION_KEY, length = 200)
    private String key;

    @Basic()
    @Column(name = VALUE)
    private String value;

    public JPAMailboxAnnotation() {
    }

    public JPAMailboxAnnotation(long mailboxId, String key, String value) {
        this.mailboxId = mailboxId;
        this.key = key;
        this.value = value;
    }

    public long getMailboxId() {
        return mailboxId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JPAMailboxAnnotation) {
            JPAMailboxAnnotation that = (JPAMailboxAnnotation) o;
            return Objects.equal(this.mailboxId, that.mailboxId)
                && Objects.equal(this.key, that.key)
                && Objects.equal(this.value, that.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mailboxId, key, value);
    }
}
