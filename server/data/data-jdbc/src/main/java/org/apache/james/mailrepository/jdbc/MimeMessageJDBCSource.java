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

package org.apache.james.mailrepository.jdbc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.james.core.MimeMessageSource;
import org.apache.james.repository.api.StreamRepository;
import org.apache.james.util.sql.JDBCUtil;

/**
 * This class points to a specific message in a repository. This will return an
 * InputStream to the JDBC field/record, possibly sequenced with the file
 * stream.
 */
public class MimeMessageJDBCSource extends MimeMessageSource {

    /**
     * Whether 'deep debugging' is turned on.
     */
    private static final boolean DEEP_DEBUG = false;

    // Define how to get to the data
    JDBCMailRepository repository = null;
    String key = null;
    StreamRepository sr = null;

    private long size = -1;

    /**
     * SQL used to retrieve the message body
     */
    String retrieveMessageBodySQL = null;

    /**
     * SQL used to retrieve the size of the message body
     */
    String retrieveMessageBodySizeSQL = null;

    /**
     * The JDBCUtil helper class
     */
    private static final JDBCUtil theJDBCUtil = new JDBCUtil();

    /**
     * Construct a MimeMessageSource based on a JDBC repository, a key, and a
     * stream repository (where we might store the message body)
     * 
     * @param repository
     *            the JDBCMailRepository to use
     * @param key
     *            the key for the particular stream in the stream repository to
     *            be used by this data source.
     * @param sr
     *            the stream repository used by this data source.
     * @throws IOException
     *             get thrown if an IO error detected
     */
    public MimeMessageJDBCSource(JDBCMailRepository repository, String key, StreamRepository sr) throws IOException {
        super();

        if (repository == null) {
            throw new IOException("Repository is null");
        }
        if (key == null) {
            throw new IOException("Message name (key) was not defined");
        }
        this.repository = repository;
        this.key = key;
        this.sr = sr;

        retrieveMessageBodySQL = repository.sqlQueries.getSqlString("retrieveMessageBodySQL", true);
        // this is optional
        retrieveMessageBodySizeSQL = repository.sqlQueries.getSqlString("retrieveMessageBodySizeSQL");

    }

    /**
     * Returns a unique String ID that represents the location from where this
     * source is loaded. This will be used to identify where the data is,
     * primarily to avoid situations where this data would get overwritten.
     * 
     * @return the String ID
     */
    public String getSourceId() {
        return repository.repositoryName + "/" + key;
    }

    /**
     * Return the input stream to the database field and then the file stream.
     * This should be smart enough to work even if the file does not exist. This
     * is to support a repository with the entire message in the database, which
     * is how James 1.2 worked.
     * 
     * @see org.apache.james.core.MimeMessageSource#getInputStream()
     */
    public synchronized InputStream getInputStream() throws IOException {
        Connection conn = null;
        PreparedStatement retrieveMessageStream = null;
        ResultSet rsRetrieveMessageStream = null;
        try {
            conn = repository.getConnection();

            byte[] headers;

            long start = 0;
            if (DEEP_DEBUG) {
                start = System.currentTimeMillis();
                System.out.println("starting");
            }
            retrieveMessageStream = conn.prepareStatement(retrieveMessageBodySQL);
            retrieveMessageStream.setString(1, key);
            retrieveMessageStream.setString(2, repository.repositoryName);
            rsRetrieveMessageStream = retrieveMessageStream.executeQuery();

            if (!rsRetrieveMessageStream.next()) {
                throw new IOException("Could not find message");
            }

            String getBodyOption = repository.sqlQueries.getDbOption("getBody");
            if (getBodyOption != null && getBodyOption.equalsIgnoreCase("useBlob")) {
                Blob b = rsRetrieveMessageStream.getBlob(1);
                headers = b.getBytes(1, (int) b.length());
            } else {
                headers = rsRetrieveMessageStream.getBytes(1);
            }
            if (DEEP_DEBUG) {
                System.err.println("stopping");
                System.err.println(System.currentTimeMillis() - start);
            }

            InputStream in = new ByteArrayInputStream(headers);
            try {
                if (sr != null) {
                    in = new SequenceInputStream(in, sr.get(key));
                }
            } catch (Exception e) {
                // ignore this... either sr is null, or the file does not exist
                // or something else
            }
            return in;
        } catch (SQLException sqle) {
            throw new IOException(sqle.toString());
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsRetrieveMessageStream);
            theJDBCUtil.closeJDBCStatement(retrieveMessageStream);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Runs a custom SQL statement to check the size of the message body
     * 
     * @see org.apache.james.core.MimeMessageSource#getMessageSize()
     */
    public synchronized long getMessageSize() throws IOException {
        if (size != -1)
            return size;
        if (retrieveMessageBodySizeSQL == null) {
            // There was no SQL statement for this repository... figure it out
            // the hard way
            System.err.println("no SQL statement to find size");
            return size = super.getMessageSize();
        }
        Connection conn = null;
        PreparedStatement retrieveMessageSize = null;
        ResultSet rsRetrieveMessageSize = null;
        try {
            conn = repository.getConnection();

            retrieveMessageSize = conn.prepareStatement(retrieveMessageBodySizeSQL);
            retrieveMessageSize.setString(1, key);
            retrieveMessageSize.setString(2, repository.repositoryName);
            rsRetrieveMessageSize = retrieveMessageSize.executeQuery();

            if (!rsRetrieveMessageSize.next()) {
                throw new IOException("Could not find message");
            }

            size = rsRetrieveMessageSize.getLong(1);

            InputStream in = null;
            try {
                if (sr != null) {
                    if (sr instanceof org.apache.james.repository.file.FilePersistentStreamRepository) {
                        size += ((org.apache.james.repository.file.FilePersistentStreamRepository) sr).getSize(key);
                    } else {
                        in = sr.get(key);
                        int len;
                        byte[] block = new byte[1024];
                        while ((len = in.read(block)) > -1) {
                            size += len;
                        }
                    }
                }
            } catch (Exception e) {
                // ignore this... either sr is null, or the file does not exist
                // or something else
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ioe) {
                    // Ignored - no access to logger at this point in the code
                }
            }

            return size;
        } catch (SQLException sqle) {
            throw new IOException(sqle.toString());
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsRetrieveMessageSize);
            theJDBCUtil.closeJDBCStatement(retrieveMessageSize);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Check to see whether this is the same repository and the same key
     */
    public boolean equals(Object obj) {
        if (obj instanceof MimeMessageJDBCSource) {
            // TODO: Figure out whether other instance variables should be part
            // of
            // the equals equation
            MimeMessageJDBCSource source = (MimeMessageJDBCSource) obj;
            return ((source.key.equals(key)) || ((source.key != null) && source.key.equals(key))) && ((source.repository == repository) || ((source.repository != null) && source.repository.equals(repository)));
        }
        return false;
    }

    /**
     * Provide a hash code that is consistent with equals for this class
     * 
     * @return the hash code
     */
    public int hashCode() {
        int result = 17;
        if (key != null) {
            result = 37 * key.hashCode();
        }
        if (repository != null) {
            result = 37 * repository.hashCode();
        }
        return result;
    }

}
