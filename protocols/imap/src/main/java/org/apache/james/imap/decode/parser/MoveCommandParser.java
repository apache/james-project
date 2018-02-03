package org.apache.james.imap.decode.parser;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.message.request.MoveRequest;

/**
 * Parse MOVE commands
 */
public class MoveCommandParser extends AbstractMessageRangeCommandParser {

    public MoveCommandParser() {
        super(ImapCommand.selectedStateCommand(ImapConstants.MOVE_COMMAND_NAME));
    }

    @Override
    protected MoveRequest createRequest(ImapCommand command, String tag,
            boolean useUids, IdRange[] idSet, String mailboxName) {
        return new MoveRequest(command, idSet, mailboxName, useUids, tag);
    }

}
