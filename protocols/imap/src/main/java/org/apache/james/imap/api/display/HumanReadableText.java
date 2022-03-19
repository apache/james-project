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

package org.apache.james.imap.api.display;

import java.util.Arrays;

import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.mailbox.MessageSequenceNumber;

import com.google.common.base.Joiner;

/**
 * Keys human response text that may be displayed to the user.
 */
public class HumanReadableText {

    public static final HumanReadableText STARTTLS = new HumanReadableText("org.apache.james.imap.STARTTLS", "Begin TLS negotiation now.");

    public static final HumanReadableText SELECT = new HumanReadableText("org.apache.james.imap.SELECT", "completed.");

    public static HumanReadableText unseen(MessageSequenceNumber numberUnseen) {
        return new HumanReadableText("org.apache.james.imap.UNSEEN", "MailboxMessage " + numberUnseen.asInt() + " is first unseen");
    }

    public static final HumanReadableText UIDNEXT = new HumanReadableText("org.apache.james.imap.UIDNEXT", "Predicted next UID");
    
    public static final HumanReadableText HIGHEST_MOD_SEQ = new HumanReadableText("org.apache.james.imap.HIGHEST_MOD_SEQ", "Highest");
    public static final HumanReadableText NO_MOD_SEQ = new HumanReadableText("org.apache.james.imap.NO_MOD_SEQ", "Sorry, this mailbox format doesn't support modsequences");

    public static final HumanReadableText UID_VALIDITY = new HumanReadableText("org.apache.james.imap.UID_VALIDITY", "UIDs valid");
    public static final HumanReadableText OK = new HumanReadableText("org.apache.james.imap.Ok", "Ok");

    public static HumanReadableText permanentFlags(Flags flags) {
        String text;
        if (flags.getSystemFlags() != null && flags.getSystemFlags().length > 0) {
            text = "Limited";
        } else {
            text = "No permanent flags permitted";
        }
        return new HumanReadableText("org.apache.james.imap.PERMANENT_FLAGS", text);
    }

    public static final HumanReadableText GENERIC_LSUB_FAILURE = new HumanReadableText("org.apache.james.imap.GENERIC_SUBSCRIPTION_FAILURE", "Cannot list subscriptions.");

    public static final HumanReadableText GENERIC_UNSUBSCRIPTION_FAILURE = new HumanReadableText("org.apache.james.imap.GENERIC_SUBSCRIPTION_FAILURE", "Cannot unsubscribe.");

    public static final HumanReadableText GENERIC_SUBSCRIPTION_FAILURE = new HumanReadableText("org.apache.james.imap.GENERIC_SUBSCRIPTION_FAILURE", "Cannot subscribe.");

    public static final HumanReadableText INVALID_MESSAGESET = new HumanReadableText("org.apache.james.imap.INVALID_MESSAGESET", "failed. Invalid messageset.");

    public static final HumanReadableText INVALID_COMMAND = new HumanReadableText("org.apache.james.imap.INVALID_COMMAND", "failed. Command not valid in this state.");
   
    public static final HumanReadableText INVALID_SYSTEM_FLAG = new HumanReadableText("org.apache.james.imap.INVALID_SYSTEM_FLAG", "Invalid system flag \\RECENT.");

    public static final HumanReadableText ILLEGAL_TAG = new HumanReadableText("org.apache.james.imap.ILLEGAL_TAG", "Illegal tag.");

    public static final HumanReadableText FAILURE_EXISTS_COUNT = new HumanReadableText("org.apache.james.imap.FAILURE_EXISTS_COUNT", "Cannot count number of existing records.");

    public static final HumanReadableText FAILURE_TO_LOAD_FLAGS = new HumanReadableText("org.apache.james.imap.FAILURE_TO_LOAD_FLAGS", "Failed to retrieve flags data.");

    public static final HumanReadableText ILLEGAL_ARGUMENTS = new HumanReadableText("org.apache.james.imap.ILLEGAL_ARGUMENTS", "failed. Illegal arguments.");

