/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.james.mailrepository.jcr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.james.core.MailAddress;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.lib.AbstractMailRepository;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mail repository that is backed by a JCR content repository.
 *
 * @Depracted: See JAMES-2323
 *
 * Will be removed in James 3.2.0 upcoming release.
 *
 * Use a modern, maintained MailRepository instead. For instead FileMailRepository.
 */
@Deprecated
public class JCRMailRepository extends AbstractMailRepository implements MailRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JCRMailRepository.class);

    private static final String MAIL_PATH = "mailrepository";

    private Repository repository;
    private SimpleCredentials creds;
    private String workspace;


    @Inject
    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        // register the nodetype
        CndImporter.registerNodeTypes(new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("org/apache/james/imap/jcr/james.cnd")), login());
    }

    /**
     * @see
     * org.apache.james.mailrepository.lib.AbstractMailRepository
     * #doConfigure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    @Override
    public void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        this.workspace = config.getString("workspace", null);
        String username = config.getString("username", null);
        String password = config.getString("password", null);

        if (username != null && password != null) {
            this.creds = new SimpleCredentials(username, password.toCharArray());
        }
    }

    protected String toSafeName(String key) {
        return ISO9075.encode(Text.escapeIllegalJcrChars(key));
    }

    private Session login() throws RepositoryException {
        return repository.login(creds, workspace);
    }

    public Iterator<String> list() throws MessagingException {
        try {
            Session session = login();
            try {
                Collection<String> keys = new ArrayList<>();
                QueryManager manager = session.getWorkspace().getQueryManager();
                @SuppressWarnings("deprecation")
                Query query = manager.createQuery("/jcr:root/" + MAIL_PATH + "//element(*,james:mail)", Query.XPATH);
                NodeIterator iterator = query.execute().getNodes();
                while (iterator.hasNext()) {
                    String name = iterator.nextNode().getName();
                    keys.add(Text.unescapeIllegalJcrChars(name));
                }
                return keys.iterator();
            } finally {
                session.logout();
            }
        } catch (RepositoryException e) {
            throw new MessagingException("Unable to list messages", e);
        }
    }

    public Mail retrieve(String key) throws MessagingException {
        try {
            Session session = login();
            try {
                String name = toSafeName(key);
                QueryManager manager = session.getWorkspace().getQueryManager();
                @SuppressWarnings("deprecation")
                Query query = manager.createQuery("/jcr:root/" + MAIL_PATH + "//element(" + name + ",james:mail)", Query.XPATH);
                NodeIterator iterator = query.execute().getNodes();
                if (iterator.hasNext()) {
                    return getMail(iterator.nextNode());
                } else {
                    return null;
                }
            } finally {
                session.logout();
            }
        } catch (IOException | RepositoryException e) {
            throw new MessagingException("Unable to retrieve message: " + key, e);
        }
    }

    // -------------------------------------------------------------< private >

    /**
     * Reads a mail message from the given mail node.
     * 
     * @param node
     *            mail node
     * @return mail message
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    private Mail getMail(Node node) throws MessagingException, RepositoryException, IOException {
        String name = Text.unescapeIllegalJcrChars(node.getName());
        MailImpl mail = new MailImpl(name, getSender(node), getRecipients(node), getMessage(node));
        mail.setState(getState(node));
        mail.setLastUpdated(getLastUpdated(node));
        mail.setErrorMessage(getError(node));
        mail.setRemoteHost(getRemoteHost(node));
        mail.setRemoteAddr(getRemoteAddr(node));
        getAttributes(node, mail);
        return mail;
    }

    /**
     * Writes the mail message to the given mail node.
     * 
     * @param node
     *            mail node
     * @param mail
     *            mail message
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    private void setMail(Node node, Mail mail) throws MessagingException, RepositoryException, IOException {
        setState(node, mail.getState());
        setLastUpdated(node, mail.getLastUpdated());
        setError(node, mail.getErrorMessage());
        setRemoteHost(node, mail.getRemoteHost());
        setRemoteAddr(node, mail.getRemoteAddr());
        setSender(node, mail.getSender());
        setRecipients(node, mail.getRecipients());
        setMessage(node, mail.getMessage());
        setAttributes(node, mail);
    }

    /**
     * Reads the message state from the <code>james:state</code> property.
     * 
     * @param node
     *            mail node
     * @return message state, or {@link Mail#DEFAULT} if not set
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private String getState(Node node) throws RepositoryException {
        try {
            return node.getProperty("james:state").getString();
        } catch (PathNotFoundException e) {
            return Mail.DEFAULT;
        }
    }

    /**
     * Writes the message state to the <code>james:state</code> property.
     * 
     * @param node
     *            mail node
     * @param state
     *            message state
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setState(Node node, String state) throws RepositoryException {
        node.setProperty("james:state", state);
    }

    /**
     * Reads the update timestamp from the
     * <code>jcr:content/jcr:lastModified</code> property.
     * 
     * @param node
     *            mail node
     * @return update timestamp
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private Date getLastUpdated(Node node) throws RepositoryException {
        try {
            node = node.getNode("jcr:content");
        } catch (PathNotFoundException e) {
            node = node.getProperty("jcr:content").getNode();
        }
        return node.getProperty("jcr:lastModified").getDate().getTime();
    }

    /**
     * Writes the update timestamp to the
     * <code>jcr:content/jcr:lastModified</code> property.
     * 
     * @param node
     *            mail node
     * @param updated
     *            update timestamp, or <code>null</code> if not set
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setLastUpdated(Node node, Date updated) throws RepositoryException {
        try {
            node = node.getNode("jcr:content");
        } catch (PathNotFoundException e) {
            node = node.getProperty("jcr:content").getNode();
        }
        Calendar calendar = Calendar.getInstance();
        if (updated != null) {
            calendar.setTime(updated);
        }
        node.setProperty("jcr:lastModified", calendar);
    }

    /**
     * Reads the error message from the <code>james:error</code> property.
     * 
     * @param node
     *            mail node
     * @return error message, or <code>null</code> if not set
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private String getError(Node node) throws RepositoryException {
        try {
            return node.getProperty("james:error").getString();
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Writes the error message to the <code>james:error</code> property.
     * 
     * @param node
     *            mail node
     * @param error
     *            error message
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setError(Node node, String error) throws RepositoryException {
        node.setProperty("james:error", error);
    }

    /**
     * Reads the remote host name from the <code>james:remotehost</code>
     * property.
     * 
     * @param node
     *            mail node
     * @return remote host name, or <code>null</code> if not set
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private String getRemoteHost(Node node) throws RepositoryException {
        try {
            return node.getProperty("james:remotehost").getString();
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Writes the remote host name to the <code>james:remotehost</code>
     * property.
     * 
     * @param node
     *            mail node
     * @param host
     *            remote host name
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setRemoteHost(Node node, String host) throws RepositoryException {
        node.setProperty("james:remotehost", host);
    }

    /**
     * Reads the remote address from the <code>james:remoteaddr</code> property.
     * 
     * @param node
     *            mail node
     * @return remote address, or <code>null</code> if not set
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private String getRemoteAddr(Node node) throws RepositoryException {
        try {
            return node.getProperty("james:remoteaddr").getString();
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Writes the remote address to the <code>james:remoteaddr</code> property.
     * 
     * @param node
     *            mail node
     * @param addr
     *            remote address
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setRemoteAddr(Node node, String addr) throws RepositoryException {
        node.setProperty("james:remoteaddr", addr);
    }

    /**
     * Reads the envelope sender from the <code>james:sender</code> property.
     * 
     * @param node
     *            mail node
     * @return envelope sender, or <code>null</code> if not set
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private MailAddress getSender(Node node) throws MessagingException, RepositoryException {
        try {
            String sender = node.getProperty("james:sender").getString();
            return new MailAddress(sender);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    /**
     * Writes the envelope sender to the <code>james:sender</code> property.
     * 
     * @param node
     *            mail node
     * @param sender
     *            envelope sender
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setSender(Node node, MailAddress sender) throws RepositoryException {
        node.setProperty("james:sender", sender.toString());
    }

    /**
     * Reads the list of recipients from the <code>james:recipients</code>
     * property.
     * 
     * @param node
     *            mail node
     * @return list of recipient, or an empty list if not set
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     */
    @SuppressWarnings("unchecked")
    private Collection<MailAddress> getRecipients(Node node) throws MessagingException, RepositoryException {
        try {
            Value[] values = node.getProperty("james:recipients").getValues();
            Collection<MailAddress> recipients = new ArrayList<>(values.length);
            for (Value value : values) {
                recipients.add(new MailAddress(value.getString()));
            }
            return recipients;
        } catch (PathNotFoundException e) {
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Writes the list of recipients to the <code>james:recipients</code>
     * property.
     * 
     * @param node
     *            mail node
     * @param recipients
     *            list of recipient
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     */
    private void setRecipients(Node node, Collection<MailAddress> recipients) throws RepositoryException {
        String[] values = new String[recipients.size()];
        Iterator<MailAddress> iterator = recipients.iterator();
        for (int i = 0; iterator.hasNext(); i++) {
            values[i] = iterator.next().toString();
        }
        node.setProperty("james:recipients", values);
    }

    /**
     * Reads the message content from the <code>jcr:content/jcr:data</code>
     * binary property.
     * 
     * @param node
     *            mail node
     * @return mail message
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    private MimeMessage getMessage(Node node) throws MessagingException, RepositoryException, IOException {
        try {
            node = node.getNode("jcr:content");
        } catch (PathNotFoundException e) {
            node = node.getProperty("jcr:content").getNode();
        }

        try (@SuppressWarnings("deprecation") InputStream stream = node.getProperty("jcr:data").getStream()) {
            Properties properties = System.getProperties();
            return new MimeMessage(javax.mail.Session.getDefaultInstance(properties), stream);
        }
    }

    /**
     * Writes the message content to the <code>jcr:content/jcr:data</code>
     * binary property.
     * 
     * @param node
     *            mail node
     * @param message
     *            mail message
     * @throws MessagingException
     *             if a messaging error occurs
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    @SuppressWarnings("deprecation")
    private void setMessage(Node node, final MimeMessage message) throws RepositoryException, IOException {
        try {
            node = node.getNode("jcr:content");
        } catch (PathNotFoundException e) {
            node = node.getProperty("jcr:content").getNode();
        }

        PipedInputStream input = new PipedInputStream();
        final PipedOutputStream output = new PipedOutputStream(input);
        new Thread(() -> {
            try {
                message.writeTo(output);
            } catch (Exception e) {
                LOGGER.info("Exception ignored", e);
            } finally {
                IOUtils.closeQuietly(output);
            }
        }).start();
        node.setProperty("jcr:data", input);
    }

    /**
     * Writes the mail attributes from the <code>jamesattr:*</code> property.
     * 
     * @param node
     *            mail node
     * @param mail
     *            mail message
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    private void getAttributes(Node node, Mail mail) throws RepositoryException, IOException {
        PropertyIterator iterator = node.getProperties("jamesattr:*");
        while (iterator.hasNext()) {
            Property property = iterator.nextProperty();
            String name = Text.unescapeIllegalJcrChars(property.getName().substring("jamesattr:".length()));
            if (property.getType() == PropertyType.BINARY) {
                try (@SuppressWarnings("deprecation") InputStream input = property.getStream()) {
                    ObjectInputStream stream = new ObjectInputStream(input);
                    mail.setAttribute(name, (Serializable) stream.readObject());
                } catch (ClassNotFoundException e) {
                    throw new IOException(e.getMessage());
                }
            } else {
                mail.setAttribute(name, property.getString());
            }
        }
    }

    /**
     * Writes the mail attributes to the <code>jamesattr:*</code> property.
     * 
     * @param node
     *            mail node
     * @param mail
     *            mail message
     * @throws RepositoryException
     *             if a repository error occurs
     * @throws IOException
     *             if an IO error occurs
     */
    @SuppressWarnings("deprecation")
    private void setAttributes(Node node, Mail mail) throws RepositoryException, IOException {
        Iterator<String> iterator = mail.getAttributeNames();
        while (iterator.hasNext()) {
            String name = iterator.next();
            Object value = mail.getAttribute(name);
            name = "jamesattr:" + Text.escapeIllegalJcrChars(name);
            if (value instanceof String || value == null) {
                node.setProperty(name, (String) value);
            } else {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(buffer);
                output.writeObject(value);
                output.close();
                node.setProperty(name, new ByteArrayInputStream(buffer.toByteArray()));
            }
        }
    }

    @Override
    protected void internalRemove(String key) throws MessagingException {
        try {
            Session session = login();
            try {
                String name = ISO9075.encode(Text.escapeIllegalJcrChars(key));
                QueryManager manager = session.getWorkspace().getQueryManager();
                @SuppressWarnings("deprecation")
                Query query = manager.createQuery("/jcr:root/" + MAIL_PATH + "//element(" + name + ",james:mail)", Query.XPATH);
                NodeIterator nodes = query.execute().getNodes();
                if (nodes.hasNext()) {
                    while (nodes.hasNext()) {
                        nodes.nextNode().remove();
                    }
                    session.save();
                    LOGGER.info("Mail {} removed from repository", key);
                } else {
                    LOGGER.warn("Mail {} not found", key);
                }
            } finally {
                session.logout();
            }
        } catch (RepositoryException e) {
            throw new MessagingException("Unable to remove message: " + key, e);
        }
    }

    @Override
    protected void internalStore(Mail mail) throws MessagingException, IOException {
        try {
            Session session = login();
            try {
                String name = Text.escapeIllegalJcrChars(mail.getName());
                final String xpath = "/jcr:root/" + MAIL_PATH + "//element(" + name + ",james:mail)";

                QueryManager manager = session.getWorkspace().getQueryManager();
                @SuppressWarnings("deprecation")
                Query query = manager.createQuery(xpath, Query.XPATH);
                NodeIterator iterator = query.execute().getNodes();

                if (iterator.hasNext()) {
                    while (iterator.hasNext()) {
                        setMail(iterator.nextNode(), mail);
                    }
                } else {
                    Node parent = session.getRootNode().getNode(MAIL_PATH);
                    Node node = parent.addNode(name, "james:mail");
                    Node resource = node.addNode("jcr:content", "nt:resource");
                    resource.setProperty("jcr:mimeType", "message/rfc822");
                    setMail(node, mail);
                }
                session.save();
                LOGGER.info("Mail {} stored in repository", mail.getName());
            } finally {
                session.logout();
            }
        } catch (IOException | RepositoryException e) {
            throw new MessagingException("Unable to store message: " + mail.getName(), e);
        }
    }

}
