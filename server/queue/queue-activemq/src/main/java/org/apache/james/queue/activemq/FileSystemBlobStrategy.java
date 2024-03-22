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
package org.apache.james.queue.activemq;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import jakarta.jms.JMSException;

import org.apache.activemq.BlobMessage;
import org.apache.activemq.blob.BlobDownloadStrategy;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.blob.BlobUploadStrategy;
import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;

/**
 * {@link BlobUploadStrategy} and {@link BlobDownloadStrategy} implementation
 * which use the {@link FileSystem} to lookup the {@link File} for the
 * {@link BlobMessage}
 */
public class FileSystemBlobStrategy implements BlobUploadStrategy, BlobDownloadStrategy, ActiveMQSupport {
    private static final Pattern PATTERN = Pattern.compile("[:\\\\/*?|<>]");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FileSystem fileSystem;
    private final BlobTransferPolicy policy;
    private final int splitCount;
    private final Object lock = new Object();

    public FileSystemBlobStrategy(BlobTransferPolicy policy, FileSystem fileSystem, int splitCount) {
        this.fileSystem = fileSystem;
        this.policy = policy;
        this.splitCount = splitCount;
    }

    @Override
    public URL uploadFile(ActiveMQBlobMessage message, File file) throws JMSException, IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            return uploadStream(message, in);
        }
    }

    @Override
    public URL uploadStream(ActiveMQBlobMessage message, InputStream in) throws JMSException, IOException {
        File f = getFile(message);
        try (FileOutputStream out = new FileOutputStream(f)) {
            IOUtils.copy(in, out, policy.getBufferSize());
            out.flush();
            // File.toURL() is deprecated
            return f.toURI().toURL();
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

    @Override
    public void deleteFile(ActiveMQBlobMessage message) throws IOException, JMSException {
        File f = getFile(message);
        synchronized (lock) {
            FileUtils.forceDelete(f);
        }
    }

    /**
     * Returns a {@link FileInputStream} for the give {@link BlobMessage}
     */
    @Override
    public InputStream getInputStream(ActiveMQBlobMessage message) throws IOException, JMSException {
        return new FileInputStream(getFile(message));
    }

    /**
     * Return the {@link File} for the {@link ActiveMQBlobMessage}. The
     * {@link File} is lookup via the {@link FileSystem} service
     *
     * @param message
     * @return file
     * @throws JMSException
     * @throws FileNotFoundException
     */
    protected File getFile(ActiveMQBlobMessage message) throws JMSException, IOException {
        if (message.getURL() != null) {
            return fileSystem.getFile(message.getURL().toString());
        }

        // Make sure it works on windows in all cases and make sure
        // we use the JMS Message ID as filename so we are safe in the case
        // we try to stream from and to the same mail
        String filename = PATTERN.matcher(message.getJMSMessageID()).replaceAll("_");
        int i = RANDOM.nextInt(splitCount) + 1;

        String queueUrl = policy.getUploadUrl() + "/" + i;

        File queueF = fileSystem.getFile(queueUrl);

        synchronized (lock) {
            // check if we need to create the queue folder
            FileUtils.forceMkdir(queueF);
        }

        return fileSystem.getFile(queueUrl + "/" + filename);
    }
}
