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

package org.apache.james.mailbox.store.streaming;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.Header;
import org.apache.james.mailbox.store.ResultHeader;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;


public class PartContentBuilder {

    private static final byte[] EMPTY = {};

    private MimeTokenStream parser;

    private boolean empty = false;

    private boolean topLevel = true;

    public PartContentBuilder() {
        MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build();

        parser = new MimeTokenStream(config);
    }

    public void markEmpty() {
        empty = true;
    }

    public void parse(final InputStream in) {
        
        parser.setRecursionMode(RecursionMode.M_RECURSE);
        parser.parse(in);
        topLevel = true;
    }

    private void skipToStartOfInner(int position) throws IOException, MimeException {
        final EntityState state = parser.next();
        switch (state) {
            case T_START_MULTIPART:
                break;
            case T_START_MESSAGE:
                break;
            case T_END_OF_STREAM:
                throw new PartNotFoundException(position);
            case T_END_BODYPART:
                throw new PartNotFoundException(position);
            default:
                skipToStartOfInner(position);
        }
    }

    public void to(int position) throws IOException, MimeException {
        try {
            if (topLevel) {
                topLevel = false;
            } else {
                skipToStartOfInner(position);
            }
            for (int count = 0; count < position;) {
                final EntityState state = parser.next();
                switch (state) {
                    case T_BODY:
                        if (position == 1) {
                            count++;
                        }
                        break;
                    case T_START_BODYPART:
                        count++;
                        break;
                    case T_START_MULTIPART:
                        if (count > 0 && count < position) {
                            ignoreInnerMessage();
                        }
                        break;
                    case T_END_OF_STREAM:
                        throw new PartNotFoundException(position);
                case T_END_BODYPART:
                case T_END_HEADER:
                case T_END_MESSAGE:
                case T_END_MULTIPART:
                case T_EPILOGUE:
                case T_FIELD:
                case T_PREAMBLE:
                case T_RAW_ENTITY:
                case T_START_HEADER:
                case T_START_MESSAGE:
                    break;
                }
            }
        } catch (IllegalStateException e) {
            throw new PartNotFoundException(position, e);
        }
    }

    private void ignoreInnerMessage() throws IOException, UnexpectedEOFException, MimeException {
        for (EntityState state = parser.next(); state != EntityState.T_END_MULTIPART; state = parser
                .next()) {
            switch (state) {
                case T_END_OF_STREAM:
                    throw new UnexpectedEOFException();

                case T_START_MULTIPART:
                    ignoreInnerMessage();
                    break;
            case T_BODY:
            case T_END_BODYPART:
            case T_END_HEADER:
            case T_END_MESSAGE:
            case T_END_MULTIPART:
            case T_EPILOGUE:
            case T_FIELD:
            case T_PREAMBLE:
            case T_RAW_ENTITY:
            case T_START_BODYPART:
            case T_START_HEADER:
            case T_START_MESSAGE:
                break;
            }
        }
    }

    public Content getFullContent() throws IOException, UnexpectedEOFException, MimeException, MailboxException {
        final List<Header> headers = getMimeHeaders();
        final byte[] content = mimeBodyContent();
        return new FullByteContent(content, headers);
    }

    public Content getMessageBodyContent() throws IOException, MimeException {
        final byte[] content = messageBodyContent();
        return new ByteContent(content);
    }

    private byte[] messageBodyContent() throws IOException, MimeException {
        final byte[] content;
        if (empty) {
            content = EMPTY;
        } else {
            boolean valid;
            try {
                advancedToMessage();
                valid = true;
            } catch (UnexpectedEOFException e) {
                // No TEXT part
                valid = false;
            }
            if (valid) {
                parser.setRecursionMode(RecursionMode.M_FLAT);
                for (EntityState state = parser.getState(); state != EntityState.T_BODY
                        && state != EntityState.T_START_MULTIPART; state = parser
                        .next()) {
                    if (state == EntityState.T_END_OF_STREAM) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    content = IOUtils.toByteArray(parser.getInputStream());
                } else {
                    content = EMPTY;
                }
            } else {
                content = EMPTY;
            }
        }
        return content;
    }

