# Multicast Chat App
- Multicast chat application
- Terminal based
- Uses java threads for concurrency

# What it does
- Users join multicast with a chosen name
- Allows for the sending and recieving of text based messages through multicast
- Users can see a live dynamic list of available users in the multicast chat
  - "/online"
  - Runs on a separate thread than the sender or listener
- Users can see a chat history
  - "/history"
  - Thread safe datastructure implementation with the use of locks
