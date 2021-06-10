$JAMES_ROOT/var/mail

      !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      !!!!!! This folder does NOT contain the users mails (mailboxes). !!!!!! 
      !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

      Look at $JAMES_ROOT/var/store folder (or your external database) to find 
      the mailboxes.

      This folder contains the mails rejected during the spooling 
      (depending on mailetcontainer.xml configuration).
      
      Sub folders of var/mail can be:

      * address-error
      
      * error
      
      * relay-denied
      
      * spam
