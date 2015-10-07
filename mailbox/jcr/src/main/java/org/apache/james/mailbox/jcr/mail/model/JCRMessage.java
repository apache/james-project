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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jcr.JCRId;
import org.apache.james.mailbox.jcr.JCRImapConstants;
import org.apache.james.mailbox.jcr.Persistent;
import org.apache.james.mailbox.store.mail.model.AbstractMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.slf4j.Logger;

/**
 * JCR implementation of {@link Message}
 *
 */
public class JCRMessage extends AbstractMessage<JCRId> implements JCRImapConstants, Persistent{

    private Node node;
    private final Logger logger;
    private SharedInputStream content;
    private String mediaType;
    private Long textualLineCount;
    private String subType;
    private List<JCRProperty> properties;
    private int bodyStartOctet;
    
    private JCRId mailboxUUID;
    private long uid;
    private Date internalDate;
    private long size;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private long modSeq;
    
    private static final String TOSTRING_SEPARATOR = " ";

    public final static String MAILBOX_UUID_PROPERTY = "jamesMailbox:mailboxUUID";
    public final static String UID_PROPERTY = "jamesMailbox:uid";
    public final static String SIZE_PROPERTY = "jamesMailbox:size";
    public final static String ANSWERED_PROPERTY = "jamesMailbox:answered";
    public final static String DELETED_PROPERTY = "jamesMailbox:deleted";
    public final static String DRAFT_PROPERTY =  "jamesMailbox:draft";
    public final static String FLAGGED_PROPERTY = "jamesMailbox:flagged";
    public final static String USERFLAGS_PROPERTY = "jamesMailbox:userFlags";

    public final static String RECENT_PROPERTY = "jamesMailbox:recent";
    public final static String SEEN_PROPERTY = "jamesMailbox:seen";
    public final static String INTERNAL_DATE_PROPERTY = "jamesMailbox:internalDate"; 
    
    public final static String BODY_START_OCTET_PROPERTY = "jamesMailbox:messageBodyStartOctet";
    public final static String HEADER_NODE_TYPE =  "jamesMailbox:messageHeader";

    public final static String PROPERTY_NODE_TYPE =  "jamesMailbox:messageProperty";
    public final static String TEXTUAL_LINE_COUNT_PROPERTY  = "jamesMailbox:messageTextualLineCount";
    public final static String SUBTYPE_PROPERTY  = "jamesMailbox:messageSubType";
    public final static String MODSEQ_PROPERTY = "jamesMailbox:modSeq";

    public JCRMessage(Node node, Logger logger) {
        this.logger= logger;
        this.node = node;
    }
    
    public JCRMessage(JCRId mailboxUUID, Date internalDate, int size, Flags flags, SharedInputStream content,
            int bodyStartOctet,  final PropertyBuilder propertyBuilder, Logger logger) {
        super();
        this.mailboxUUID = mailboxUUID;
        this.internalDate = internalDate;
        this.size = size;
        this.logger = logger;
        setFlags(flags);
        this.content = content;
       
        this.bodyStartOctet = bodyStartOctet;
        this.textualLineCount = propertyBuilder.getTextualLineCount();
        this.mediaType = propertyBuilder.getMediaType();
        this.subType = propertyBuilder.getSubType();
        final List<Property> properties = propertyBuilder.toProperties();
        this.properties = new ArrayList<JCRProperty>(properties.size());
        for (final Property property:properties) {
            this.properties.add(new JCRProperty(property,logger));
        }
        
    }


