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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.mail.MessagingException;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.core.MimeMessageSource;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;

/**
 * {@link ManageableMailQueue} implementation which use the fs to store {@link Mail}'s
 * <p/>
 * On create of the {@link FileMailQueue} the {@link #init()} will get called. This takes care of
 * loading the needed meta-data into memory for fast access.
 */
public class FileMailQueue implements ManageableMailQueue {

    private final ConcurrentHashMap<String, FileItem> keyMappings = new ConcurrentHashMap<String, FileMailQueue.FileItem>();
    private final BlockingQueue<String> inmemoryQueue = new LinkedBlockingQueue<String>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final static AtomicLong COUNTER = new AtomicLong();
    private final String queueDirName;
    private final File queueDir;
    private final Logger log;

    private final boolean sync;
    private final static String MSG_EXTENSION = ".msg";
    private final static String OBJECT_EXTENSION = ".obj";
    private final static String NEXT_DELIVERY = "FileQueueNextDelivery";
    private final static int SPLITCOUNT = 10;

    public FileMailQueue(File parentDir, String queuename, boolean sync, Logger log) throws IOException {
        this.log = log;
        this.sync = sync;
        this.queueDir = new File(parentDir, queuename);
        this.queueDirName = queueDir.getAbsolutePath();
        init();
    }