    public static final HumanReadableText FAILURE_MAILBOX_NAME = new HumanReadableText("org.apache.james.imap.ILLEGAL_ARGUMENTS", "too long mailbox name. Illegal arguments.");

    public static final HumanReadableText FAILURE_MAIL_PARSE = new HumanReadableText("org.apache.james.imap.FAILURE_MAIL_PARSE", "failed. Mail cannot be parsed.");

    public static final HumanReadableText FAILURE_NO_SUCH_MAILBOX = new HumanReadableText("org.apache.james.imap.FAILURE_NO_SUCH_MAILBOX", "failed. No such mailbox.");

    public static final HumanReadableText FAILURE_OVERQUOTA = new HumanReadableText("org.apache.james.imap.OVERQUOTA", "failed. Over quota.");

    public static final HumanReadableText FAILURE_NO_QUOTA_RESOURCE = new HumanReadableText("org.apache.james.imap.FAILURE_NO_SUCH_QUOTA_RESOURCE", "failed. No such quota resource.");

    public static final HumanReadableText START_TRANSACTION_FAILED = new HumanReadableText("org.apache.james.imap.START_TRANSACTION_FAILED", "failed. Cannot start transaction.");

    public static final HumanReadableText COMMIT_TRANSACTION_FAILED = new HumanReadableText("org.apache.james.imap.COMMIT_TRANSACTION_FAILED", "failed. Transaction commit failed.");

    public static final HumanReadableText DELETED_FAILED = new HumanReadableText("org.apache.james.imap.DELETED_FAILED", "failed. Deletion failed.");

    public static final HumanReadableText SEARCH_FAILED = new HumanReadableText("org.apache.james.imap.SEARCH_FAILED", "failed. Search failed.");

    public static final HumanReadableText STATUS_FAILED = new HumanReadableText("org.apache.james.imap.STATUS_FAILED", "failed. Status failed.");

    public static final HumanReadableText COUNT_FAILED = new HumanReadableText("org.apache.james.imap.COUNT_FAILED", "failed. Count failed.");

    public static final HumanReadableText SAVE_FAILED = new HumanReadableText("org.apache.james.imap.SAVE_FAILED", "failed. Save failed.");

    public static final HumanReadableText FAILED = new HumanReadableText("org.apache.james.imap.SAVE_FAILED", "failed.");

    public static final HumanReadableText UNSUPPORTED_SEARCH = new HumanReadableText("org.apache.james.imap.UNSUPPORTED_SEARCH", "failed. Unsupported search.");

    public static final HumanReadableText LOCK_FAILED = new HumanReadableText("org.apache.james.imap.LOCK_FAILED", "failed. Failed to lock mailbox.");

    public static final HumanReadableText UNSUPPORTED = new HumanReadableText("org.apache.james.imap.UNSUPPORTED", "failed. Unsupported operation.");

    public static final HumanReadableText DUPLICATE_MAILBOXES = new HumanReadableText("org.apache.james.imap.DUPLICATE_MAILBOXES", "failed. Expected unique mailbox but duplicate exists.");

    public static final HumanReadableText MAILBOX_EXISTS = new HumanReadableText("org.apache.james.imap.MAILBOX_EXISTS", "failed. Mailbox already exists.");

    public static final HumanReadableText MAILBOX_NOT_FOUND = new HumanReadableText("org.apache.james.imap.MAILBOX_NOT_FOUND", "failed. Mailbox not found.");

    public static final HumanReadableText MAILBOX_DELETED = new HumanReadableText("org.apache.james.imap.MAILBOX_DELETED", "failed. Mailbox has been deleted.");

    public static final HumanReadableText COMSUME_UID_FAILED = new HumanReadableText("org.apache.james.imap.COMSUME_UID_FAILED", "failed. Failed to acquire UID.");

    public static final HumanReadableText USER_DOES_NOT_EXIST = new HumanReadableText("org.apache.james.imap.GENERIC_FAILURE_DURING_PROCESSING", "User does not exist");

