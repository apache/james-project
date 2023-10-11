CREATE TABLE IF NOT EXISTS subscription
(
    username varchar(255) not null,
    mailbox  varchar(500) not null,
    domain varchar(255),
    constraint usenrame_mailbox_pk unique (username, mailbox)
);

