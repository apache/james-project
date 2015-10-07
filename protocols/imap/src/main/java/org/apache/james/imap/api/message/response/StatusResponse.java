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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.MessageFlags;

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

    /**
     * Gets the server response type of this status message.
     * 
     * @return The type, either {@link Type#BAD}, {@link Type#BYE}, {@link Type#NO},
     *         {@link Type#OK} or {@link Type#PREAUTH}
     */
    public Type getServerResponseType();

    /**
     * Gets the tag.
     * 
     * @return if tagged response, the tag. Otherwise null.
     */
    public String getTag();

    /**
     * Gets the command.
     * 
     * @return if tagged response, the command. Otherwise null
     */
    public ImapCommand getCommand();

    /**
     * Gets the key to the human readable text to be displayed. Required.
     * 
     * @return key for the text message to be displayed, not null
     */
    public HumanReadableText getTextKey();

    /**
     * Gets the response code. Optional.
     * 
     * @return <code>ResponseCode</code>, or null if there is no response code
     */
    public ResponseCode getResponseCode();

    /**
     * Enumerates types of RC2060 status response
     */
    public enum Type {
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

        private Type(final String code) {
            this.code = code;
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
    public static final class ResponseCode {

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

        /** RFC5162 <code>CLOSED</code> response code */
        private static final ResponseCode CLOSED = new ResponseCode("CLOSED");

        
        /** RFC4315 <code>APPENDUID</code> response code */
        public static ResponseCode appendUid(long uidValidity, IdRange[] uids) {
            String uidParam = formatRanges(uids);
            return new ResponseCode("APPENDUID", Arrays.asList(uidParam), uidValidity, false);
        }

        /** RFC4315 <code>COPYUID</code> response code */
        public static ResponseCode copyUid(long uidValidity, IdRange[] sourceRanges, IdRange[] targetRanges) {
            String source = formatRanges(sourceRanges);
            String target = formatRanges(targetRanges);

            return new ResponseCode("COPYUID", Arrays.asList(new String[] { source, target }), uidValidity, false);
        }

        /** RFC4551 <code>Conditional STORE</code> response code */
        public static ResponseCode condStore(IdRange[] failedRanges) {
            String failed = formatRanges(failedRanges);

            return new ResponseCode("MODIFIED", Arrays.asList(new String[] { failed}), 0, false);
        }
        
        private static String formatRanges(IdRange[] ranges) {
            if (ranges == null || ranges.length == 0)
                return "*";
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
         * @param charsetNames
         *            <code>Collection<String></code> containing charset names
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode badCharset(Collection<String> charsetNames) {
            return new ResponseCode("BADCHARSET", charsetNames);
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

        /**
         * Creates a RFC2060 <code>UIDVALIDITY</code> response code.
         * 
         * @param uid
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode uidValidity(long uid) {
            return new ResponseCode("UIDVALIDITY", uid);
        }

        /**
         * Creates a RFC2060 <code>UNSEEN</code> response code.
         * 
         * @param numberUnseen
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode unseen(int numberUnseen) {
            return new ResponseCode("UNSEEN", numberUnseen);
        }

        /**
         * Creates a RFC2060 <code>UIDNEXT</code> response code.
         * 
         * @param uid
         *            positive non-zero integer
         * @return <code>ResponseCode</code>, not null
         */
        public static ResponseCode uidNext(long uid) {
            return new ResponseCode("UIDNEXT", uid);
        }

        
        /**
         * Create a RFC4551 <code>HIGESTMODSEQ</code> response code
         * 
         * @param modSeq positive non-zero long
         * @return <code>ResponseCode</code>
         */
        public static ResponseCode highestModSeq(long modSeq) {
            return new ResponseCode("HIGHESTMODSEQ", modSeq);
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
            StringBuffer buffer = new StringBuffer(name.length() + 2);
            if (!name.startsWith("X")) {
                buffer.append('X');
            }
            buffer.append(name);
            final ResponseCode result = new ResponseCode(buffer.toString());
            return result;
        }

        public final static int NO_NUMBER = -1;
        
        private final String code;

        private final Collection<String> parameters;

        private final long number;

        private final boolean useParens;

        @SuppressWarnings("unchecked")
        private ResponseCode(final String code) {
            this(code, Collections.EMPTY_LIST, NO_NUMBER, true);
        }

        @SuppressWarnings("unchecked")
        private ResponseCode(final String code, final long number) {
            this(code, Collections.EMPTY_LIST, number, true);
        }

        private ResponseCode(final String code, final Collection<String> parameters) {
            this(code, parameters, NO_NUMBER, true);
        }

        private ResponseCode(final String code, final Collection<String> parameters, final long number, final boolean useParens) {
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
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ResponseCode other = (ResponseCode) obj;
            if (code == null) {
                if (other.code != null)
                    return false;
            } else if (!code.equals(other.code))
                return false;
            if (number != other.number)
                return false;
            if (parameters == null) {
                if (other.parameters != null)
                    return false;
            } else if (!parameters.equals(other.parameters))
                return false;
            return true;
        }

        public String toString() {
            return code;
        }
    }
}