    public static final HumanReadableText DELEGATION_FORBIDDEN = new HumanReadableText("org.apache.james.imap.GENERIC_FAILURE_DURING_PROCESSING", "Delegation is forbidden.");

    public static final HumanReadableText GENERIC_FAILURE_DURING_PROCESSING = new HumanReadableText("org.apache.james.imap.GENERIC_FAILURE_DURING_PROCESSING", "processing failed.");

    public static final HumanReadableText FAILURE_MAILBOX_EXISTS = new HumanReadableText("org.apache.james.imap.FAILURE_NO_SUCH_MAILBOX", "failed. Mailbox already exists.");

    public static final HumanReadableText INIT_FAILED = new HumanReadableText("org.apache.james.imap.INIT_FAILED", "failed. Cannot initialise.");

    public static final HumanReadableText SOCKET_IO_FAILURE = new HumanReadableText("org.apache.james.imap.SOCKET_IO_FAILURE", "failed. IO failure.");

    public static final HumanReadableText BAD_IO_ENCODING = new HumanReadableText("org.apache.james.imap.BAD_IO_ENCODING", "failed. Illegal encoding.");
    public static final HumanReadableText COMPLETED = new HumanReadableText("org.apache.james.imap.COMPLETED", "completed.");
    public static final HumanReadableText REPLACE_READY = new HumanReadableText("org.apache.james.imap.REPLACE", "Replacement Message ready");

    public static final HumanReadableText INVALID_LOGIN = new HumanReadableText("org.apache.james.imap.INVALID_LOGIN", "failed. Invalid login/password.");

    public static final HumanReadableText DISABLED_LOGIN = new HumanReadableText("org.apache.james.imap.DISABLED_LOGIN", "failed. Plain login / authentication are disabled.");

    
    public static final HumanReadableText UNSUPPORTED_SEARCH_CRITERIA = new HumanReadableText("org.apache.james.imap.UNSUPPORTED_CRITERIA", "failed. One or more search criteria is unsupported.");

    public static final HumanReadableText UNSUPPORTED_AUTHENTICATION_MECHANISM = new HumanReadableText("org.apache.james.imap.UNSUPPORTED_AUTHENTICATION_MECHANISM", "failed. Authentication mechanism is unsupported.");
    public static final HumanReadableText AUTHENTICATION_FAILED = new HumanReadableText("org.apache.james.imap.AUTHENTICATION_FAILED", "failed. Authentication failed.");

    public static final HumanReadableText UNKNOWN_COMMAND = new HumanReadableText("org.apache.james.imap.UNKNOWN_COMMAND", "failed. Unknown command.");

    public static final HumanReadableText BAD_CHARSET = new HumanReadableText("org.apache.james.imap.BAD_CHARSET", "failed. Charset is unsupported.");

    public static final HumanReadableText MAILBOX_IS_READ_ONLY = new HumanReadableText("org.apache.james.imap.MAILBOX_IS_READ_ONLY", "failed. Mailbox is read only.");

    public static final HumanReadableText BYE = new HumanReadableText("org.apache.james.imap.BYE", ImapConstants.VERSION + " Server logging out");

    public static final HumanReadableText TOO_MANY_FAILURES = new HumanReadableText("org.apache.james.imap.TOO_MANY_FAILURES", "Login failed too many times.");

    public static final HumanReadableText BYE_UNKNOWN_COMMAND = new HumanReadableText("org.apache.james.imap.BYE_UNKNOWN_COMMAND", "Unknown command.");

    public static final HumanReadableText IDLING = new HumanReadableText("org.apache.james.imap.IDLING", "Idling");
    public static final HumanReadableText HEARTBEAT = new HumanReadableText("org.apache.james.imap.HEARTBEAT", "Still here");

    public static final HumanReadableText DEFLATE_ACTIVE = new HumanReadableText("org.apache.james.imap.DEFLATE", "DEFLATE active");

