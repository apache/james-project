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
package org.apache.james.mailbox.jcr.user;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.util.Text;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.jcr.AbstractJCRScalingMapper;
import org.apache.james.mailbox.jcr.MailboxSessionJCRRepository;
import org.apache.james.mailbox.jcr.user.model.JCRSubscription;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * JCR implementation of a SubscriptionManager
 * 
 */
public class JCRSubscriptionMapper extends AbstractJCRScalingMapper implements SubscriptionMapper {

    @SuppressWarnings("deprecation")
    private static final String XPATH_LANGUAGE = Query.XPATH;

    public JCRSubscriptionMapper(final MailboxSessionJCRRepository repos, MailboxSession session, final int scaling) {
        super(repos,session, scaling);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.user.SubscriptionMapper#delete(org.apache
     * .james.imap.store.user.model.Subscription)
     */
    public void delete(Subscription subscription) throws SubscriptionException {

        JCRSubscription sub = (JCRSubscription) subscription;
        try {

            Node node = sub.getNode();
            if (node != null) {
                Property prop = node.getProperty(JCRSubscription.MAILBOXES_PROPERTY);
                Value[] values = prop.getValues();
                List<String> newValues = new ArrayList<String>();
                for (int i = 0; i < values.length; i++) {
                    String m = values[i].getString();
                    if (m.equals(sub.getMailbox()) == false) {
                        newValues.add(m);
                    }
                }
                if (newValues.isEmpty() == false) {
                    prop.setValue(newValues.toArray(new String[newValues.size()]));
                } else {
                    prop.remove();
                }
            }
        } catch (PathNotFoundException e) {
            // do nothing
        } catch (RepositoryException e) {
            throw new SubscriptionException(e);
        }

    }



    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.user.SubscriptionMapper#findMailboxSubscriptionForUser(java.lang.String, java.lang.String)
     */
    public Subscription findMailboxSubscriptionForUser(String user, String mailbox) throws SubscriptionException {
        try {
            String queryString = "/jcr:root/" + MAILBOXES_PATH + "//element(*,jamesMailbox:user)[@" + JCRSubscription.USERNAME_PROPERTY + "='" + user + "'] AND [@" + JCRSubscription.MAILBOXES_PROPERTY +"='" + mailbox + "']";

            QueryManager manager = getSession().getWorkspace().getQueryManager();
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            
            NodeIterator nodeIt = result.getNodes();
            if (nodeIt.hasNext()) {
                JCRSubscription sub = new JCRSubscription(nodeIt.nextNode(), mailbox, getLogger());
                return sub;
            }
            
        } catch (PathNotFoundException e) {
            // nothing todo here
        } catch (RepositoryException e) {
            throw new SubscriptionException(e);
        }
        return null;

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.user.SubscriptionMapper#findSubscriptionsForUser
     * (java.lang.String)
     */
    public List<Subscription> findSubscriptionsForUser(String user) throws SubscriptionException {
        List<Subscription> subList = new ArrayList<Subscription>();
        try {
            String queryString = "/jcr:root/" + MAILBOXES_PATH + "//element(*,jamesMailbox:user)[@" + JCRSubscription.USERNAME_PROPERTY + "='" + user + "']";

            QueryManager manager = getSession().getWorkspace().getQueryManager();
            QueryResult result = manager.createQuery(queryString, XPATH_LANGUAGE).execute();
            
            NodeIterator nodeIt = result.getNodes();
            while (nodeIt.hasNext()) {
                Node node = nodeIt.nextNode();
                if (node.hasProperty(JCRSubscription.MAILBOXES_PROPERTY)) {
                    Value[] values = node.getProperty(JCRSubscription.MAILBOXES_PROPERTY).getValues();
                    for (int i = 0; i < values.length; i++) {
                        subList.add(new JCRSubscription(node, values[i].getString(), getLogger()));
                    }
                }
            }
        } catch (PathNotFoundException e) {
            // Do nothing just return the empty list later
        } catch (RepositoryException e) {
            throw new SubscriptionException(e);
        }
        return subList;

    }


    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.user.SubscriptionMapper#save(org.apache.james
     * .imap.store.user.model.Subscription)
     */
    public void save(Subscription subscription) throws SubscriptionException {
        String username = subscription.getUser();
        String mailbox = subscription.getMailbox();
        try {

            Node node = null;
         
            JCRSubscription sub = (JCRSubscription) findMailboxSubscriptionForUser(username, mailbox);
            
            // its a new subscription
            if (sub == null) {
                node = JcrUtils.getOrAddNode(getSession().getRootNode(), MAILBOXES_PATH);
                node = JcrUtils.getOrAddNode(node, Text.escapeIllegalJcrChars(MailboxConstants.USER_NAMESPACE));

                // This is needed to minimize the child nodes a bit
                node = createUserPathStructure(node, Text.escapeIllegalJcrChars(username));
            } else {
                node = sub.getNode();
            }
            
            // Copy new properties to the node
            ((JCRSubscription)subscription).merge(node);

        } catch (RepositoryException e) {
            throw new SubscriptionException(e);
        }
    }

}