    public Content getMimeBodyContent() throws IOException, MimeException {
        final byte[] content = mimeBodyContent();
        return new ByteContent(content);
    }

    private byte[] mimeBodyContent() throws IOException, MimeException {
        final byte[] content;
        if (empty) {
            content = EMPTY;
        } else {
            parser.setRecursionMode(RecursionMode.M_FLAT);
            boolean valid = true;
            for (EntityState state = parser.getState(); state != EntityState.T_BODY
                    && state != EntityState.T_START_MULTIPART; state = parser
                    .next()) {
                if (state == EntityState.T_END_OF_STREAM) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                content = IOUtils.toByteArray(parser.getInputStream());
            } else {
                content = EMPTY;
            }
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    public List<MessageResult.Header> getMimeHeaders() throws IOException, UnexpectedEOFException, MimeException {
        final List<MessageResult.Header> results;
        if (empty) {
            results = Collections.EMPTY_LIST;
        } else {
            results = new ArrayList<MessageResult.Header>();
            for (EntityState state = parser.getState(); state != EntityState.T_END_HEADER; state = parser
                    .next()) {
                switch (state) {
                    case T_END_OF_STREAM:
                        throw new UnexpectedEOFException();

                    case T_FIELD:
                        final String fieldValue = parser.getField().getBody().trim();
                        final String fieldName = parser.getField().getName();
                        ResultHeader header = new ResultHeader(fieldName, fieldValue);
                        results.add(header);
                        break;
                case T_BODY:
                case T_END_BODYPART:
                case T_END_HEADER:
                case T_END_MESSAGE:
                case T_END_MULTIPART:
                case T_EPILOGUE:
                case T_PREAMBLE:
                case T_RAW_ENTITY:
                case T_START_BODYPART:
                case T_START_HEADER:
                case T_START_MESSAGE:
                case T_START_MULTIPART:
                    break;
                }
            }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    public List<MessageResult.Header> getMessageHeaders() throws IOException, MimeException {
        final List<MessageResult.Header> results;
        if (empty) {
            results = Collections.EMPTY_LIST;
        } else {
            results = new ArrayList<MessageResult.Header>();
            try {
                advancedToMessage();

                for (EntityState state = parser.getState(); state != EntityState.T_END_HEADER; state = parser
                        .next()) {
                    switch (state) {
                        case T_END_OF_STREAM:
                            throw new IOException("Unexpected EOF");

                        case T_FIELD:
                            final String fieldValue = parser.getField().getBody().trim();
                            final String fieldName = parser.getField().getName();
                            ResultHeader header = new ResultHeader(fieldName, fieldValue);
                            results.add(header);
                            break;
                    case T_BODY:
                    case T_END_BODYPART:
                    case T_END_HEADER:
                    case T_END_MESSAGE:
                    case T_END_MULTIPART:
                    case T_EPILOGUE:
                    case T_PREAMBLE:
                    case T_START_HEADER:
                    case T_START_MESSAGE:
                    case T_START_MULTIPART:
                    case T_RAW_ENTITY:
                    case T_START_BODYPART:
                        break;
                    }
                }
            } catch (UnexpectedEOFException e) {
                // No headers found
            }
        }
        return results;
    }

    private void advancedToMessage() throws IOException, UnexpectedEOFException, MimeException {
        for (EntityState state = parser.getState(); state != EntityState.T_START_MESSAGE; state = parser
                .next()) {
            if (state == EntityState.T_END_OF_STREAM) {
                throw new UnexpectedEOFException();
            }
        }
    }

    public static final class UnexpectedEOFException extends MimeException {

        private static final long serialVersionUID = -3755637466593055796L;

        public UnexpectedEOFException() {
            super("Unexpected EOF");
        }
    }

    public static final class PartNotFoundException extends MimeException {

        private static final long serialVersionUID = 7519976990944851574L;

        private final int position;

        public PartNotFoundException(int position) {
            this(position, null);
        }

        public PartNotFoundException(int position, Exception e) {
            super("Part " + position + " not found.", e);
            this.position = position;
        }

        public int getPosition() {
            return position;
        }

    }
}