    public static final HumanReadableText COMPRESS_ALREADY_ACTIVE = new HumanReadableText("org.apache.james.imap.DEFLATE", "already active");

    public static final HumanReadableText UNSELECT = new HumanReadableText("org.apache.james.imap.UNSELECT", "No Mailbox selected.");
    
    public static final HumanReadableText QRESYNC_NOT_ENABLED = new HumanReadableText("org.apache.james.imap.QRESYNC_NOT_ENABLED", "QRESYNC not enabled.");
    public static final HumanReadableText QRESYNC_UIDVALIDITY_MISMATCH = new HumanReadableText("org.apache.james.imap.QRESYNC_UIDVALIDITY_MISMATCH", "Sorry, UIDVALIDITY mismatch.");
    public static final HumanReadableText QRESYNC_CLOSED = new HumanReadableText("org.apache.james.imap.QRESYNC_CLOSED", "");
    public static final HumanReadableText QRESYNC_VANISHED_WITHOUT_CHANGEDSINCE = new HumanReadableText("org.apache.james.imap.QRESYNC_VANISHED_WITHOUT_CHANGEDSINCE", "VANISHED used without CHANGEDSINCE");

    public static final HumanReadableText DENIED_SHARED_MAILBOX = new HumanReadableText("org.apache.james.imap.DENIED_SHARED_MAILBOX", "You can not access a mailbox that does not belong to you");

    public static final String UNSUFFICIENT_RIGHTS_DEFAULT_VALUE = "You need the {0} right to perform command {1} on mailbox {2}.";
    public static final String UNSUFFICIENT_RIGHTS_KEY = "org.apache.james.imap.UNSUFFICIENT_RIGHTS";

    public static final String UNSUPPORTED_RIGHT_KEY = "org.apache.james.imap.UNSUPPORTED_RIGHT";
    public static final String UNSUPPORTED_RIGHT_DEFAULT_VALUE = "The {0} right is not supported.";

    public static final String UNDEFINED_QUOTA_ROOT_KEY = "org.apache.james.imap.UNDEFINED_QUOTA_ROOT_KEY";
    public static final String UNDEFINED_QUOTA_ROOT_DEFAULT_VALUE = "The Quota Root {0} does not exist.";

    public static final String MAILBOX_ANNOTATION_KEY = "org.apache.james.imap.ANNOTATION_ERROR_KEY";

    private final String defaultValue;

    private final String key;

    private final Object[] parameters;

    public HumanReadableText(String key, String defaultValue) {
        this(key, defaultValue, (Object[]) null);
    }

    public HumanReadableText(String key, String defaultValue, Object... parameters) {
        super();
        this.defaultValue = defaultValue;
        this.key = key;
        this.parameters = parameters;
    }

    /**
     * Gets the default value for this text.
     * 
     * @return default human readable text, not null
     */
    public final String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets a unique key that can be used to loopup the text. How this is
     * performed is implementation independent.
     * 
     * @return key value, not null
     */
    public final String getKey() {
        return key;
    }

    /**
     * Gets parameters that may be substituted into the text.
     * 
     * @return substitution parameters, possibly null
     */
    public Object[] getParameters() {
        return parameters;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((defaultValue == null) ? 0 : defaultValue.hashCode());
        result = PRIME * result + ((key == null) ? 0 : key.hashCode());
        result = PRIME * result + Arrays.hashCode(parameters);
        return result;
    }

    @Override
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
        final HumanReadableText other = (HumanReadableText) obj;
        if (defaultValue == null) {
            if (other.defaultValue != null) {
                return false;
            }
        } else if (!defaultValue.equals(other.defaultValue)) {
            return false;
        }
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if (!Arrays.equals(parameters, other.parameters)) {
            return false;
        }
        return true;
    }

    public String toString() {
        return defaultValue;
    }

    public String asString() {
        return key + " " + defaultValue + "[" + Joiner.on(", ").join(parameters) + "]";
    }
}
