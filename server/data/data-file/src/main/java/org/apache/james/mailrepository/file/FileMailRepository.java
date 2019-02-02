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
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.lib.AbstractMailRepository;
import org.apache.james.repository.file.FilePersistentObjectRepository;
import org.apache.james.repository.file.FilePersistentStreamRepository;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Implementation of a MailRepository on a FileSystem.
 * </p>
 * <p>
 * Requires a configuration element in the .conf.xml file of the form:
 * <p/>
 * <pre>
 *  &lt;repository destinationURL="file://path-to-root-dir-for-repository"
 *              type="MAIL"
 *              model="SYNCHRONOUS"/&gt;
 * </pre>
 * <p/>
 * Requires a logger called MailRepository.
 * </p>
 */
public class FileMailRepository extends AbstractMailRepository {
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

    @Inject
    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    protected void doConfigure(HierarchicalConfiguration config) throws org.apache.commons.configuration.ConfigurationException {
        super.doConfigure(config);
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
            DefaultConfigurationBuilder reposConfiguration = new DefaultConfigurationBuilder();

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
                keys = Collections.synchronizedSet(new HashSet<String>());
            }

            // Finds non-matching pairs and deletes the extra files
            HashSet<String> streamKeys = new HashSet<>();
            for (Iterator<String> i = streamRepository.list(); i.hasNext(); ) {
                streamKeys.add(i.next());
            }
            HashSet<String> objectKeys = new HashSet<>();
            for (Iterator<String> i = objectRepository.list(); i.hasNext(); ) {
                objectKeys.add(i.next());
            }

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
                for (Iterator<String> i = objectRepository.list(); i.hasNext(); ) {
                    keys.add(i.next());
                }
            }
            LOGGER.debug("{} created in {}", getClass().getName(), destination);
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve Store component", e);
            throw e;
        }
    }

    @Override
    protected void internalStore(Mail mc) throws MessagingException, IOException {
        String key = mc.getName();
        if (keys != null && !keys.contains(key)) {
            keys.add(key);
        }
        boolean saveStream = true;
        boolean update = true;

        MimeMessage message = mc.getMessage();
        // if the message is a Copy on Write proxy we check the wrapped message
        // to optimize the behaviour in case of MimeMessageWrapper
        if (message instanceof MimeMessageCopyOnWriteProxy) {
            MimeMessageCopyOnWriteProxy messageCow = (MimeMessageCopyOnWriteProxy) message;
            message = messageCow.getWrappedMessage();
        }
        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper) message;
            if (DEEP_DEBUG) {
                System.out.println("Retrieving from: " + wrapper.getSourceId());
                String debugBuffer = "Saving to:       " + destination + "/" + mc.getName();
                System.out.println(debugBuffer);
                System.out.println("Modified: " + wrapper.isModified());
            }
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

                // its an update
                update = true;
            }
        }
        if (saveStream) {
            OutputStream out = null;
            try {
                if (update && message instanceof MimeMessageWrapper) {
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
            mc.setMessage(new MimeMessageCopyOnWriteProxy(source));

            return mc;
        } catch (Exception me) {
            LOGGER.error("Exception retrieving mail", me);
            throw new MessagingException("Exception while retrieving mail: " + me.getMessage(), me);
        }
    }

    @Override
    protected void internalRemove(MailKey key) throws MessagingException {
        if (keys != null) {
            keys.remove(key.asString());
        }
        streamRepository.remove(key.asString());
        objectRepository.remove(key.asString());
    }

    @Override
    public Iterator<MailKey> list() {
        // Fix ConcurrentModificationException by cloning
        // the keyset before getting an iterator
        final ArrayList<String> clone;
        if (keys != null) {
            synchronized (lock) {
                clone = new ArrayList<>(keys);
            }
        } else {
            clone = new ArrayList<>();
            for (Iterator<String> i = objectRepository.list(); i.hasNext(); ) {
                clone.add(i.next());
            }
        }
        if (fifo) {
            Collections.sort(clone); // Keys is a HashSet; impose FIFO for apps
        }
        // that need it
        return clone.stream()
            .map(MailKey::new)
            .iterator();
    }
}
