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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.util.SharedByteArrayInputStream;
import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.james.lifecycle.api.Disposable;

/**
 * Takes an input stream and creates a repeatable input stream source for a
 * MimeMessageWrapper. It does this by completely reading the input stream and
 * saving that to data to an {@link DeferredFileOutputStream} with its threshold set to 100kb
 *
 * This class is not thread safe!
 */
public class MimeMessageInputStreamSource extends MimeMessageSource implements Disposable {

    private final Set<InputStream> streams = new HashSet<>();

    /**
     * A temporary file used to hold the message stream
     */
    private DeferredFileOutputStream out;

    /**
     * The full path of the temporary file
     */
    private final String sourceId;

    /**
     * 100kb threshold for the stream.
     */
    private static final int THRESHOLD = 1024 * 100;

    /**
     * Temporary directory to use
     */
    private static final File TMPDIR = new File(System.getProperty("java.io.tmpdir"));

    /**
     * Construct a new MimeMessageInputStreamSource from an
     * <code>InputStream</code> that contains the bytes of a MimeMessage.
     *
     * @param key the prefix for the name of the temp file
     * @param in  the stream containing the MimeMessage
     * @throws MessagingException if an error occurs while trying to store the stream
     */
    public MimeMessageInputStreamSource(String key, InputStream in) throws MessagingException {
        super();
        // We want to immediately read this into a temporary file
        // Create a temp file and channel the input stream into it
        try {
            out = new DeferredFileOutputStream(THRESHOLD, "mimemessage-" + key, ".m64", TMPDIR);
            IOUtils.copy(in, out);
            sourceId = key;
        } catch (IOException ioe) {
            File file = out.getFile();
            if (file != null) {
                FileUtils.deleteQuietly(file);
            }
            throw new MessagingException("Unable to retrieve the data: " + ioe.getMessage(), ioe);
        } finally {
            try {
                if (out != null) {
                    out.close();
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

    public MimeMessageInputStreamSource(String key) {
        super();
        out = new DeferredFileOutputStream(THRESHOLD, key, ".m64", TMPDIR);
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
        InputStream in;
        if (out.isInMemory()) {
            in = new SharedByteArrayInputStream(out.getData());
        } else {
            in = new SharedFileInputStream(out.getFile());
        }
        streams.add(in);
        return in;
    }

    /**
     * Get the size of the temp file
     *
     * @return the size of the temp file
     * @throws IOException if an error is encoutered while computing the size of the
     *                     message
     */
    @Override
    public long getMessageSize() throws IOException {
        return out.getByteCount();
    }

    public OutputStream getWritableOutputStream() {
        return out;
    }

    @Override
    public void dispose() {
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
            out = null;
        }
    }

}
