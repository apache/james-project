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
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
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
import javax.mail.internet.MimeMessage;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import org.apache.james.util.AuditTrail;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Implementation of a MailRepository on a database via JPA.
 */
public class JPAMailRepository implements MailRepository, Configurable, Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JPAMailRepository.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String repositoryName;

    private final EntityManagerFactory entityManagerFactory;

    @Inject
    public JPAMailRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public JPAMailRepository(EntityManagerFactory entityManagerFactory, MailRepositoryUrl url) throws ConfigurationException {
        this.entityManagerFactory = entityManagerFactory;
        this.repositoryName = url.getPath().asString();
        if (repositoryName.isEmpty()) {
            throw new ConfigurationException(
                    "Malformed destinationURL - Must be of the format 'jpa://<repositoryName>'.  Was passed " + url);
        }
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

            AuditTrail.entry()
                .protocol("mailrepository")
                .action("store")
                .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                    "mimeMessageId", Optional.ofNullable(mail.getMessage())
                        .map(Throwing.function(MimeMessage::getMessageID))
                        .orElse(""),
                    "sender", mail.getMaybeSender().asString(),
                    "recipients", StringUtils.join(mail.getRecipients()))))
                .log("JPAMailRepository stored mail.");

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

    private String serializeAttributes(Stream<Attribute> attributes) {
        Map<String, JsonNode> map = attributes
            .flatMap(entry -> entry.getValue().toJson().map(value -> Pair.of(entry.getName().asString(), value)).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));

        return new ObjectNode(JsonNodeFactory.instance, map).toString();
    }

    private List<Attribute> deserializeAttributes(String data) {
        try {
            JsonNode jsonNode = OBJECT_MAPPER.readTree(data);
            if (jsonNode instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) jsonNode;

                return Iterators.toStream(objectNode.fields())
                    .map(entry -> new Attribute(AttributeName.of(entry.getKey()), AttributeValue.fromJson(entry.getValue())))
                    .collect(ImmutableList.toImmutableList());
            }
            throw new IllegalArgumentException("JSON object corresponding to mail attibutes must be a JSON object");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Mail attributes is not a valid JSON object", e);
        }
    }

    private String serializePerRecipientHeaders(PerRecipientHeaders perRecipientHeaders) {
        if (perRecipientHeaders == null) {
            return null;
        }
        Map<MailAddress, Collection<Header>> map = perRecipientHeaders.getHeadersByRecipient().asMap();
        if (map.isEmpty()) {
            return null;
        }
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<MailAddress, Collection<Header>> entry : map.entrySet()) {
            String recipient = entry.getKey().asString();
            ObjectNode headers = node.putObject(recipient);
            entry.getValue().forEach(header -> headers.put(header.getName(), header.getValue()));
        }
        return node.toString();
    }

    private PerRecipientHeaders deserializePerRecipientHeaders(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        PerRecipientHeaders perRecipientHeaders = new PerRecipientHeaders();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(data);
            if (node instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) node;
                Iterators.toStream(objectNode.fields()).forEach(
                    entry -> addPerRecipientHeaders(perRecipientHeaders, entry.getKey(), entry.getValue()));
                return perRecipientHeaders;
            }
            throw new IllegalArgumentException("JSON object corresponding to recipient headers must be a JSON object");
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("per recipient headers is not a valid JSON object", e);
        }
    }

    private void addPerRecipientHeaders(PerRecipientHeaders perRecipientHeaders, String recipient, JsonNode headers) {
        try {
            MailAddress address = new MailAddress(recipient);
            Iterators.toStream(headers.fields()).forEach(
                entry -> {
                    String name = entry.getKey();
                    String value = entry.getValue().textValue();
                    Header header = Header.builder().name(name).value(value).build();
                    perRecipientHeaders.addHeaderForRecipient(header, address);
                });
        } catch (AddressException ae) {
            throw new IllegalArgumentException("invalid recipient address", ae);
        }
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
    public void removeAll() throws MessagingException {
        EntityManager entityManager = entityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        try {
            entityManager.createNamedQuery("deleteAllMailMessages")
                .setParameter("repositoryName", repositoryName)
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
