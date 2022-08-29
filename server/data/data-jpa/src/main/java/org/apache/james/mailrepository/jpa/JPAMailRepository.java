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

package org.apache.james.mailrepository.jpa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.james.backends.jpa.EntityManagerUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.Initializable;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.jpa.model.JPAMail;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Implementation of a MailRepository on a database via JPA.
 */
public class JPAMailRepository implements MailRepository, Configurable, Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPAMailRepository.class);

    private String repositoryName;

    private final EntityManagerFactory entityManagerFactory;

    @Inject
    public JPAMailRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    // note: caller must close the returned EntityManager when done using it
    protected EntityManager entityManager() {
        return entityManagerFactory.createEntityManager();
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        LOGGER.debug("{}.configure()", getClass().getName());
        String destination = configuration.getString("[@destinationURL]");
        MailRepositoryUrl url = MailRepositoryUrl.from(destination); // also validates url and standardizes slashes
        repositoryName = url.getPath().asString();
        if (repositoryName.isEmpty()) {
            throw new ConfigurationException(
                "Malformed destinationURL - Must be of the format 'jpa://<repositoryName>'.  Was passed " + url);
        }
        LOGGER.debug("Parsed URL: repositoryName = '{}'", repositoryName);
    }

    /**
     * Initialises the JPA repository.
     *
     * @throws Exception if an error occurs
     */
    @Override
    @PostConstruct
    public void init() throws Exception {
        LOGGER.debug("{}.initialize()", getClass().getName());
        list();
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey key = MailKey.forMail(mail);
        EntityManager entityManager = entityManager();
        try {
            JPAMail jpaMail = new JPAMail();
            jpaMail.setRepositoryName(repositoryName);
            jpaMail.setMessageName(mail.getName());
            jpaMail.setMessageState(mail.getState());
            jpaMail.setErrorMessage(mail.getErrorMessage());
            if (!mail.getMaybeSender().isNullSender()) {
                jpaMail.setSender(mail.getMaybeSender().get().toString());
            }
            String recipients = mail.getRecipients().stream()
                .map(MailAddress::toString)
                .collect(Collectors.joining("\r\n"));
            jpaMail.setRecipients(recipients);
            jpaMail.setRemoteHost(mail.getRemoteHost());
            jpaMail.setRemoteAddr(mail.getRemoteAddr());
            jpaMail.setPerRecipientHeaders(serializePerRecipientHeaders(mail.getPerRecipientSpecificHeaders()));
            jpaMail.setLastUpdated(new Timestamp(mail.getLastUpdated().getTime()));
            jpaMail.setMessageBody(getBody(mail));
            jpaMail.setMessageAttributes(serializeAttributes(mail.attributes()));
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            jpaMail = entityManager.merge(jpaMail);
            transaction.commit();
            return key;
        } catch (MessagingException e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw new MessagingException("Exception caught while storing mail " + key, e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    private byte[] getBody(Mail mail) throws MessagingException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int)mail.getMessageSize());
        if (mail instanceof MimeMessageWrapper) {
            // we need to force the loading of the message from the
            // stream as we want to override the old message
            ((MimeMessageWrapper) mail).loadMessage();
            ((MimeMessageWrapper) mail).writeTo(out, out, null, true);
        } else {
            mail.getMessage().writeTo(out);
        }
        return out.toByteArray();
    }

    private byte[] serializeAttributes(Stream<Attribute> attributes) {
        Map<String, Object> map = attributes.collect(Collectors.toMap(
            attribute -> attribute.getName().asString(),
            attribute -> attribute.getValue().value()));
        return SerializationUtils.serialize((Serializable)map);
    }

    private List<Attribute> deserializeAttributes(byte[] data) {
        HashMap<String, Object> attributes = SerializationUtils.deserialize(data);
        return Optional.ofNullable(attributes)
            .orElse(new HashMap<>())
            .entrySet()
            .stream()
            .map(entry -> Attribute.convertToAttribute(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    private String serializePerRecipientHeaders(PerRecipientHeaders perRecipientHeaders) {
        if (perRecipientHeaders == null) {
            return null;
        }
        Map<MailAddress, Collection<PerRecipientHeaders.Header>> map = perRecipientHeaders.getHeadersByRecipient().asMap();
        if (map.isEmpty()) {
            return null;
        }
        StringBuilder data = new StringBuilder(map.size() * 1024);
        for (Map.Entry<MailAddress, Collection<PerRecipientHeaders.Header>> entry : map.entrySet()) {
            data.append(entry.getKey().asString()).append('\n');
            entry.getValue().forEach(header -> data.append(header.asString()).append('\n'));
            data.append('\n');
        }
        return data.toString();
    }

    private PerRecipientHeaders deserializePerRecipientHeaders(String data) throws AddressException {
        if (data == null || data.isEmpty()) {
            return null;
        }
        PerRecipientHeaders perRecipientHeaders = new PerRecipientHeaders();
        for (String entry : data.split("\n\n")) {
            String[] lines = entry.split("\n");
            MailAddress address = new MailAddress(lines[0]);
            for (int i = 1; i < lines.length; i++) {
                perRecipientHeaders.addHeaderForRecipient(PerRecipientHeaders.Header.fromString(lines[i]), address);
            }
        }
        return perRecipientHeaders;
    }

    @Override
    public Mail retrieve(MailKey key) throws MessagingException {
        EntityManager entityManager = entityManager();
        try {
            JPAMail jpaMail = entityManager.createNamedQuery("findMailMessage", JPAMail.class)
                .setParameter("repositoryName", repositoryName)
                .setParameter("messageName", key.asString())
                .getSingleResult();

            MailImpl.Builder mail = MailImpl.builder().name(key.asString());
            if (jpaMail.getMessageAttributes() != null) {
                mail.addAttributes(deserializeAttributes(jpaMail.getMessageAttributes()));
            }
            mail.state(jpaMail.getMessageState());
            mail.errorMessage(jpaMail.getErrorMessage());
            String sender = jpaMail.getSender();
            if (sender == null) {
                mail.sender((MailAddress)null);
            } else {
                mail.sender(new MailAddress(sender));
            }
            StringTokenizer st = new StringTokenizer(jpaMail.getRecipients(), "\r\n", false);
            while (st.hasMoreTokens()) {
                mail.addRecipient(st.nextToken());
            }
            mail.remoteHost(jpaMail.getRemoteHost());
            mail.remoteAddr(jpaMail.getRemoteAddr());
            PerRecipientHeaders perRecipientHeaders = deserializePerRecipientHeaders(jpaMail.getPerRecipientHeaders());
            if (perRecipientHeaders != null) {
                mail.addAllHeadersForRecipients(perRecipientHeaders);
            }
            mail.lastUpdated(jpaMail.getLastUpdated());

            MimeMessageJPASource source = new MimeMessageJPASource(this, key.asString(), jpaMail.getMessageBody());
            MimeMessageWrapper message = new MimeMessageWrapper(source);
            mail.mimeMessage(message);
            return mail.build();
        } catch (NoResultException nre) {
            LOGGER.debug("Did not find mail {} in repository {}", key, repositoryName);
            return null;
        } catch (Exception e) {
            throw new MessagingException("Exception while retrieving mail: " + e.getMessage(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public long size() throws MessagingException {
        EntityManager entityManager = entityManager();
        try {
            return entityManager.createNamedQuery("countMailMessages", long.class)
                .setParameter("repositoryName", repositoryName)
                .getSingleResult();
        } catch (Exception me) {
            throw new MessagingException("Exception while listing messages: " + me.getMessage(), me);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public Iterator<MailKey> list() throws MessagingException {
        EntityManager entityManager = entityManager();
        try {
            return entityManager.createNamedQuery("listMailMessages", String.class)
                .setParameter("repositoryName", repositoryName)
                .getResultStream()
                .map(MailKey::new)
                .iterator();
        } catch (Exception me) {
            throw new MessagingException("Exception while listing messages: " + me.getMessage(), me);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public void remove(MailKey key) throws MessagingException {
        remove(Collections.singleton(key));
    }

    @Override
    public void removeAll() throws MessagingException {
        remove(ImmutableList.copyOf(list()));
    }

    @Override
    public void remove(Collection<MailKey> keys) throws MessagingException {
        Collection<String> messageNames = keys.stream().map(MailKey::asString).collect(Collectors.toList());
        EntityManager entityManager = entityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        try {
            entityManager.createNamedQuery("deleteMailMessages")
                .setParameter("repositoryName", repositoryName)
                .setParameter("messageNames", messageNames)
                .executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            throw new MessagingException("Exception while removing message(s): " + e.getMessage(), e);
        } finally {
            EntityManagerUtils.safelyClose(entityManager);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JPAMailRepository
            && Objects.equals(repositoryName, ((JPAMailRepository)obj).repositoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryName);
    }
}
