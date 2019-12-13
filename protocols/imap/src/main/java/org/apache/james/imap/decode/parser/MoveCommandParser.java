package org.apache.james.imap.decode.parser;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.message.request.MoveRequest;

/**
 * Parse MOVE commands
 */
public class MoveCommandParser extends AbstractMessageRangeCommandParser {
    public MoveCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.MOVE_COMMAND, statusResponseFactory);
    }

    @Override
    protected MoveRequest createRequest(Tag tag, boolean useUids, IdRange[] idSet, String mailboxName) {
        return new MoveRequest(idSet, mailboxName, useUids, tag);
    }
}
