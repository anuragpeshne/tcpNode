package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Arrays;

public class Server {
  private static final int PORT = 8080;
  private static final int BUFFER_SIZE = 4096;
  private ConcurrentHashMap<String, Client> clientMapping;

  private class Client {
    public Socket sock;
    public BufferedInputStream inBin;
    public BufferedOutputStream outBin;
    public PrintWriter out;
    public BufferedReader in;

    public Client(Socket sock) {
      try {
        this.sock = sock;
        this.in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        this.out = new PrintWriter(sock.getOutputStream());

        this.inBin = new BufferedInputStream(sock.getInputStream());
        this.outBin = new BufferedOutputStream(sock.getOutputStream());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private class ClientHandler implements Runnable {
    BufferedReader in;
    PrintWriter out;

    BufferedOutputStream outBin;
    BufferedInputStream inBin;

    Client client;
    Boolean loggedIn;
    ConcurrentHashMap<String, Client> clientMapping;
    String username;

    public ClientHandler(Socket sock,
                         ConcurrentHashMap<String, Client> clientMapping) {
      this.loggedIn = false;
      this.clientMapping = clientMapping;

      this.client = new Client(sock);

      this.in = this.client.in;
      this.out = this.client.out;
      this.inBin = this.client.inBin;
      this.outBin = this.client.outBin;
    }

    @Override
    public void run() {
      String inMsg, outMsg;
      try {
        /* *** Message Format
         * @user: hello there //unicast
         * @all: hello everyone //broadcast
         * @all -user1 -user2: hello subset of everyone //blockcast
         * @all/user: file: /path/to/file //can be used with everything above + file
         *
         *** Internal Messages
         * CONNECT: username //connects new socket with username
         * LISTCL //server sends back client list
         */
        while ((inMsg = in.readLine()) != null) {
          String[] req = inMsg.split(": ?", 2);

          if (!this.loggedIn && req[0].compareTo("CONNECT") == 0) {
            this.login(req[1]);
          } else if (!this.loggedIn) {
              this.warnClient("Not logged in");
          } else {
            if (req[0].compareTo("LIST") == 0) {
              ArrayList<String> clients = this.filterClients(new ArrayList<String>());
              for (String client: clients) {
                out.println(client);
              }
              out.flush();
            } else if (req[0].startsWith("@all")) { // broadcast
              ArrayList<String> blockList = new ArrayList<String>();

              if (req[0].length() > 4) { // probably contains blocked users
                String[] blocks = req[0].substring(4).trim().split("-");
                for (String blockUsername: blocks) {
                  if (blockUsername.trim().compareTo("") != 0)
                    blockList.add(blockUsername.trim());
                }
              }
              blockList.add(this.username);

              Boolean isFile;
              String msg;
              if (req[1].contains("file:")) {
                isFile = true;
                msg = req[1].trim().split(": ?", 2)[1];
              } else {
                isFile = false;
                msg = req[1].trim();
              }
              this.broadcast(blockList, msg, isFile);

            } else if (req[0].startsWith("@")) { //unicast
              Boolean isFile;
              String msg;
              if (req[1].contains("file:")) {
                isFile = true;
                msg = req[1].trim().split(": ?", 2)[1];
              } else {
                isFile = false;
                msg = req[1].trim();
              }
              this.unicast(req[0].substring(1).trim(), msg, isFile);

            } else {
              this.warnClient("Invalid Command");
            }
          }
        }
      } catch (java.net.SocketException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        // clean up client here
        this.clientMapping.remove(this.username);
        this.broadcast(this.username + " dropped");
        System.out.println(this.username + " dropped");
        try {
          this.in.close();
          this.out.close();
          this.inBin.close();
          this.outBin.close();
          this.client.sock.close();
        } catch (IOException e){
          e.printStackTrace();
        }
      }
    }

    private Boolean unicast(String target, String msg, Boolean isFile) {
      ArrayList<String> targetWrapper = new ArrayList<String>();
      targetWrapper.add(target);
      return this.multicast(targetWrapper, msg, isFile);
    }

    private Boolean multicast(ArrayList<String> targets, String msg, Boolean isFile) {
      ArrayList<Client> targetClients = this.getClients(targets);
      if (targetClients.size() == 0) {
        this.out.println("User not found");
        this.out.flush();
        return false;
      }

      try {
        if (isFile) {
          System.out.println("File detected");
          /* here msg looks /path/to/file, length: \d+ */
          int fileLen = Integer.parseInt(msg.split(", ?")[1].split(":")[1].trim());

          for (Client targetClient: targetClients) {
            targetClient.out.println(this.username + ": file: " + msg);
            targetClient.out.flush();
          }

          byte[] buffer = new byte[BUFFER_SIZE];
          for (int read = 0, totalRead = 0; totalRead < fileLen; totalRead += read) {
            if (BUFFER_SIZE < (fileLen - totalRead))
              read = this.inBin.read(buffer, 0, BUFFER_SIZE);
            else
              read = this.inBin.read(buffer, 0, (fileLen - totalRead));

            System.out.println("buffering file");
            System.out.println(read + ", " + totalRead + File.separator + fileLen);
            System.out.flush();

            for (Client targetClient: targetClients) {
              //byte[] bufferCopy = Arrays.copyOf(buffer, buffer.length);
              targetClient.outBin.write(buffer, 0, read);
              targetClient.outBin.flush();
            }
          }
          System.out.println("buffering file done");
          System.out.flush();
        } else {
          for (Client targetClient: targetClients) {
            targetClient.out.println(this.username + ": " + msg);
            targetClient.out.flush();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

      return true;
    }

    private Boolean broadcast(String msg) {
      ArrayList<String> emptyList = new ArrayList<String>();
      emptyList.add(this.username);
      return this.broadcast(emptyList, msg, false);
    }

    private Boolean broadcast(ArrayList<String> negList, String msg, Boolean isFile) {
      return this.multicast(this.filterClients(negList), msg, isFile);
    }

    private Boolean login(String username) {
      /* replace all nasty characters with '_' */
      String sanitizedUsername =
        username.replaceAll("[ !.,/\\\"':;\\[\\]{}@#$%^&*()-+=]", "_");

      if (this.clientMapping.containsKey(sanitizedUsername) ||
          sanitizedUsername.compareTo("all") == 0) {
        out.println("Another user with username:" + sanitizedUsername + " exists."
                    + "\nPick another username");
        out.flush();
        return false;
      }

      this.username = sanitizedUsername;
      clientMapping.put(this.username, this.client);
      this.loggedIn = true;
      out.println("Logged in using username:" + this.username);
      out.flush();
      this.broadcast(" joined");
      return true;
    }

    private ArrayList<Client> getClients(ArrayList<String> usernames) {
      if (usernames.size() == 0) {
        return new ArrayList<Client>(this.clientMapping.values());
      } else {
        ArrayList<Client> clients = new ArrayList<Client>();
        for (String username: usernames) {
          Client userClient = this.clientMapping.get(username);
          if (userClient != null) {
            clients.add(userClient);
          }
        }
        return clients;
      }
    }

    private void warnClient(String msg) {
      if (msg.isEmpty()) {
        this.out.println("Error!");
      } else {
        this.out.println(msg);
      }
      this.out.flush();
    }

    private ArrayList<String> filterClients(ArrayList<String> negList) {
      Enumeration<String> ec= this.clientMapping.keys();
      if (negList.size() == 0) {
        ArrayList<String> allClients = new ArrayList<String>();
        while (ec.hasMoreElements()) {
          allClients.add(ec.nextElement());
        }
        return allClients;
      } else {
        ArrayList<String> someClients = new ArrayList<String>();
        while (ec.hasMoreElements()) {
          String next = ec.nextElement();
          if (!negList.contains(next)) {
            someClients.add(next);
          }
        }
        return someClients;
      }
    }
  }

  public Server() {
    this.clientMapping = new ConcurrentHashMap<String, Client>();
  }

  public void serveForever() {
    ServerSocket listenSocket = null;

    try {
      listenSocket = new ServerSocket(PORT);
    } catch (IOException e) {
      e.printStackTrace();
    }

    while (true) {
      try {
        Socket clientSocket = listenSocket.accept();
        System.out.println("got connection@" + clientSocket.getRemoteSocketAddress());

        Thread clientHandlerThread =
          new Thread(new ClientHandler(clientSocket, this.clientMapping));
        clientHandlerThread.start();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    Server s = new Server();
    s.serveForever();
  }

}
