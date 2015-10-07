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
package org.apache.james.mailbox.jcr.mail.model;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.util.Text;
import org.apache.james.mailbox.jcr.JCRId;
import org.apache.james.mailbox.jcr.JCRImapConstants;
import org.apache.james.mailbox.jcr.Persistent;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.slf4j.Logger;


/**
 * JCR implementation of a {@link Mailbox}
 */
public class JCRMailbox implements Mailbox<JCRId>, JCRImapConstants, Persistent{

    private static final String TAB = " ";

    
    public final static String USER_PROPERTY = "jamesMailbox:mailboxUser";
    public final static String NAMESPACE_PROPERTY = "jamesMailbox:mailboxNamespace";
    public final static String NAME_PROPERTY = "jamesMailbox:mailboxName";
    public final static String UIDVALIDITY_PROPERTY = "jamesMailbox:mailboxUidValidity";
    public final static String LASTUID_PROPERTY = "jamesMailbox:mailboxLastUid";
    public final static String HIGHESTMODSEQ_PROPERTY = "jamesMailbox:mailboxHighestModSeq";

    private String name;
    private long uidValidity;
    private final Logger logger;
    private Node node;


    private String namespace;
    private String user;
    private long lastKnownUid;
    private long highestKnownModSeq;
    
    
    public JCRMailbox( final MailboxPath path, final long uidValidity, Logger logger) {
        this.name = path.getName();
        this.namespace = path.getNamespace();
        this.user = path.getUser();
        this.uidValidity = uidValidity;
        this.logger = logger;
    }
    
    public JCRMailbox( final Node node, final Logger logger) {
        this.node = node;
        this.logger = logger;
    }
    
    public Logger getLog() {
        return logger;
    }

   
    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getName()
     */
    public String getName() {
        if (isPersistent()) {
            try {
                return node.getProperty(NAME_PROPERTY).getString();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + NAME_PROPERTY, e);
            }
        }
        return name;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getUidValidity()
     */
    public long getUidValidity() {
        if (isPersistent()) {
            try {
                return node.getProperty(UIDVALIDITY_PROPERTY).getLong();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + UIDVALIDITY_PROPERTY, e);
            }
        }
        return uidValidity;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setName(java.lang.String)
     */
    public void setName(String name) {  
        if (isPersistent()) {
            try {
                node.setProperty(NAME_PROPERTY, name);
                // move the node 
                // See https://issues.apache.org/jira/browse/IMAP-162
                node.getSession().move(node.getPath(), node.getParent().getPath() + NODE_DELIMITER + Text.escapePath(name));
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + NAME_PROPERTY, e);
            }
        } else {
            this.name = name;
        }
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.jcr.Persistent#getNode()
     */
    public Node getNode() {
        return node;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.jcr.Persistent#isPersistent()
     */
    public boolean isPersistent() {
        return node != null;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.jcr.Persistent#merge(javax.jcr.Node)
     */
    public void  merge(Node node) throws RepositoryException {
        node.setProperty(NAME_PROPERTY, getName());
        node.setProperty(UIDVALIDITY_PROPERTY, getUidValidity());
        String user = getUser();
        if (user == null) {
            user = "";
        }
        node.setProperty(USER_PROPERTY, user);
        node.setProperty(NAMESPACE_PROPERTY, getNamespace());
        node.setProperty(HIGHESTMODSEQ_PROPERTY, getHighestModSeq());
        node.setProperty(LASTUID_PROPERTY, getLastUid());
        this.node = node;
    }
    
    @Override
    public String toString() {
        final String retValue = "Mailbox ( "
            + "mailboxUID = " + this.getMailboxId() + TAB
            + "name = " + this.getName() + TAB
            + "uidValidity = " + this.getUidValidity() + TAB
            + " )";
        return retValue;
    }
    
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) getMailboxId().hashCode();
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final JCRMailbox other = (JCRMailbox) obj;
        if (getMailboxId() != null) {
            if (!getMailboxId().equals(other.getMailboxId()))
        	return false;
        } else {
            if (other.getMailboxId() != null)
        	return false;
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getMailboxId()
     */
    public JCRId getMailboxId() {
        if (isPersistent()) {
            try {
                return JCRId.of(node.getIdentifier());
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + JcrConstants.JCR_UUID, e);
            }
        }
        return null;      
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getNamespace()
     */
    public String getNamespace() {
        if (isPersistent()) {
            try {
                return node.getProperty(NAMESPACE_PROPERTY).getString();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + NAMESPACE_PROPERTY, e);
            }
        }
        return namespace;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getUser()
     */
    public String getUser() {
        if (isPersistent()) {
            try {
                String user = node.getProperty(USER_PROPERTY).getString();
                if (user.trim().length() == 0) {
                    return null;
                } else {
                    return user;
                }
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + USER_PROPERTY, e);
            }
        }
        return user;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setNamespace(java.lang.String)
     */
    public void setNamespace(String namespace) {
        if (isPersistent()) {
            try {
                node.setProperty(NAMESPACE_PROPERTY, namespace);
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + NAMESPACE_PROPERTY, e);
            }
        } else {
            this.namespace = namespace;
        }                
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setUser(java.lang.String)
     */
    public void setUser(String user) {
        if (isPersistent()) {
            try {
                if (user == null) {
                    user = "";
                }
                node.setProperty(USER_PROPERTY, user);
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + NAME_PROPERTY, e);
            }
        } else {
            this.user = user;
        }        
    }

    private long getLastUid() {
        if (isPersistent()) {
            try {
                return node.getProperty(LASTUID_PROPERTY).getLong();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + LASTUID_PROPERTY, e);
            }
        }
        return lastKnownUid;
    }

    private long getHighestModSeq() {
        if (isPersistent()) {
            try {
                return node.getProperty(HIGHESTMODSEQ_PROPERTY).getLong();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + HIGHESTMODSEQ_PROPERTY, e);
            }
        }
        return highestKnownModSeq;
    }
    
    /* (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#getACL()
     */
    @Override
    public MailboxACL getACL() {
        // TODO ACL support
        return SimpleMailboxACL.OWNER_FULL_ACL;
    }

    /* (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Mailbox#setACL(org.apache.james.mailbox.MailboxACL)
     */
    @Override
    public void setACL(MailboxACL acl) {
        // TODO ACL support
    }
    
}
