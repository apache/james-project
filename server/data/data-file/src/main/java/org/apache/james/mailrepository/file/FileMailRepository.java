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

package org.apache.james.mailrepository.file;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.Initializable;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.repository.file.FilePersistentObjectRepository;
import org.apache.james.repository.file.FilePersistentStreamRepository;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

/**
 * Implementation of a MailRepository on a FileSystem.
 */
public class FileMailRepository implements MailRepository, Configurable, Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileMailRepository.class);

    private FilePersistentStreamRepository streamRepository;
    private FilePersistentObjectRepository objectRepository;
    private String destination;
    private Set<String> keys;
    private final Object lock = new Object();
    private boolean fifo;
    private boolean cacheKeys; // experimental: for use with write mostly
    // repositories such as spam and error
    private FileSystem fileSystem;

    /**
     * A lock used to control access to repository elements, locking access
     * based on the key
     */
    private final Lock accessControlLock = new Lock();

    /**
     * Releases a lock on a message identified the key
     *
     * @param key
     *            the key of the message to be unlocked
     *
     * @return true if successfully released the lock, false otherwise
     */
    private boolean unlock(MailKey key) {
        return accessControlLock.unlock(key);
    }

    /**
     * Obtains a lock on a message identified by key
     *
     * @param key
     *            the key of the message to be locked
     *
     * @return true if successfully obtained the lock, false otherwise
     */
    private boolean lock(MailKey key) {
        return accessControlLock.lock(key);
    }


    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        destination = config.getString("[@destinationURL]");
        LOGGER.debug("FileMailRepository.destinationURL: {}", destination);
        fifo = config.getBoolean("[@FIFO]", false);
        cacheKeys = config.getBoolean("[@CACHEKEYS]", true);
        // ignore model
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        try {
            BaseHierarchicalConfiguration reposConfiguration = new BaseHierarchicalConfiguration();

            reposConfiguration.addProperty("[@destinationURL]", destination);
            objectRepository = new FilePersistentObjectRepository();
            objectRepository.setFileSystem(fileSystem);
            objectRepository.configure(reposConfiguration);
            objectRepository.init();

            streamRepository = new FilePersistentStreamRepository();
            streamRepository.setFileSystem(fileSystem);
            streamRepository.configure(reposConfiguration);
            streamRepository.init();

            if (cacheKeys) {
                keys = Collections.synchronizedSet(new HashSet<>());
            }

            // Finds non-matching pairs and deletes the extra files

            HashSet<String> streamKeys = streamRepository.list().collect(Collectors.toCollection(HashSet::new));
            HashSet<String> objectKeys = objectRepository.list().collect(Collectors.toCollection(HashSet::new));

            @SuppressWarnings("unchecked")
            Collection<String> strandedStreams = (Collection<String>) streamKeys.clone();
            strandedStreams.removeAll(objectKeys);
            for (Object strandedStream : strandedStreams) {
                MailKey key = new MailKey((String) strandedStream);
                remove(key);
            }

            @SuppressWarnings("unchecked")
            Collection<String> strandedObjects = (Collection<String>) objectKeys.clone();
            strandedObjects.removeAll(streamKeys);
            for (Object strandedObject : strandedObjects) {
                MailKey key = new MailKey((String) strandedObject);
                remove(key);
            }

            if (keys != null) {
                // Next get a list from the object repository
                // and use that for the list of keys
                keys.clear();
                objectRepository.list().forEach(keys::add);
            }
            LOGGER.debug("{} created in {}", getClass().getName(), destination);
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve Store component", e);
            throw e;
        }
    }

    @Override
    public MailKey store(Mail mc) throws MessagingException {
        boolean wasLocked = true;
        MailKey key = MailKey.forMail(mc);
        try {
            synchronized (this) {
                wasLocked = accessControlLock.isLocked(key);
                if (!wasLocked) {
                    // If it wasn't locked, we want a lock during the store
                    lock(key);
                }
            }
            internalStore(mc);

            AuditTrail.entry()
                .protocol("mailrepository")
                .action("store")
                .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mc.getName(),
                    "mimeMessageId", Optional.ofNullable(mc.getMessage())
                        .map(Throwing.function(MimeMessage::getMessageID))
                        .orElse(""),
                    "sender", mc.getMaybeSender().asString(),
                    "recipients", StringUtils.join(mc.getRecipients()))))
                .log("FileMailRepository stored mail.");

            return key;
        } catch (MessagingException e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Exception caught while storing mail {}", key, e);
            throw new MessagingException("Exception caught while storing mail " + key, e);
        } finally {
            if (!wasLocked) {
                // If it wasn't locked, we need to unlock now
                unlock(key);
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }

    private void internalStore(Mail mc) throws MessagingException, IOException {
        String key = mc.getName();
        if (keys != null) {
            keys.add(key);
        }
        boolean saveStream = true;

        MimeMessage message = mc.getMessage();

        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper) message;
            LOGGER.trace("Retrieving from: {}", wrapper.getSourceId());
            LOGGER.trace("Saving to:       {}/{}", destination, mc.getName());
            LOGGER.trace("Modified: {}", wrapper.isModified());
            String destinationBuffer = destination + "/" + mc.getName();
            if (destinationBuffer.equals(wrapper.getSourceId())) {
                if (!wrapper.isModified()) {
                    // We're trying to save to the same place, and it's not
                    // modified... we shouldn't save.
                    // More importantly, if we try to save, we will create a
                    // 0-byte file since we're
                    // retrying to retrieve from a file we'll be overwriting.
                    saveStream = false;
                }
            }
        }
        if (saveStream) {
            OutputStream out = null;
            try {
                if (message instanceof MimeMessageWrapper) {
                    // we need to force the loading of the message from the
                    // stream as we want to override the old message
                    ((MimeMessageWrapper) message).loadMessage();
                    out = streamRepository.put(key);

                    ((MimeMessageWrapper) message).writeTo(out, out, null, true);

                } else {
                    out = streamRepository.put(key);
                    mc.getMessage().writeTo(out);

                }

            } finally {
                if (out != null) {
                    out.close();
                }
            }
        }
        // Always save the header information
        objectRepository.put(key, mc);
    }

    @Override
    public Mail retrieve(MailKey key) throws MessagingException {
        try {
            Mail mc;
            try {
                mc = (Mail) objectRepository.get(key.asString());
            } catch (RuntimeException re) {
                if (re.getCause() instanceof Error) {
                    LOGGER.warn("Error when retrieving mail, not deleting: {}", re, re);
                } else {
                    LOGGER.warn("Exception retrieving mail: {}, so we're deleting it.", re, re);
                    remove(key);
                }
                return null;
            }
            MimeMessageStreamRepositorySource source = new MimeMessageStreamRepositorySource(streamRepository, destination, key.asString());
            mc.setMessage(new MimeMessageWrapper(source));

            return mc;
        } catch (Exception me) {
            LOGGER.error("Exception retrieving mail", me);
            throw new MessagingException("Exception while retrieving mail: " + me.getMessage(), me);
        }
    }


    @Override
    public void remove(MailKey key) throws MessagingException {
        if (lock(key)) {
            try {
                internalRemove(key);
            } finally {
                unlock(key);
            }
        } else {
            throw new MessagingException("Cannot lock " + key + " to remove it");
        }
    }

    @Override
    public long size() {
        return Iterators.size(list());
    }

    @Override
    public void removeAll() {
        listStream()
            .forEach(Throwing.<MailKey>consumer(this::remove).sneakyThrow());
    }

    private void internalRemove(MailKey key) {
        if (keys != null) {
            keys.remove(key.asString());
        }
        streamRepository.remove(key.asString());
        objectRepository.remove(key.asString());
    }

    @Override
    public Iterator<MailKey> list() {
        return listStream()
            .iterator();
    }

    private Stream<MailKey> listStream() {
        // Fix ConcurrentModificationException by cloning
        // the keyset before getting an iterator
        final ArrayList<String> clone;
        if (keys != null) {
            synchronized (lock) {
                clone = new ArrayList<>(keys);
            }
        } else {
            clone = objectRepository.list().collect(Collectors.toCollection(ArrayList::new));
        }
        if (fifo) {
            Collections.sort(clone); // Keys is a HashSet; impose FIFO for apps
        }
        // that need it
        return clone
            .stream()
            .map(MailKey::new);
    }
}