    private void init() throws IOException {

        for (int i = 1; i <= SPLITCOUNT; i++) {

            File qDir = new File(queueDir, Integer.toString(i));
            FileUtils.forceMkdir(qDir);

            String[] files = qDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(OBJECT_EXTENSION);
                }
            });

            for (final String name : files) {

                ObjectInputStream oin = null;

                try {

                    final String msgFileName = name.substring(0, name.length() - OBJECT_EXTENSION.length()) + MSG_EXTENSION;

                    FileItem item = new FileItem(qDir.getAbsolutePath() + File.separator + name, qDir.getAbsolutePath() + File.separator + msgFileName);

                    oin = new ObjectInputStream(new FileInputStream(item.getObjectFile()));
                    Mail mail = (Mail) oin.readObject();
                    Long next = (Long) mail.getAttribute(NEXT_DELIVERY);
                    if (next == null) {
                        next = 0L;
                    }

                    final String key = mail.getName();
                    keyMappings.put(key, item);
                    if (next <= System.currentTimeMillis()) {

                        try {
                            inmemoryQueue.put(key);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Unable to init", e);
                        }
                    } else {

                        // Schedule a task which will put the mail in the queue
                        // for processing after a given delay
                        scheduler.schedule(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    inmemoryQueue.put(key);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Unable to init", e);
                                }
                            }
                        }, next - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    }

                } catch (ClassNotFoundException e1) {
                    log.error("Unable to load Mail", e1);
                } catch (IOException e) {
                    log.error("Unable to load Mail", e);
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

    @Override
    public void enQueue(final Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        final String key = mail.getName() + "-" + COUNTER.incrementAndGet();
        FileOutputStream out = null;
        FileOutputStream foout = null;
        ObjectOutputStream oout = null;
        try {
            int i = (int) (Math.random() * SPLITCOUNT + 1);


            String name = queueDirName + "/" + i + "/" + key;

            final FileItem item = new FileItem(name + OBJECT_EXTENSION, name + MSG_EXTENSION);
            if (delay > 0) {
                mail.setAttribute(NEXT_DELIVERY, System.currentTimeMillis() + unit.toMillis(delay));
            }
            foout = new FileOutputStream(item.getObjectFile());
            oout = new ObjectOutputStream(foout);
            oout.writeObject(mail);
            oout.flush();
            if (sync) foout.getFD().sync();
            out = new FileOutputStream(item.getMessageFile());

            mail.getMessage().writeTo(out);
            out.flush();
            if (sync) out.getFD().sync();

            keyMappings.put(key, item);

            if (delay > 0) {
                // The message should get delayed so schedule it for later
                scheduler.schedule(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            inmemoryQueue.put(key);

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Unable to init", e);
                        }
                    }
                }, delay, unit);

            } else {
                inmemoryQueue.put(key);
            }

            //TODO: Think about exception handling in detail
        } catch (FileNotFoundException e) {
            throw new MailQueueException("Unable to enqueue mail", e);
        } catch (IOException e) {
            throw new MailQueueException("Unable to enqueue mail", e);

        } catch (MessagingException e) {
            throw new MailQueueException("Unable to enqueue mail", e);
        } catch (InterruptedException e) {
            throw new MailQueueException("Unable to enqueue mail", e);

        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
            if (oout != null) {
                try {
                    oout.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
            if (foout != null) {
                try {
                    foout.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
        }

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
            ObjectInputStream oin = null;
            try {
                final File objectFile = new File(fitem.getObjectFile());
                final File msgFile = new File(fitem.getMessageFile());
                oin = new ObjectInputStream(new FileInputStream(objectFile));
                final Mail mail = (Mail) oin.readObject();
                mail.setMessage(new MimeMessageCopyOnWriteProxy(new FileMimeMessageSource(msgFile)));
                return new MailQueueItem() {

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

                // TODO: Think about exception handling in detail
            } catch (FileNotFoundException e) {
                throw new MailQueueException("Unable to dequeue", e);
            } catch (IOException e) {
                throw new MailQueueException("Unable to dequeue", e);
            } catch (ClassNotFoundException e) {
                throw new MailQueueException("Unable to dequeue", e);
            } catch (MessagingException e) {
                throw new MailQueueException("Unable to dequeue", e);

            } finally {
                if (oin != null) {
                    try {
                        oin.close();
                    } catch (IOException e) {
                        // ignore on close
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailQueueException("Unable to dequeue", e);
        }
    }

    private final class FileMimeMessageSource extends MimeMessageSource implements Disposable {

        private File file;
        private SharedFileInputStream in;

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
            IOUtils.closeQuietly(in);
            file = null;
        }

    }

    /**
     * Helper class which is used to reference the path to the object and msg file
     */
    private final class FileItem {
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
                log.debug("Remove of msg file for mail failed");
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
        final Iterator<Entry<String, FileItem>> items = keyMappings.entrySet().iterator();
        long count = 0;
        while (items.hasNext()) {
            Entry<String, FileItem> entry = items.next();
            FileItem item = entry.getValue();
            String key = entry.getKey();

            item.delete();
            keyMappings.remove(key);
            count++;

        }
        return count;
    }

    /**
     * TODO: implement me
     *
     * @see ManageableMailQueue#remove(org.apache.james.queue.api.ManageableMailQueue.Type, String)
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
            private MailQueueItemView item = null;

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Read-only");
            }

            @Override
            public MailQueueItemView next() {
                if (hasNext()) {
                    MailQueueItemView vitem = item;
                    item = null;
                    return vitem;
                } else {

                    throw new NoSuchElementException();
                }
            }

            @Override
            public boolean hasNext() {
                if (item == null) {
                    while (items.hasNext()) {
                        ObjectInputStream in = null;
                        try {
                            in = new ObjectInputStream(new FileInputStream(items.next().getObjectFile()));
                            final Mail mail = (Mail) in.readObject();
                            item = new MailQueueItemView() {

                                @Override
                                public long getNextDelivery() {
                                    return (Long) mail.getAttribute(NEXT_DELIVERY);
                                }

                                @Override
                                public Mail getMail() {
                                    return mail;
                                }
                            };
                            return true;
                        } catch (FileNotFoundException e) {
                            log.info("Unable to load mail", e);
                        } catch (IOException e) {
                            log.info("Unable to load mail", e);

                        } catch (ClassNotFoundException e) {
                            log.info("Unable to load mail", e);
                        } finally {
                            if (in != null) {
                                try {
                                    in.close();
                                } catch (IOException e) {
                                    // ignore on close
                                }
                            }
                        }
                    }
                    return false;
                } else {
                    return true;
                }
            }

            @Override
            public void close() {
                // do nothing
            }
        };
    }

}
