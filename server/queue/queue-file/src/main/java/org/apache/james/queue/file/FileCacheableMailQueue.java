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
package org.apache.james.queue.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.mail.MessagingException;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link ManageableMailQueue} implementation which use the fs to store {@link Mail}'s
 * <p/>
 * On create of the {@link FileCacheableMailQueue} the {@link #init()} will get called. This takes care of
 * loading the needed meta-data into memory for fast access.
 *
 * @deprecated FileMailQueue implementation is unmaintained, incomplete and not thread safe
 * We recommend using embedded ActiveMQMailQueue implementation instead
 */
@Deprecated
public class FileCacheableMailQueue implements ManageableMailQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileCacheableMailQueue.class);

    private final Map<String, FileItem> keyMappings = Collections.synchronizedMap(new LinkedHashMap<>());
    private final BlockingQueue<String> inmemoryQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(NamedThreadFactory.withClassName(getClass()));
    private static final AtomicLong COUNTER = new AtomicLong();
    private final String queueDirName;
    private final File queueDir;

    private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    private final boolean sync;
    private static final String MSG_EXTENSION = ".msg";
    private static final String OBJECT_EXTENSION = ".obj";
    private static final AttributeName NEXT_DELIVERY = AttributeName.of("FileQueueNextDelivery");
    private static final int SPLITCOUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String queueName;
    private final Flux<MailQueueItem> flux;

    public FileCacheableMailQueue(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, File parentDir, String queuename, boolean sync) throws IOException {
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.sync = sync;
        this.queueName = queuename;
        this.queueDir = new File(parentDir, queueName);
        this.queueDirName = queueDir.getAbsolutePath();
        init();
        this.flux = Mono.defer(this::deQueueOneItem)
            .repeat()
            .limitRate(1);
    }

    @Override
    public void close() {
        //There's no resource to free
    }

    @Override
    public String getName() {
        return queueName;
    }

    private void init() throws IOException {

        for (int i = 1; i <= SPLITCOUNT; i++) {

            File qDir = new File(queueDir, Integer.toString(i));
            FileUtils.forceMkdir(qDir);

            String[] files = qDir.list((dir, name) -> name.endsWith(OBJECT_EXTENSION));

            for (String name : files) {

                ObjectInputStream oin = null;

                try {

                    final String msgFileName = name.substring(0, name.length() - OBJECT_EXTENSION.length()) + MSG_EXTENSION;

                    FileItem item = new FileItem(qDir.getAbsolutePath() + File.separator + name, qDir.getAbsolutePath() + File.separator + msgFileName);

                    oin = new ObjectInputStream(new FileInputStream(item.getObjectFile()));
                    Mail mail = (Mail) oin.readObject();
                    Optional<ZonedDateTime> next = getNextDelivery(mail);

                    final String key = mail.getName();
                    keyMappings.put(key, item);
                    if (!next.isPresent() || next.get().isBefore(ZonedDateTime.now())) {

                        try {
                            inmemoryQueue.put(key);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Unable to init", e);
                        }
                    } else {

                        // Schedule a task which will put the mail in the queue
                        // for processing after a given delay
                        long nextDeliveryDelay = ZonedDateTime.now().until(next.get(), ChronoUnit.MILLIS);
                        scheduler.schedule(() -> {
                            try {
                                inmemoryQueue.put(key);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Unable to init", e);
                            }
                        }, nextDeliveryDelay, TimeUnit.MILLISECONDS);
                    }
                } catch (ClassNotFoundException | IOException e) {
                    LOGGER.error("Unable to load Mail", e);
                } finally {
                    if (oin != null) {
                        try {
                            oin.close();
                        } catch (Exception e) {
                            // ignore on close
                        }
                    }
                }

            }
        }
    }

    private Optional<ZonedDateTime> getNextDelivery(Mail mail) {
        return AttributeUtils
            .getValueAndCastFromMail(mail, NEXT_DELIVERY, Long.class)
            .map(next -> Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()));
    }

    @Override
    public void enQueue(Mail mail, Duration delay) throws MailQueueException {
        final String key = mail.getName() + "-" + COUNTER.incrementAndGet();
        try {
            int i = RANDOM.nextInt(SPLITCOUNT) + 1;

            String name = queueDirName + "/" + i + "/" + key;

            final FileItem item = new FileItem(name + OBJECT_EXTENSION, name + MSG_EXTENSION);
            if (!delay.isNegative()) {
                mail.setAttribute(new Attribute(NEXT_DELIVERY, AttributeValue.of(computeNextDelivery(delay))));
            }
            try (FileOutputStream foout = new FileOutputStream(item.getObjectFile());
                ObjectOutputStream oout = new ObjectOutputStream(foout)) {
                oout.writeObject(mail);
                oout.flush();
                if (sync) {
                    foout.getFD().sync();
                }
            }
            try (FileOutputStream out = new FileOutputStream(item.getMessageFile())) {
                mail.getMessage().writeTo(out);
                out.flush();
                if (sync) {
                    out.getFD().sync();
                }
            }

            keyMappings.put(key, item);

            if (!delay.isNegative()) {
                // The message should get delayed so schedule it for later
                scheduler.schedule(() -> {
                    try {
                        inmemoryQueue.put(key);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Unable to init", e);
                    }
                }, delay.getSeconds(), TimeUnit.SECONDS);

            } else {
                inmemoryQueue.put(key);
            }

            //TODO: Think about exception handling in detail
        } catch (IOException | MessagingException | InterruptedException e) {
            throw new MailQueueException("Unable to enqueue mail", e);
        }

    }

    private long computeNextDelivery(Duration delay) {
        try {
            return Instant.now().plus(delay).getEpochSecond();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {
        enQueue(mail, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public Flux<MailQueueItem> deQueue() {
        return flux;
    }

    private Mono<MailQueueItem> deQueueOneItem() {
        try {
            FileItem item = null;
            String k = null;
            while (item == null) {
                k = inmemoryQueue.take();

                item = keyMappings.get(k);

            }
            final String key = k;
            final FileItem fitem = item;
            try {
                final File objectFile = new File(fitem.getObjectFile());
                final File msgFile = new File(fitem.getMessageFile());
                try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(objectFile))) {
                    final Mail mail = (Mail) oin.readObject();
                    mail.setMessage(new MimeMessageCopyOnWriteProxy(new FileMimeMessageSource(msgFile)));
                    MailQueueItem fileMailQueueItem = new MailQueueItem() {

                        @Override
                        public Mail getMail() {
                            return mail;
                        }

                        @Override
                        public void done(boolean success) throws MailQueueException {
                            if (!success) {
                                try {
                                    inmemoryQueue.put(key);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new MailQueueException("Unable to rollback", e);
                                }
                            } else {
                                fitem.delete();
                                keyMappings.remove(key);
                            }

                            LifecycleUtil.dispose(mail);
                        }
                    };
                    return Mono.just(mailQueueItemDecoratorFactory.decorate(fileMailQueueItem));
                }
                // TODO: Think about exception handling in detail
            } catch (IOException | ClassNotFoundException | MessagingException e) {
                return Mono.error(new MailQueueException("Unable to dequeue", e));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Mono.error(new MailQueueException("Unable to dequeue", e));
        }
    }

    private static final class FileMimeMessageSource extends MimeMessageSource implements Disposable {

        private File file;
        private final SharedFileInputStream in;

        public FileMimeMessageSource(File file) throws IOException {
            this.file = file;
            this.in = new SharedFileInputStream(file);
        }

        @Override
        public String getSourceId() {
            return file.getAbsolutePath();
        }

        /**
         * Get an input stream to retrieve the data stored in the temporary file
         *
         * @return a <code>BufferedInputStream</code> containing the data
         */
        @Override
        public InputStream getInputStream() throws IOException {
            return in.newStream(0, -1);
        }

        @Override
        public long getMessageSize() throws IOException {
            return file.length();
        }

        @Override
        public void dispose() {
            try {
                in.close();
            } catch (IOException e) {
                //ignore exception during close
            }
            file = null;
        }

    }

    /**
     * Helper class which is used to reference the path to the object and msg file
     */
    private static final class FileItem {
        private final String objectfile;
        private final String messagefile;

        public FileItem(String objectfile, String messagefile) {
            this.objectfile = objectfile;
            this.messagefile = messagefile;
        }

        public String getObjectFile() {
            return objectfile;
        }

        public String getMessageFile() {
            return messagefile;
        }

        public void delete() throws MailQueueException {
            try {
                FileUtils.forceDelete(new File(getObjectFile()));
            } catch (IOException e) {
                throw new MailQueueException("Unable to delete mail");
            }

            try {
                FileUtils.forceDelete(new File(getMessageFile()));
            } catch (IOException e) {
                LOGGER.debug("Remove of msg file for mail failed");
            }
        }
    }

    @Override
    public long getSize() throws MailQueueException {
        return keyMappings.size();
    }

    @Override
    public long flush() throws MailQueueException {
        Iterator<String> keys = keyMappings.keySet().iterator();
        long i = 0;
        while (keys.hasNext()) {
            String key = keys.next();
            if (!inmemoryQueue.contains(key)) {
                inmemoryQueue.add(key);
                i++;
            }
        }
        return i;
    }

    @Override
    public long clear() throws MailQueueException {
        long count = getSize();

        keyMappings.values().forEach(Throwing.consumer(FileItem::delete));
        keyMappings.clear();
        inmemoryQueue.clear();

        return count;
    }

    /**
     * TODO: implement me
     */
    @Override
    public long remove(Type type, String value) throws MailQueueException {
        switch (type) {
            case Name:
                FileItem item = keyMappings.remove(value);
                if (item != null) {
                    item.delete();
                    return 1;
                } else {
                    return 0;
                }

            default:
                break;
        }
        throw new MailQueueException("Not supported yet");

    }

    @Override
    public MailQueueIterator browse() throws MailQueueException {
        final Iterator<FileItem> items = keyMappings.values().iterator();

        return new MailQueueIterator() {
            private MailQueueItemView item;

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Read-only");
            }

            @Override
            public MailQueueItemView next() {
                if (hasNext()) {
                    MailQueueItemView itemView = item;
                    item = null;
                    return itemView;
                }

                throw new NoSuchElementException();
            }

            @Override
            public boolean hasNext() {
                if (item != null) {
                    return true;
                }

                while (items.hasNext()) {
                    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(items.next().getObjectFile()))) {
                        final Mail mail = (Mail) in.readObject();
                        item = new MailQueueItemView(mail, getNextDelivery(mail));
                        return true;
                    } catch (IOException | ClassNotFoundException e) {
                        LOGGER.info("Unable to load mail", e);
                    }
                }

                return false;
            }

            @Override
            public void close() {
                // do nothing
            }
        };
    }

}
