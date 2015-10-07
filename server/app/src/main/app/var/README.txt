The $JAMES_ROOT/var folder is the place where James Server writes and reads the
files it needs to achieve its functions.

There should be two folders in var:

  * mail
  
      This folder does NOT contain the users mails
      (look at store folder (or your external database) to find the mailboxes).
      The mail foldercontains the mails rejected during the spooling 
      (depending on mailetcontainer.xml configuration).
      
  * store
  
     This folder contains the files related to database, jcr, activemq,... needed by James.
     James Server default settings comes with a embedded Derby database that stores the 
     users, domains and mailboxes (the user mails) (see store/derby folder).
     Of course, if you changed database.properties and still use a database for the mailboxes,
     you will have to look for the users, domains and mailboxes in your database, whatever,
     wherever it is.
     You can find in store other folders such as store/activemq, store/maildir,
     store/jackrabbit,...

     
For some functions, James uses the system temporary folder (/tmp on linux).
The system temp can be populated with some temporary files, but James should remove
them after a short time.
