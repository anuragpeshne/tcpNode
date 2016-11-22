package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Arrays;

public class Server {
  private static final int PORT = 8080;
  private static final int BUFFER_SIZE = 4096;
  private ConcurrentHashMap<String, Socket> clientMapping;

  private class ClientHandler implements Runnable {
    BufferedReader in;
    PrintWriter out;

    BufferedOutputStream outBin;
    BufferedInputStream inBin;

    Socket sock;
    Boolean loggedIn;
    ConcurrentHashMap<String, Socket> clientMapping;
    String username;

    public ClientHandler(Socket clientSocket,
                         ConcurrentHashMap<String, Socket> clientMapping) {
      this.sock = clientSocket;
      this.loggedIn = false;
      this.clientMapping = clientMapping;

      try {
        this.in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        this.out = new PrintWriter(sock.getOutputStream());

        this.inBin = new BufferedInputStream(sock.getInputStream());
        this.outBin = new BufferedOutputStream(sock.getOutputStream());
      } catch (IOException e) {
        e.printStackTrace();
      }
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
                blockList = new ArrayList<String>(Arrays.asList(blocks));
              }

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
        // clean up client here
        this.clientMapping.remove(this.username);
      } catch (java.net.SocketException e) {
        this.broadcast(this.username + " dropped");
        System.out.println(this.username + " dropped");

        this.clientMapping.remove(this.username);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private Boolean unicast(String target, String msg, Boolean isFile) {
      ArrayList<String> targetWrapper = new ArrayList<String>();
      targetWrapper.add(target);
      return this.multicast(targetWrapper, msg, isFile);
    }

    private Boolean multicast(ArrayList<String> targets, String msg, Boolean isFile) {
      ArrayList<Socket> targetSocks = this.getClientSocks(targets);
      if (targetSocks.size() == 0) {
        this.out.println("User not found");
        this.out.flush();
        return false;
      }
      for (Socket targetSock: targetSocks) {
        try {
          if (isFile) {
            byte[] buffer = new byte[BUFFER_SIZE];
            BufferedOutputStream outStream = new BufferedOutputStream(targetSock.getOutputStream());
            for (int read = this.inBin.read(buffer);
                 read >= 0;
                 read = this.inBin.read(buffer)) {
              outStream.write(buffer, 0, read);
            }
          } else {
            PrintWriter targetOut = new PrintWriter(targetSock.getOutputStream());
            targetOut.println(this.username + ": " + msg);
            targetOut.flush();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
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
      clientMapping.put(this.username, this.sock);
      this.loggedIn = true;
      out.println("Logged in using username:" + this.username);
      out.flush();
      this.broadcast(" joined");
      return true;
    }

    private ArrayList<Socket> getClientSocks(ArrayList<String> usernames) {
      if (usernames.size() == 0) {
        return new ArrayList<Socket>(this.clientMapping.values());
      } else {
        ArrayList<Socket> clientSocks = new ArrayList<Socket>();
        for (String username: usernames) {
          Socket userSock = this.clientMapping.get(username);
          if (userSock != null) {
            clientSocks.add(userSock);
          }
        }
        return clientSocks;
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
    this.clientMapping = new ConcurrentHashMap<String, Socket>();
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
