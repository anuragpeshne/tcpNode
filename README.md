# tcpNode
simple chat app with file transfer

## Usage:
### Message Format
- @user: hello there //unicast
- @all: hello everyone //broadcast
- @all -user1 -user2: hello subset of everyone //blockcast
- @all/user: file: /path/to/file //can be used with everything above + file

### Internal Messages
- CONNECT: username //connects new socket with username
- LISTCL //server sends back client list

## Implementation Idea
### Server
- A new thread is spawned for each connection
- All connection data is kept in `ConcurrentHashMap`, which can be accessed using
  `username` as key. Value is struct like class which has `BufferedOutputstream`
  (`outBin`), `BufferedInputStream` (`inBin`), `PrintWriter` (`out`) and `BufferedReader`
  (`in`).
- Client Server communication is in simple text string. Messages are parsed to detect
  metadata such as recipent, type of message (broadcast, unicast, blockcast).
  - Everything is done on Server, clients are pretty dumb (except handling files).
    In fact, they can be emulated using simple `nc` command (which can do everything
    except file transfer).

### Client[s]
- Clients are dumb: they just forward the string input to server except for files.
- When client detect file is to be transfered, it takes in the file and sends it
  over `BufferedOutputStream`.
- Files received are saved under `~`, withing folder named after the client username.
