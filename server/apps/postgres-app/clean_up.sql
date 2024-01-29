-- This is a script to delete old rows from some tables. One of the attempts to clean up the never-used data after a long time.

DO
$$
    DECLARE
        days_to_keep INTEGER;
    BEGIN
        -- Set the number of days dynamically
        days_to_keep := 60;

        -- Delete rows older than the specified number of days in email_change
        DELETE
        FROM email_change
        WHERE date < current_timestamp - interval '1 day' * days_to_keep;

        -- Delete rows older than the specified number of days in mailbox_change
        DELETE
        FROM email_change
        WHERE date < current_timestamp - interval '1 day' * days_to_keep;
    END
$$;