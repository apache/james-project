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

package org.apache.james.imap.api.message.response;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.MessageFlags;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.mailbox.MessageSequenceNumber;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.UidValidity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Represents an <code>RFC2060</code> status response. The five specified status
 * server responses (<code>OK<code>.
 * <code>NO</code>, <code>BAD</code>, <code>PREAUTH</code> and <code>BYE</code>)
 * are modeled by this single interface. They are differentiated by
 * {@link #getServerResponseType()}
 * </p>
 */
public interface StatusResponse extends ImapResponseMessage {

    Set<String> AVAILABLE_CHARSET_NAMES =
        Charset.availableCharsets()
            .values()
            .stream()
            .flatMap(charset -> Stream.concat(
                Stream.of(charset.name()),
                charset.aliases().stream()))
            .collect(ImmutableSet.toImmutableSet());

    /**
     * Gets the server response type of this status message.
     * 
     * @return The type, either {@link Type#BAD}, {@link Type#BYE}, {@link Type#NO},
     *         {@link Type#OK} or {@link Type#PREAUTH}
     */
    Type getServerResponseType();

    /**
     * Gets the tag.
     * 
     * @return if tagged response, the tag. Otherwise null.
     */
    Tag getTag();

    /**
     * Gets the command.
     * 
     * @return if tagged response, the command. Otherwise null
     */
    ImapCommand getCommand();

    /**
     * Gets the key to the human readable text to be displayed. Required.
     * 
     * @return key for the text message to be displayed, not null
     */
    HumanReadableText getTextKey();

    /**
     * Gets the response code. Optional.
     * 
     * @return <code>ResponseCode</code>, or null if there is no response code
     */
    ResponseCode getResponseCode();

    /**
     * Enumerates types of RC2060 status response
     */
    enum Type {
        /** RFC2060 <code>OK</code> server response */
        OK("OK"),
        /** RFC2060 <code>OK</code> server response */
        NO("NO"),
        /** RFC2060 <code>BAD</code> server response */
        BAD("BAD"),
        /** RFC2060 <code>PREAUTH</code> server response */
        PREAUTH("PREAUTH"),
        /** RFC2060 <code>BYE</code> server response */
        BYE("BYE");

        private final String code;
        private final byte[] codeAsBytes;

        Type(String code) {
            this.code = code;
            codeAsBytes = code.getBytes(StandardCharsets.US_ASCII);
        }

        public byte[] getCodeAsBytes() {
            return codeAsBytes;
        }

        public final String getCode() {
            return code;
        }

        public String toString() {
            return code;
        }
    }

    /**
     * Enumerates response codes.
     */
    final class ResponseCode {

        /** RFC2060 <code>ALERT</code> response code */
        private static final ResponseCode ALERT = new ResponseCode("ALERT");

        /** RFC2060 <code>PARSE</code> response code */
        private static final ResponseCode PARSE = new ResponseCode("PARSE");

        /** RFC2060 <code>READ_ONLY</code> response code */
        private static final ResponseCode READ_ONLY = new ResponseCode("READ-ONLY");

        /** RFC2060 <code>READ_WRITE</code> response code */
        private static final ResponseCode READ_WRITE = new ResponseCode("READ-WRITE");

        /** RFC2060 <code>TRYCREATE</code> response code */
        private static final ResponseCode TRYCREATE = new ResponseCode("TRYCREATE");
        private static final ResponseCode OVERQUOTA = new ResponseCode("OVERQUOTA");

        /** RFC5162 <code>CLOSED</code> response code */
        private static final ResponseCode CLOSED = new ResponseCode("CLOSED");

        
        /** RFC4315 <code>APPENDUID</code> response code */
        public static ResponseCode appendUid(UidValidity uidValidity, UidRange[] uids) {
            String uidParam = formatRanges(uids);
            return new ResponseCode("APPENDUID", Arrays.asList(uidParam), uidValidity.asLong(), false);
        }

        public static ResponseCode mailboxId(MailboxId mailboxId) {
            return new ResponseCode("MAILBOXID", ImmutableList.of(mailboxId.serialize()), NO_NUMBER, true);
        }

        /** RFC4315 <code>COPYUID</code> response code */
        public static ResponseCode copyUid(UidValidity uidValidity, IdRange[] sourceRanges, IdRange[] targetRanges) {
            String source = formatRanges(sourceRanges);
            String target = formatRanges(targetRanges);

            return new ResponseCode("COPYUID", Arrays.asList(source, target), uidValidity.asLong(), false);
        }

        /** RFC4551 <code>Conditional STORE</code> response code */
        public static ResponseCode condStore(IdRange[] failedRanges) {
            String failed = formatRanges(failedRanges);

            return new ResponseCode("MODIFIED", Arrays.asList(failed), 0, false);
        }
        
        /** RFC4551 <code>Conditional STORE</code> response code */
        public static ResponseCode condStore(UidRange[] failedRanges) {
            String failed = formatRanges(failedRanges);

            return new ResponseCode("MODIFIED", Arrays.asList(failed), 0, false);
        }
        
        private static String formatRanges(IdRange[] ranges) {
            if (ranges == null || ranges.length == 0) {
                return "*";
            }
            StringBuilder rangeBuilder = new StringBuilder();
            for (int i = 0; i < ranges.length; i++) {
                rangeBuilder.append(ranges[i].getFormattedString());
                if (i + 1 < ranges.length) {
                    rangeBuilder.append(",");
                }
            }
            return rangeBuilder.toString();
        }

        private static String formatRanges(UidRange[] ranges) {
            if (ranges == null || ranges.length == 0) {
                return "*";
            }
            StringBuilder rangeBuilder = new StringBuilder();
            for (int i = 0; i < ranges.length; i++) {
                rangeBuilder.append(ranges[i].getFormattedString());
                if (i + 1 < ranges.length) {
                    rangeBuilder.append(",");
                }
            }
            return rangeBuilder.toString();
        }

        
        /**
         * Create a RFC5162 (QRESYNC) <code>CLOSED</code> response code
         * 
         * @return code
         */
        public static ResponseCode closed() {
            return CLOSED;
        }
        
        /**
         * Creates a RFC2060 <code>ALERT</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode alert() {
            return ALERT;
        }

        /**
         * Creates a RFC2060 <code>BADCHARSET</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode badCharset() {
            return new ResponseCode("BADCHARSET", AVAILABLE_CHARSET_NAMES);
        }

        /**
         * Creates a RFC2060 <code>PARSE</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode parse() {
            return PARSE;
        }

        /**
         * Creates a RFC2060 <code>PERMENANTFLAGS</code> response code.
         * 
         * @param flags
         *            <code>Collection<String></code> containing flag names
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode permanentFlags(Flags flags) {
            return new ResponseCode("PERMANENTFLAGS", MessageFlags.names(flags));
        }

        /**
         * Creates a RFC2060 <code>READ-ONLY</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode readOnly() {
            return READ_ONLY;
        }

        /**
         * Creates a RFC2060 <code>READ-WRITE</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode readWrite() {
            return READ_WRITE;
        }

        /**
         * Creates a RFC2060 <code>TRYCREATE</code> response code.
         * 
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode tryCreate() {
            return TRYCREATE;
        }

        public static ResponseCode overQuota() {
            return OVERQUOTA;
        }

        /**
         * Creates a RFC2060 <code>UIDVALIDITY</code> response code.
         * 
         * @param uid
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode uidValidity(UidValidity uid) {
            return new ResponseCode("UIDVALIDITY", uid.asLong());
        }

        /**
         * Creates a RFC2060 <code>UNSEEN</code> response code.
         * 
         * @param numberUnseen
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode unseen(MessageSequenceNumber numberUnseen) {
            return new ResponseCode("UNSEEN", numberUnseen.asInt());
        }

        /**
         * Creates a RFC2060 <code>UIDNEXT</code> response code.
         * 
         * @param uid
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode uidNext(MessageUid uid) {
            return new ResponseCode("UIDNEXT", uid.asLong());
        }

        
        /**
         * Create a RFC4551 <code>HIGESTMODSEQ</code> response code
         * 
         * @param modSeq positive non-zero long
         * @return <code>ResponseCode</code>
         */
        public static ResponseCode highestModSeq(ModSeq modSeq) {
            return new ResponseCode("HIGHESTMODSEQ", modSeq.asLong());
        }
        
        /**
         * Create a RFC5464 getMetadata which support MAXSIZE
         * @param entryLong positive non-zero long
         * @return <code>ResponseCode</code>
         */
        public static ResponseCode longestMetadataEntry(long entryLong) {
            return new ResponseCode("METADATA LONGENTRIES", entryLong);
        }
        
        /**
         * Create a RFC4551 <code>NOMODSEQ</code> response code
         * 
         * @return <code>ResponseCode</code>
         */
        public static ResponseCode noModSeq() {
            return new ResponseCode("NOMODSEQ");
        }


        
        /**
         * Creates an extension response code. Names that do not begin with 'X'
         * will have 'X' prepended
         * 
         * @param name
         *            extension code, not null
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode createExtension(String name) {
            StringBuilder builder = new StringBuilder(name.length() + 2);
            if (!name.startsWith("X")) {
                builder.append('X');
            }
            builder.append(name);
            return new ResponseCode(builder.toString());
        }

        public static final int NO_NUMBER = -1;
        
        private final String code;

        private final Collection<String> parameters;

        private final long number;

        private final boolean useParens;

        private ResponseCode(String code) {
            this(code, Collections.<String>emptyList(), NO_NUMBER, true);
        }

        private ResponseCode(String code, long number) {
            this(code, Collections.<String>emptyList(), number, true);
        }

        private ResponseCode(String code, Collection<String> parameters) {
            this(code, parameters, NO_NUMBER, true);
        }

        private ResponseCode(String code, Collection<String> parameters, long number, boolean useParens) {
            super();
            this.useParens = useParens;
            this.code = code;
            this.parameters = parameters;
            this.number = number;
        }

        public String getCode() {
            return code;
        }

       
        /**
         * Gets number for this response.
         * 
         * @return the number, or zero if no number has been set
         */
        public long getNumber() {
            return number;
        }

        public boolean useParens() {
            return useParens;
        }

        /**
         * Gets parameters for this code.
         * 
         * @return the parameters <code>Collection</code> of <code>String</code>
         *         parameters, not null
         */
        public Collection<String> getParameters() {
            return parameters;
        }

        public int hashCode() {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + ((code == null) ? 0 : code.hashCode());
            result = PRIME * result + (int) (number ^ (number >>> 32));
            result = PRIME * result + ((parameters == null) ? 0 : parameters.hashCode());
            return result;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ResponseCode other = (ResponseCode) obj;
            if (code == null) {
                if (other.code != null) {
                    return false;
                }
            } else if (!code.equals(other.code)) {
                return false;
            }
            if (number != other.number) {
                return false;
            }
            if (parameters == null) {
                if (other.parameters != null) {
                    return false;
                }
            } else if (!parameters.equals(other.parameters)) {
                return false;
            }
            return true;
        }

        public String toString() {
            return code;
        }
    }
}
