package org.apache.james.mailbox.jpa.mail;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.Username;

import java.util.Collection;
import java.util.List;

public class TransactionalAttachmentMapper implements AttachmentMapper {

    private final JPAAttachmentMapper attachmentMapper;

    public TransactionalAttachmentMapper(JPAAttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        return attachmentMapper.getAttachment(attachmentId);
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        return attachmentMapper.getAttachments(attachmentIds);
    }

    @Override
    public void storeAttachmentForOwner(Attachment attachment, Username owner) throws MailboxException {
        attachmentMapper.storeAttachmentForOwner(attachment, owner);
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException {
        attachmentMapper.storeAttachmentsForMessage(attachments, ownerMessageId);
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        return attachmentMapper.getRelatedMessageIds(attachmentId);
    }

    @Override
    public Collection<Username> getOwners(AttachmentId attachmentId) throws MailboxException {
        return attachmentMapper.getOwners(attachmentId);
    }

    @Override
    public void endRequest() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }
}
