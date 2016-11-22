package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.net.UnknownHostException;


public class Client {
  private static final int SERVER_PORT = 8080;

  private String username;
  private Socket sock;

  private PrintWriter out;
  private BufferedReader in;
  private BufferedInputStream binIn;
  private BufferedOutputStream binOut;

  private Thread dumper;

  public Client(String proposedUsername, String serverAddr) {
    String connectionStr = "CONNECT: " + proposedUsername;

    try {
      this.sock = new Socket(serverAddr, SERVER_PORT);

      InputStreamReader is = new InputStreamReader(this.sock.getInputStream());
      this.in = new BufferedReader(is);
      this.binIn = new BufferedInputStream(this.sock.getInputStream());

      this.out = new PrintWriter(this.sock.getOutputStream());
      this.binOut = new BufferedOutputStream(this.sock.getOutputStream());

      this.dumper = new Thread(new Dumper(this.in));
      this.dumper.start();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      this.out.println(connectionStr);
      this.out.flush();
      String ret = this.in.readLine();
      if (ret.startsWith("Logged in")) {
        this.username = ret.split(":", 2)[1];
      }
      System.out.println(ret);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class Dumper implements Runnable {
    private BufferedReader in;

    public Dumper(BufferedReader in) {
      this.in = in;
    }

    @Override
    public void run() {
      System.out.println("Dumper Starter!");
      String res;
      try {
        while ((res = in.readLine()) != null) {
          System.out.println(res);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void listenInp() {
    String inp = null;
    BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    try {
      while ((inp = stdin.readLine()) != null && inp.compareTo("quit") != 0) {
        this.out.println(inp);
        this.out.flush();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println("Please enter Username, Server Address as arguments");
    }

    Client c = new Client(args[0], args[1]);
    c.listenInp();
  }
}
