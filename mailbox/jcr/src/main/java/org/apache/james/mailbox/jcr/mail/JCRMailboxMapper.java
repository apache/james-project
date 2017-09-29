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
package org.apache.james.mailbox.jcr.mail;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.jcr.AbstractJCRScalingMapper;
import org.apache.james.mailbox.jcr.MailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.mail.model.JCRMailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * JCR implementation of a MailboxMapper
 * 
 * 
 */
public class JCRMailboxMapper extends AbstractJCRScalingMapper implements MailboxMapper {

    @SuppressWarnings("deprecation")
    private static final String XPATH_LANGUAGE = Query.XPATH;

    public JCRMailboxMapper(MailboxSessionJCRRepository repos, MailboxSession session, int scaling) {
        super(repos, session, scaling);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MailboxMapper#delete(org.apache.james
     * .imap.store.mail.model.Mailbox)
     */
    public void delete(Mailbox mailbox) throws MailboxException {
        try {
            Node node = getSession().getNodeByIdentifier(((JCRMailbox) mailbox).getMailboxId().serialize());
                   
            node.remove();
            
        } catch (PathNotFoundException e) {
            // mailbox does not exists..
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to delete mailbox " + mailbox, e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxByPath(org.apache.james.imap.api.MailboxPath)
     */
    public Mailbox findMailboxByPath(MailboxPath path) throws MailboxException, MailboxNotFoundException {
        try {
            String name = Text.escapeIllegalXpathSearchChars(path.getName());
            String user = path.getUser();
            if (user == null ) {
                user = "";
            }
            user = Text.escapeIllegalXpathSearchChars(user);
            String namespace = Text.escapeIllegalXpathSearchChars(path.getNamespace());
            
            QueryManager manager = getSession().getWorkspace().getQueryManager();

            String queryString = "/jcr:root/" + MAILBOXES_PATH + "/" + ISO9075.encodePath(path.getNamespace())  + "//element(*,jamesMailbox:mailbox)[@" + JCRMailbox.NAME_PROPERTY + "='" + name+ "' and @" + JCRMailbox.NAMESPACE_PROPERTY +"='" + namespace + "' and @" + JCRMailbox.USER_PROPERTY + "='" + user + "']";
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            NodeIterator it = result.getNodes();
            if (it.hasNext()) {
                return new JCRMailbox(it.nextNode());
            }
            throw new MailboxNotFoundException(path);
        } catch (PathNotFoundException e) {
            throw new MailboxNotFoundException(path);
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to find mailbox " + path, e);
        }
    }

    @Override
    public Mailbox findMailboxById(MailboxId id) throws MailboxException, MailboxNotFoundException {
        try {
            Node node = getSession().getNodeByIdentifier(id.serialize());
            return new JCRMailbox(node);
        } catch (PathNotFoundException e) {
            throw new MailboxNotFoundException(id.serialize());
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to find mailbox " + id.serialize(), e);
        }
    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#findMailboxWithPathLike(org.apache.james.imap.api.MailboxPath)
     */
    public List<Mailbox> findMailboxWithPathLike(MailboxPath path) throws MailboxException {
        List<Mailbox> mailboxList = new ArrayList<>();
        try {
            String name = Text.escapeIllegalXpathSearchChars(path.getName());
            String user = path.getUser();
            if (user == null ) {
                user = "";
            }
            user = Text.escapeIllegalXpathSearchChars(user);
            String namespace = Text.escapeIllegalXpathSearchChars(path.getNamespace());
            
            QueryManager manager = getSession().getWorkspace().getQueryManager();
            String queryString = "/jcr:root/" + MAILBOXES_PATH + "/" + ISO9075.encodePath(path.getNamespace()) + "//element(*,jamesMailbox:mailbox)[jcr:like(@" + JCRMailbox.NAME_PROPERTY + ",'%" + name + "%') and @" + JCRMailbox.NAMESPACE_PROPERTY +"='" + namespace + "' and @" + JCRMailbox.USER_PROPERTY + "='" + user + "']";
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            NodeIterator it = result.getNodes();
            while (it.hasNext()) {
                mailboxList.add(new JCRMailbox(it.nextNode()));
            }
        } catch (PathNotFoundException e) {
            // nothing todo
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to find mailbox " + path, e);
        }
        return mailboxList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.MailboxMapper#save(org.apache.james.
     * imap.store.mail.model.Mailbox)
     */
    public MailboxId save(Mailbox mailbox) throws MailboxException {
        
        try {
            final JCRMailbox jcrMailbox = (JCRMailbox)mailbox;
            Node node = null;

            if (jcrMailbox.isPersistent()) {
                node = getSession().getNodeByIdentifier(jcrMailbox.getMailboxId().serialize());
            }
            if (node == null) {
                Node rootNode = getSession().getRootNode();
                Node mailboxNode;
                if (rootNode.hasNode(MAILBOXES_PATH) == false) {
                    mailboxNode = rootNode.addNode(MAILBOXES_PATH);
                    mailboxNode.addMixin(JcrConstants.MIX_LOCKABLE);
                    getSession().save();
                } else {
                    mailboxNode = rootNode.getNode(MAILBOXES_PATH);
                }

                node = JcrUtils.getOrAddNode(mailboxNode, Text.escapeIllegalJcrChars(jcrMailbox.getNamespace()), "nt:unstructured");
                if (jcrMailbox.getUser() != null) {
                    node = createUserPathStructure(node, Text.escapeIllegalJcrChars(jcrMailbox.getUser()));
                }
                node = JcrUtils.getOrAddNode(node, Text.escapeIllegalJcrChars(jcrMailbox.getName()), "nt:unstructured");
                node.addMixin("jamesMailbox:mailbox");

                jcrMailbox.merge(node);
                
           } else {
               jcrMailbox.merge(node);
           }
           return jcrMailbox.getMailboxId();
            
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to save mailbox " + mailbox, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#hasChildren(org.apache.james.
     * imap.store.mail.model.Mailbox)
     */
    public boolean hasChildren(Mailbox mailbox, char delimiter)
            throws MailboxException, MailboxNotFoundException {
        try {
            String name = Text.escapeIllegalXpathSearchChars(mailbox.getName());
            String user = mailbox.getUser();
            if (user == null ) {
                user = "";
            }
            user = Text.escapeIllegalXpathSearchChars(user);
            String namespace = Text.escapeIllegalXpathSearchChars(mailbox.getNamespace());
            
            QueryManager manager = getSession().getWorkspace()
                    .getQueryManager();
            String queryString = "/jcr:root/" + MAILBOXES_PATH + "/" + ISO9075.encodePath(mailbox.getNamespace()) 
                    + "//element(*,jamesMailbox:mailbox)[jcr:like(@"
                    + JCRMailbox.NAME_PROPERTY + ",'" + name + delimiter + "%') and @" + JCRMailbox.NAMESPACE_PROPERTY +"='" + namespace + "' and @" + JCRMailbox.USER_PROPERTY + "='" + user + "']";
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE)
                    .execute();
            NodeIterator it = result.getNodes();
            return it.hasNext();
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to retrieve children for mailbox " + mailbox, e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MailboxMapper#list()
     */
    public List<Mailbox> list() throws MailboxException {
        try {
            List<Mailbox> mList = new ArrayList<>();
            QueryManager manager = getSession().getWorkspace().getQueryManager();

            String queryString = "/jcr:root/" + MAILBOXES_PATH + "//element(*,jamesMailbox:mailbox)";
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            NodeIterator it = result.getNodes();
            while (it.hasNext()) {
                mList.add(new JCRMailbox(it.nextNode()));
            }
            return mList;
        } catch (RepositoryException e) {
            throw new MailboxException("Unable to retrieve the list of mailboxes", e);
        }
    }

    @Override
    public void updateACL(Mailbox mailbox, MailboxACL.ACLCommand mailboxACLCommand) throws MailboxException {
        mailbox.setACL(mailbox.getACL().apply(mailboxACLCommand));
    }

    @Override
    public void setACL(Mailbox mailbox, MailboxACL mailboxACL) throws MailboxException {
        mailbox.setACL(mailboxACL);
    }
}
