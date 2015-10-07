

package org.apache.james.mailbox.elasticsearch.json;

import javax.mail.Flags;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageUpdateJson {

    private final Flags flags;
    private final long modSeq;

    public MessageUpdateJson(Flags flags, long modSeq) {
        this.flags = flags;
        this.modSeq = modSeq;
    }

    @JsonProperty(JsonMessageConstants.IS_ANSWERED)
    public boolean isAnswered() {
        return flags.contains(Flags.Flag.ANSWERED);
    }

    @JsonProperty(JsonMessageConstants.IS_DELETED)
    public boolean isDeleted() {
        return flags.contains(Flags.Flag.DELETED);
    }

    @JsonProperty(JsonMessageConstants.IS_DRAFT)
    public boolean isDraft() {
        return flags.contains(Flags.Flag.DRAFT);
    }

    @JsonProperty(JsonMessageConstants.IS_FLAGGED)
    public boolean isFlagged() {
        return flags.contains(Flags.Flag.FLAGGED);
    }

    @JsonProperty(JsonMessageConstants.IS_RECENT)
    public boolean isRecent() {
        return flags.contains(Flags.Flag.RECENT);
    }

    @JsonProperty(JsonMessageConstants.IS_UNREAD)
    public boolean isUnRead() {
        return ! flags.contains(Flags.Flag.SEEN);
    }


    @JsonProperty(JsonMessageConstants.USER_FLAGS)
    public String[] getUserFlags() {
        return flags.getUserFlags();
    }

    @JsonProperty(JsonMessageConstants.MODSEQ)
    public long getModSeq() {
        return modSeq;
    }

}
