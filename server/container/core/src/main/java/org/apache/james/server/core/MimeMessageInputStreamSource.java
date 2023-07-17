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

package org.apache.james.server.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.mail.MessagingException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.util.SizeFormat;

/**
 * Takes an input stream and creates a repeatable input stream source for a
 * MimeMessageWrapper. It does this by completely reading the input stream and
 * saving that to data to an {@link DeferredFileOutputStream} with its threshold set to 100kb
 *
 * This class is not thread safe!
 */
public class MimeMessageInputStreamSource extends Disposable.LeakAware<MimeMessageInputStreamSource.Resource> implements MimeMessageSource {
    /**
     * 100kb threshold for the stream.
     */
    private static final int DEFAULT_THRESHOLD = 1024 * 100;

    private static int threshold() {
        return Optional.ofNullable(System.getProperty("james.message.memory.threshold"))
            .map(SizeFormat::parseAsByteCount)
            .map(Math::toIntExact)
            .orElse(DEFAULT_THRESHOLD);
    }

    private static final int THRESHOLD = threshold();
    /**
     * The full path of the temporary file
     */
    private final String sourceId;

    static class Resource extends LeakAware.Resource {
        private final BufferedDeferredFileOutputStream out;
        private final Set<InputStream> streams;

        Resource(BufferedDeferredFileOutputStream out, Set<InputStream> streams) {
            super(() -> {
                // explicit close all streams
                for (InputStream stream : streams) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        //ignore exception during close
                    }
                }

                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        //ignore exception during close
                    }
                    File file = out.getFile();
                    if (file != null) {
                        FileUtils.deleteQuietly(file);
                        file = null;
                    }
                    out.dispose();
                }
            });
            this.out = out;
            this.streams = streams;
        }

        public BufferedDeferredFileOutputStream getOut() {
            return out;
        }


    }

    public static MimeMessageInputStreamSource create(String key, InputStream in) throws MessagingException {
        Disposable.LeakAware.track();
        BufferedDeferredFileOutputStream out = new BufferedDeferredFileOutputStream(THRESHOLD, "mimemessage-" + key, ".m64");
        Resource resource = new Resource(out, new HashSet<>());

        return new MimeMessageInputStreamSource(resource, key, in);
    }

    public static MimeMessageInputStreamSource create(String key) {
        Disposable.LeakAware.track();
        BufferedDeferredFileOutputStream out = new BufferedDeferredFileOutputStream(THRESHOLD, "mimemessage-" + key, ".m64");
        Resource resource = new Resource(out, new HashSet<>());

        return new MimeMessageInputStreamSource(resource, key);
    }

    /**
     * Construct a new MimeMessageInputStreamSource from an
     * <code>InputStream</code> that contains the bytes of a MimeMessage.
     *
     * @param key the prefix for the name of the temp file
     * @param in  the stream containing the MimeMessage
     * @throws MessagingException if an error occurs while trying to store the stream
     */
    private MimeMessageInputStreamSource(Resource resource, String key, InputStream in) throws MessagingException {
        super(resource);
        // We want to immediately read this into a temporary file
        // Create a temp file and channel the input stream into it
        try {
            IOUtils.copy(in, resource.out);
            sourceId = key;
        } catch (IOException ioe) {
            File file = resource.out.getFile();
            if (file != null) {
                FileUtils.deleteQuietly(file);
            }
            throw new MessagingException("Unable to retrieve the data: " + ioe.getMessage(), ioe);
        } finally {
            try {
                if (resource.out != null) {
                    resource.out.close();
                }
            } catch (IOException ioe) {
                // Ignored - logging unavailable to log this non-fatal error.
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ioe) {
                // Ignored - logging unavailable to log this non-fatal error.
            }

        }
    }

    private MimeMessageInputStreamSource(Resource resource, String key) {
        super(resource);
        sourceId = key;
    }

    /**
     * Returns the unique identifier of this input stream source
     *
     * @return the unique identifier for this MimeMessageInputStreamSource
     */
    @Override
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Get an input stream to retrieve the data stored in the temporary file
     *
     * @return a <code>BufferedInputStream</code> containing the data
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (getResource().getOut().isInMemory()) {
            return new ByteArrayInputStream(getResource().getOut().getData());
        } else {
            InputStream in = UnsynchronizedBufferedInputStream.builder()
                .setInputStream(new FileInputStream(getResource().getOut().getFile()))
                .setBufferSize(2048)
                .get();
            getResource().streams.add(in);
            return in;
        }
    }

    /**
     * Get the size of the temp file
     *
     * @return the size of the temp file
     */
    @Override
    public long getMessageSize() {
        return getResource().getOut().getByteCount();
    }

    public OutputStream getWritableOutputStream() {
        return getResource().getOut();
    }
}