    /**
     * Create a copy of the given message
     * 
     * @param message
     * @throws IOException 
     */
    public JCRMessage(JCRId mailboxUUID, long uid,  long modSeq, JCRMessage message, Logger logger) throws MailboxException {
        this.mailboxUUID = mailboxUUID;
        this.internalDate = message.getInternalDate();
        this.size = message.getFullContentOctets();
        setFlags(message.createFlags());
        this.uid = uid;
        this.modSeq = modSeq;
        this.logger = logger;
        try {
            this.content = new SharedByteArrayInputStream(IOUtils.toByteArray(message.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
       
        this.bodyStartOctet = (int) (message.getFullContentOctets() - message.getBodyOctets());
        
        PropertyBuilder pBuilder = new PropertyBuilder(message.getProperties());
        this.textualLineCount = message.getTextualLineCount();
        this.mediaType = message.getMediaType();
        this.subType = message.getSubType();
        final List<Property> properties = pBuilder.toProperties();
        this.properties = new ArrayList<JCRProperty>(properties.size());
        for (final Property property:properties) {
            this.properties.add(new JCRProperty(property,  logger));
        }
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getFullContentOctets()
     */
    public long getFullContentOctets() {
        if (isPersistent()) {
            try {
                return node.getProperty(SIZE_PROPERTY).getLong();
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve property " + SIZE_PROPERTY, e);

            }
            return 0;
        }
        return size;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getMediaType()
     */
    public String getMediaType() {
        if (isPersistent()) {
            try {
                return node.getNode(JcrConstants.JCR_CONTENT).getProperty(JcrConstants.JCR_MIMETYPE).getString();
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve node " + JcrConstants.JCR_MIMETYPE, e);
            }
            return null;
        }
        return mediaType;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getProperties()
     */
    public List<Property> getProperties() {
        if (isPersistent()) {
            try {
                List<Property> properties = new ArrayList<Property>();
                NodeIterator nodeIt = node.getNodes("messageProperty");
                while (nodeIt.hasNext()) {
                    properties.add(new JCRProperty(nodeIt.nextNode(), logger));
                }
                return properties;
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve nodes messageProperty", e);
            }
        }
        return new ArrayList<Property>(properties);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getSubType()
     */
    public String getSubType() {
        if (isPersistent()) {
            try {
                return node.getProperty(SUBTYPE_PROPERTY).getString();
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve node " + SUBTYPE_PROPERTY, e);
            }
            return null;
        }
        return subType;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Document#getTextualLineCount()
     */
    public Long getTextualLineCount() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(TEXTUAL_LINE_COUNT_PROPERTY)) {
                    return node.getProperty(TEXTUAL_LINE_COUNT_PROPERTY).getLong();
                } 
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve property " + TEXTUAL_LINE_COUNT_PROPERTY, e);

            }
            return null;
        }
        return textualLineCount;
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

    public String getUUID() {
        if (isPersistent()) {
            try {
                return node.getIdentifier();
            } catch (RepositoryException e) {
                logger.error("Unable to access UUID", e);
            }
        }
        return null;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.jcr.Persistent#merge(javax.jcr.Node)
     */
    public void merge(Node node) throws RepositoryException, IOException {

        // update the flags 
        node.setProperty(ANSWERED_PROPERTY, isAnswered());
        node.setProperty(DELETED_PROPERTY, isDeleted());
        node.setProperty(DRAFT_PROPERTY, isDraft());
        node.setProperty(FLAGGED_PROPERTY, isFlagged());
        node.setProperty(RECENT_PROPERTY, isRecent());
        node.setProperty(SEEN_PROPERTY, isSeen());
        node.setProperty(USERFLAGS_PROPERTY, createFlags().getUserFlags());
        // This stuff is only ever changed on a new message
        // so if it is persistent we don'T need to set all the of this.
        //
        // This also fix https://issues.apache.org/jira/browse/IMAP-159
        if (isPersistent() == false) {
            node.setProperty(SIZE_PROPERTY, getFullContentOctets());
            node.setProperty(MAILBOX_UUID_PROPERTY, getMailboxId().serialize());
            node.setProperty(UID_PROPERTY, getUid());
            node.setProperty(MODSEQ_PROPERTY, getModSeq());

            if (getInternalDate() == null) {
                internalDate = new Date();
            }

            Calendar cal = Calendar.getInstance();

            cal.setTime(getInternalDate());
            node.setProperty(INTERNAL_DATE_PROPERTY, cal);

            Node contentNode = JcrUtils.getOrAddNode(node, JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
            Binary binaryContent = contentNode.getSession().getValueFactory().createBinary(getFullContent());
            contentNode.setProperty(JcrConstants.JCR_DATA, binaryContent);
            contentNode.setProperty(JcrConstants.JCR_MIMETYPE, getMediaType());

            if (getTextualLineCount() != null) {
                node.setProperty(TEXTUAL_LINE_COUNT_PROPERTY, getTextualLineCount());
            }
            node.setProperty(SUBTYPE_PROPERTY, getSubType());
            node.setProperty(BODY_START_OCTET_PROPERTY, getBodyStartOctet());


            List<Property> currentProperties = getProperties();
            List<Property> newProperites = new ArrayList<Property>();
            for (int i = 0; i < currentProperties.size(); i++) {
                Property prop = currentProperties.get(i);
                newProperites.add(new JCRProperty(prop, logger));
            }
            // remove old properties, we will add a bunch of new ones
            NodeIterator iterator = node.getNodes("messageProperty");
            while (iterator.hasNext()) {
                iterator.nextNode().remove();
            }

            // store new properties
            for (int i = 0; i < newProperites.size(); i++) {
                JCRProperty prop = (JCRProperty) newProperites.get(i);
                Node propNode = node.addNode("messageProperty", "nt:unstructured");
                propNode.addMixin(PROPERTY_NODE_TYPE);
                prop.merge(propNode);
            }
        }
        this.node = node;

    }
    
    @Override
    protected String[] createUserFlags() {
        return userFlags;
    }

    @Override
    protected int getBodyStartOctet() {
        if (isPersistent()) {
            try {
                return (int)node.getProperty(BODY_START_OCTET_PROPERTY).getLong();
            } catch (RepositoryException e) {
                logger.error("Unable to retrieve property " + TEXTUAL_LINE_COUNT_PROPERTY, e);

            }
            return 0;
        }
        return bodyStartOctet;
    }
    

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        
        final JCRMessage other = (JCRMessage) obj;

        if (getUUID() != null) {
            if (!getUUID().equals(other.getUUID()))
        	return false;
        } else {
            if (other.getUUID() != null)
        	return false;
        }
        if (getMailboxId() != null) {
            if (!getMailboxId().equals(other.getMailboxId()))
        	return false;
        } else {
            if (other.getMailboxId() != null)
        	return false;
        }
        if (getId() != null) {
            if (!getId().equals(other.getId()))
        	return false;
        } else {
            if (other.getId() != null)
        	return false;
        }
        return true;
    }



    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.MailboxMembership#getInternalDate
     * ()
     */
    public Date getInternalDate() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(INTERNAL_DATE_PROPERTY)) {
                    return node.getProperty(INTERNAL_DATE_PROPERTY).getDate().getTime();
                }

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + FLAGGED_PROPERTY,
                                e);
            }
            return null;
        }
        return internalDate;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.MailboxMembership#getMailboxId()
     */
    public JCRId getMailboxId() {
        if (isPersistent()) {
            try {
                return JCRId.of(node.getProperty(MAILBOX_UUID_PROPERTY).getString());
            } catch (RepositoryException e) {
                logger.error("Unable to access property "
                        + MAILBOX_UUID_PROPERTY, e);
            }
        }
        return mailboxUUID;
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#getUid()
     */
    public long getUid() {
        if (isPersistent()) {
            try {
                return node.getProperty(UID_PROPERTY).getLong();

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + UID_PROPERTY, e);
            }
            return 0;
        }
        return uid;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.MailboxMembership#isAnswered()
     */
    public boolean isAnswered() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(ANSWERED_PROPERTY)) {
                    return node.getProperty(ANSWERED_PROPERTY).getBoolean();
                }

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + ANSWERED_PROPERTY,
                        e);
            }
            return false;
        }
        return answered;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#isDeleted()
     */
    public boolean isDeleted() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(DELETED_PROPERTY)) {
                    return node.getProperty(DELETED_PROPERTY).getBoolean();
                }

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + DELETED_PROPERTY,
                                e);
            }
            return false;
        }
        return deleted;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#isDraft()
     */
    public boolean isDraft() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(DRAFT_PROPERTY)) {
                    return node.getProperty(DRAFT_PROPERTY).getBoolean();
                }
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + DRAFT_PROPERTY, e);
            }
            return false;
        }
        return draft;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#isFlagged()
     */
    public boolean isFlagged() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(FLAGGED_PROPERTY)) {
                    return node.getProperty(FLAGGED_PROPERTY).getBoolean();
                }
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + FLAGGED_PROPERTY,
                                e);
            }
            return false;
        }
        return flagged;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#isRecent()
     */
    public boolean isRecent() {
        if (isPersistent()) {
            try {
                if (node.hasProperty(RECENT_PROPERTY)) {
                    return node.getProperty(RECENT_PROPERTY).getBoolean();
                }
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + RECENT_PROPERTY, e);
            }
            return false;
        }
        return recent;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.MailboxMembership#isSeen()
     */
    public boolean isSeen() {
        if (isPersistent()) {
            try {
                return node.getProperty(SEEN_PROPERTY).getBoolean();

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + SEEN_PROPERTY, e);
            }
            return false;
        }
        return seen;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.MailboxMembership#setFlags(javax
     * .mail.Flags)
     */
    public void setFlags(Flags flags) {
        if (isPersistent()) {
            try {
                node.setProperty(ANSWERED_PROPERTY,
                        flags.contains(Flags.Flag.ANSWERED));
                node.setProperty(DELETED_PROPERTY,
                        flags.contains(Flags.Flag.DELETED));
                node.setProperty(DRAFT_PROPERTY,
                        flags.contains(Flags.Flag.DRAFT));
                node.setProperty(FLAGGED_PROPERTY,
                        flags.contains(Flags.Flag.FLAGGED));
                node.setProperty(RECENT_PROPERTY,
                        flags.contains(Flags.Flag.RECENT));
                node.setProperty(SEEN_PROPERTY,
                        flags.contains(Flags.Flag.SEEN));
                node.setProperty(USERFLAGS_PROPERTY, flags.getUserFlags());
            } catch (RepositoryException e) {
                logger.error("Unable to set flags", e);
            }
        } else {
            answered = flags.contains(Flags.Flag.ANSWERED);
            deleted = flags.contains(Flags.Flag.DELETED);
            draft = flags.contains(Flags.Flag.DRAFT);
            flagged = flags.contains(Flags.Flag.FLAGGED);
            recent = flags.contains(Flags.Flag.RECENT);
            seen = flags.contains(Flags.Flag.SEEN);
            userFlags = flags.getUserFlags();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.MailboxMembership#unsetRecent()
     */
    public void unsetRecent() {
        if (isPersistent()) {
            try {
                node.setProperty(RECENT_PROPERTY, false);

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + RECENT_PROPERTY, e);
            }
        } else {
            recent = false;
        }
    }



    public String getId() {
        if (isPersistent()) {
            try {
                return node.getIdentifier();
            } catch (RepositoryException e) {
                logger.error("Unable to access property " + JcrConstants.JCR_UUID, e);
            }
        }
        return null;      
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + getUUID().hashCode();
        result = PRIME * result + getMailboxId().hashCode();
        return result;
    }


    public String toString() {
        final String retValue = 
            "message("
            + "uuid = " + getUUID()
            + "mailboxUUID = " + this.getMailboxId() + TOSTRING_SEPARATOR
            + "uuid = " + this.getId() + TOSTRING_SEPARATOR
            + "internalDate = " + this.getInternalDate() + TOSTRING_SEPARATOR
            + "size = " + this.getFullContentOctets() + TOSTRING_SEPARATOR
            + "answered = " + this.isAnswered() + TOSTRING_SEPARATOR
            + "deleted = " + this.isDeleted() + TOSTRING_SEPARATOR
            + "draft = " + this.isDraft() + TOSTRING_SEPARATOR
            + "flagged = " + this.isFlagged() + TOSTRING_SEPARATOR
            + "recent = " + this.isRecent() + TOSTRING_SEPARATOR
            + "seen = " + this.isSeen() + TOSTRING_SEPARATOR
            + " )";

        return retValue;
    }


    @Override
    public InputStream getFullContent() throws IOException {
        if (isPersistent()) {
            try {
                //TODO: Maybe we should cache this somehow...
                InputStream contentStream = node.getNode(JcrConstants.JCR_CONTENT).getProperty(JcrConstants.JCR_DATA).getBinary().getStream();
                return contentStream;
            } catch (RepositoryException e) {
                throw new IOException("Unable to retrieve property " + JcrConstants.JCR_CONTENT, e);
            }
        }
        return content.newStream(0, -1);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getBodyContent()
     */
    public InputStream getBodyContent() throws IOException {
        InputStream body = getFullContent();
        IOUtils.skipFully(body,  getBodyStartOctet());
        return body;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getModSeq()
     */
    public long getModSeq() {
        if (isPersistent()) {
            try {
                return node.getProperty(MODSEQ_PROPERTY).getLong();

            } catch (RepositoryException e) {
                logger.error("Unable to access property " + MODSEQ_PROPERTY, e);
            }
            return 0;
        }
        return modSeq;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#setModSeq(long)
     */
    public void setModSeq(long modSeq) {
        if (isPersistent()) {
            try {
                node.setProperty(MODSEQ_PROPERTY, modSeq);
            } catch (RepositoryException e) {
                logger.error("Unable to set mod-sequence", e);
            }
        } else {
            this.modSeq = modSeq;
        }  
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#setUid(long)
     */
    public void setUid(long uid) {
        if (isPersistent()) {
            try {
                node.setProperty(UID_PROPERTY, uid);
            } catch (RepositoryException e) {
                logger.error("Unable to set uid", e);
            }
        } else {
            this.uid = uid;
        }          
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.model.Message#getHeaderContent()
     */
    public InputStream getHeaderContent() throws IOException {
        long limit = getBodyStartOctet();
        if (limit < 0) {
            limit = 0;
        }
        return new BoundedInputStream(getFullContent(), limit);
    }
}
