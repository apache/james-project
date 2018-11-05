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
import java.util.stream.Stream;

import javax.mail.MessagingException;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * {@link ManageableMailQueue} implementation which use the fs to store {@link Mail}'s
 * <p/>
 * On create of the {@link FileMailQueue} the {@link #init()} will get called. This takes care of loading the needed
 * meta-data into memory for fast access.
 */
public class FileMailQueue implements ManageableMailQueue {
    private static final String FILE_PATH_KEY = "key";
    private static final SetBasedFieldSelector FIELDS_TO_LOAD = new SetBasedFieldSelector(Collections.singleton(FILE_PATH_KEY), Collections.emptySet());
    private static final Logger LOGGER = LoggerFactory.getLogger(FileMailQueue.class);
    private static final AtomicLong COUNTER = new AtomicLong();
    private static final String MSG_EXTENSION = ".msg";
    private static final String OBJECT_EXTENSION = ".obj";
    private static final String NEXT_DELIVERY = "FileQueueNextDelivery";
    private static final int SPLITCOUNT = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, FileItem> keyMappings = Collections.synchronizedMap(new LinkedHashMap<>());
    private final BlockingQueue<String> inmemoryQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String queueDirName;
    private final File queueDir;
    private final MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory;
    private final boolean sync;
    private final String queueName;
    private final IndexWriter indexWriter;

    public FileMailQueue(MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory, File parentDir, String queuename, boolean sync) throws IOException {
        this.mailQueueItemDecoratorFactory = mailQueueItemDecoratorFactory;
        this.sync = sync;
        this.queueName = queuename;
        this.queueDir = new File(parentDir, queueName);
        this.queueDirName = queueDir.getAbsolutePath();
        StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_36);
        this.indexWriter = new IndexWriter(FSDirectory.open(new File(queueDir, "index")),
                new IndexWriterConfig(Version.LUCENE_36, analyzer));

        init();
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
                String msgFileName = name.substring(0, name.length() - OBJECT_EXTENSION.length()) + MSG_EXTENSION;
                FileItem item = new FileItem(qDir.toPath().resolve(name).toFile(), qDir.toPath().resolve(msgFileName).toFile());

                try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(item.getObjectFile()))) {
                    Mail mail = (Mail) oin.readObject();
                    Optional<ZonedDateTime> next = getNextDelivery(mail);

                    final String key = mail.getName();
                    keyMappings.put(key, item);
                    if (!next.isPresent() || next.get().isBefore(ZonedDateTime.now())) {
                        enqueueInMemory(key);
                    } else {

                        // Schedule a task which will put the mail in the queue
                        // for processing after a given delay
                        long nextDeliveryDelay = ZonedDateTime.now().until(next.get(), ChronoUnit.MILLIS);
                        scheduler.schedule(() -> enqueueInMemory(key), nextDeliveryDelay, TimeUnit.MILLISECONDS);
                    }
                } catch (ClassNotFoundException | IOException e) {
                    LOGGER.error("Unable to load Mail", e);
                }
            }
        }
    }

    private Optional<ZonedDateTime> getNextDelivery(Mail mail) {
        Long next = (Long) mail.getAttribute(NEXT_DELIVERY);
        if (next == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()));
    }

    @Override
    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        final String key = mail.getName() + "-" + COUNTER.incrementAndGet();
        try {
            int i = RANDOM.nextInt(SPLITCOUNT) + 1;

            String name = queueDirName + "/" + i + "/" + key;

            final FileItem item = FileItem.of(name + OBJECT_EXTENSION, name + MSG_EXTENSION);
            if (delay > 0) {
                mail.setAttribute(NEXT_DELIVERY, System.currentTimeMillis() + unit.toMillis(delay));
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

            indexWriter.addDocument(toIndexDocument(mail, key));
            indexWriter.commit();

            keyMappings.put(key, item);

            if (delay > 0) {
                // The message should get delayed so schedule it for later
                scheduler.schedule(() -> enqueueInMemory(key), delay, unit);
            } else {
                inmemoryQueue.put(key);
            }

            //TODO: Think about exception handling in detail
        } catch (IOException | MessagingException | InterruptedException e) {
            throw new MailQueueException("Unable to enqueue mail", e);
        }

    }

    private void enqueueInMemory(String key) {
        try {
            inmemoryQueue.put(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Unable to init", e);
        }
    }

    private Document toIndexDocument(Mail mail, String key) {
        Document doc = new Document();

        doc.add(analyzedField(key, FILE_PATH_KEY));
        doc.add(analyzedField(mail.getName(), Type.Name.name()));
        doc.add(analyzedField(mail.getMaybeSender().asString(), Type.Sender.name()));

        if (mail.getRecipients() != null) {
            mail.getRecipients()
                    .stream()
                    .map(MailAddress::asString)
                    .forEach(mailAddress -> doc.add(analyzedField(mailAddress, Type.Recipient.name())));
        }

        return doc;
    }

    private Field analyzedField(String key, String filePathKey) {
        return new Field(filePathKey, key, Field.Store.YES, Field.Index.NOT_ANALYZED);
    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {
        enQueue(mail, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public MailQueueItem deQueue() throws MailQueueException {
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
                try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(fitem.getObjectFile()))) {
                    final Mail mail = (Mail) oin.readObject();
                    mail.setMessage(new MimeMessageCopyOnWriteProxy(new FileMimeMessageSource(fitem.getMessageFile())));
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
                    return mailQueueItemDecoratorFactory.decorate(fileMailQueueItem);
                }
                // TODO: Think about exception handling in detail
            } catch (IOException | ClassNotFoundException | MessagingException e) {
                throw new MailQueueException("Unable to dequeue", e);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailQueueException("Unable to dequeue", e);
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
        private final File objectfile;
        private final File messagefile;

        FileItem(File objectfile, File messagefile) {
            this.objectfile = objectfile;
            this.messagefile = messagefile;
        }

        public static FileItem of(String objectFileName, String messageFileName) {
            return new FileItem(new File(objectFileName), new File(messageFileName));
        }

        File getObjectFile() {
            return objectfile;
        }

        File getMessageFile() {
            return messagefile;
        }

        void delete() throws MailQueueException {
            try {
                FileUtils.forceDelete(getObjectFile());
            } catch (IOException e) {
                throw new MailQueueException("Unable to delete mail");
            }

            try {
                FileUtils.forceDelete(getMessageFile());
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

    @Override
    public long remove(Type type, String value) throws MailQueueException {
        try (IndexReader indexReader = IndexReader.open(indexWriter, true)) {
            int maxCount = Math.max(1, indexReader.maxDoc());
            IndexSearcher searcher = new IndexSearcher(indexReader);
            MutableLong count = new MutableLong();
            Term term;
            Query query;

            switch (type) {
                case Name:
                case Sender:
                case Recipient:
                    term = new Term(type.name(), value);
                    query = new TermQuery(term);
                    break;
                default:
                    throw new MailQueueException("Not supported yet");
            }

            Optional.ofNullable(searcher.search(query, maxCount).scoreDocs)
                    .filter(hits -> hits.length > 0)
                    .map(Stream::of)
                    .ifPresent(scoreDocStream -> scoreDocStream
                            .mapToInt(scoreDoc -> scoreDoc.doc)
                            .forEach(id -> Optional.ofNullable(Throwing.supplier(() -> searcher.doc(id, FIELDS_TO_LOAD)).get())
                                    .map(doc -> doc.get(FILE_PATH_KEY))
                                    .map(keyMappings::remove)
                                    .ifPresent(fileItem -> {
                                        Throwing.runnable(fileItem::delete).run();
                                        Throwing.<Query>consumer(indexWriter::deleteDocuments).accept(query);

                                        count.increment();
                                    })
                    ));

            return count.longValue();
        } catch (IOException e) {
            throw new MailQueueException("Cannot perform remove operation.", e);
        }
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
