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

package org.apache.james.mailrepository.jpa.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity(name = "JamesMailStore")
@IdClass(JPAMail.JPAMailId.class)
@Table(name = "JAMES_MAIL_STORE", indexes = {
   @Index(name = "REPOSITORY_NAME_MESSAGE_NAME_INDEX", columnList = "REPOSITORY_NAME, MESSAGE_NAME")
})
@NamedQueries({
    @NamedQuery(name = "listMailMessages",
        query = "SELECT mail.messageName FROM JamesMailStore mail WHERE mail.repositoryName = :repositoryName"),
    @NamedQuery(name = "countMailMessages",
        query = "SELECT COUNT(mail) FROM JamesMailStore mail WHERE mail.repositoryName = :repositoryName"),
    @NamedQuery(name = "deleteMailMessages",
        query = "DELETE FROM JamesMailStore mail WHERE mail.repositoryName = :repositoryName AND mail.messageName IN (:messageNames)"),
    @NamedQuery(name = "deleteAllMailMessages",
        query = "DELETE FROM JamesMailStore mail WHERE mail.repositoryName = :repositoryName"),
    @NamedQuery(name = "findMailMessage",
        query = "SELECT mail FROM JamesMailStore mail WHERE mail.repositoryName = :repositoryName AND mail.messageName = :messageName")
})
public class JPAMail {

    static class JPAMailId implements Serializable {
        public JPAMailId() {
        }

        String repositoryName;
        String messageName;

        public boolean equals(Object obj) {
            return obj instanceof JPAMailId
                && Objects.equals(messageName, ((JPAMailId) obj).messageName)
                && Objects.equals(repositoryName, ((JPAMailId) obj).repositoryName);
        }

        public int hashCode() {
            return Objects.hash(messageName, repositoryName);
        }
    }

    @Id
    @Basic(optional = false)
    @Column(name = "REPOSITORY_NAME", nullable = false, length = 255)
    private String repositoryName;

    @Id
    @Basic(optional = false)
    @Column(name = "MESSAGE_NAME", nullable = false, length = 200)
    private String messageName;

    @Basic(optional = false)
    @Column(name = "MESSAGE_STATE", nullable = false, length = 30)
    private String messageState;

    @Basic(optional = true)
    @Column(name = "ERROR_MESSAGE", nullable = true, length = 200)
    private String errorMessage;

    @Basic(optional = true)
    @Column(name = "SENDER", nullable = true, length = 255)
    private String sender;

    @Basic(optional = false)
    @Column(name = "RECIPIENTS", nullable = false)
    private String recipients; // CRLF delimited

    @Basic(optional = false)
    @Column(name = "REMOTE_HOST", nullable = false, length = 255)
    private String remoteHost;

    @Basic(optional = false)
    @Column(name = "REMOTE_ADDR", nullable = false, length = 20)
    private String remoteAddr;

    @Basic(optional = false)
    @Column(name = "LAST_UPDATED", nullable = false)
    private Timestamp lastUpdated;

    @Basic(optional = true)
    @Column(name = "PER_RECIPIENT_HEADERS", nullable = true, length = 10485760)
    @Lob
    private String perRecipientHeaders;

    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "MESSAGE_BODY", nullable = false, length = 1048576000)
    @Lob
    private byte[] messageBody; // TODO: support streaming body where possible (see e.g. JPAStreamingMailboxMessage)

    @Basic(optional = true)
    @Column(name = "MESSAGE_ATTRIBUTES", nullable = true, length = 10485760)
    @Lob
    private String messageAttributes;

    public JPAMail() {
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getMessageName() {
        return messageName;
    }

    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }

    public String getMessageState() {
        return messageState;
    }

    public void setMessageState(String messageState) {
        this.messageState = messageState;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getPerRecipientHeaders() {
        return perRecipientHeaders;
    }

    public void setPerRecipientHeaders(String perRecipientHeaders) {
        this.perRecipientHeaders = perRecipientHeaders;
    }

    public byte[] getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(byte[] messageBody) {
        this.messageBody = messageBody;
    }

    public String getMessageAttributes() {
        return messageAttributes;
    }

    public void setMessageAttributes(String messageAttributes) {
        this.messageAttributes = messageAttributes;
    }

    @Override
    public String toString() {
        return "JPAMail ( "
            + "repositoryName = " + repositoryName
            + ", messageName = " + messageName
            + " )";
    }

    @Override
    public final boolean equals(Object obj) {
        return obj instanceof JPAMail
            && Objects.equals(this.repositoryName, ((JPAMail)obj).repositoryName)
            && Objects.equals(this.messageName, ((JPAMail)obj).messageName);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(repositoryName, messageName);
    }
}
