package org.apache.james.vault.search;

public enum FieldName {
    DELETION_DATE("deletionDate"),
    DELIVERY_DATE("deliveryDate"),
    RECIPIENTS("recipients"),
    SENDER("sender"),
    HAS_ATTACHMENT("hasAttachment"),
    ORIGIN_MAILBOXES("originMailboxes"),
    SUBJECT("subject");

    private final String value;

    FieldName(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}