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
