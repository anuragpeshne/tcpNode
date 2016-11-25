package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.net.Socket;


public class Client {
  private static final int SERVER_PORT = 8080;
  private static final int BUFFER_SIZE = 4096;

  private String username;
  private Socket sock;

  private PrintWriter out;
  private BufferedReader in;
  private BufferedInputStream inBin;
  private BufferedOutputStream outBin;

  private Thread dumper;

  public Client(String proposedUsername, String serverAddr) {
    String connectionStr = "CONNECT: " + proposedUsername;

    try {
      this.sock = new Socket(serverAddr, SERVER_PORT);

      InputStreamReader is = new InputStreamReader(this.sock.getInputStream());
      this.in = new BufferedReader(is);
      this.inBin = new BufferedInputStream(this.sock.getInputStream());

      this.out = new PrintWriter(this.sock.getOutputStream());
      this.outBin = new BufferedOutputStream(this.sock.getOutputStream());

      this.dumper = new Thread(new Dumper(this.in, this.inBin));
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
    private BufferedInputStream inBin;

    public Dumper(BufferedReader in, BufferedInputStream inBin) {
      this.in = in;
      this.inBin = inBin;
    }

    @Override
    public void run() {
      System.out.println("Dumper Started!");
      String res;
      try {
        while ((res = in.readLine()) != null) {
          if (res.contains("file:")) {
            String[] resList = res.split(": ?", 2);
            String filePath = resList[1].split(": ?", 2)[1];
            System.out.println("Incoming file " + filePath + " from user " + resList[0]);
            /* TODO: make user directories */
            File newFile = new File(filePath);
            byte[] buffer = new byte[BUFFER_SIZE];
            BufferedOutputStream outStream =
              new BufferedOutputStream(new FileOutputStream(newFile));
            for (int read = this.inBin.read(buffer);
                 read >= 0;
                 read = this.inBin.read(buffer)) {
              outStream.write(buffer, 0, read);
            }
            System.out.println("File saved at " + newFile.getAbsolutePath());
          } else {
            System.out.println(res);
            System.out.flush();
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void listenInp() {
    String inp = null;
    try {
      while ((inp = System.console().readLine()) != null) {
        if (inp.contains("file:")) {
          String filePath = inp.split(": ?", 2)[1].split(": ?", 2)[1];
          File inpFile = new File(filePath);
          if (inpFile.exists()) {
            this.out.println(inp);
            this.out.flush();

            System.out.println("Sending file...");
            BufferedInputStream bis =
              new BufferedInputStream(new FileInputStream(inpFile));

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int read = bis.read(buffer); read >= 0; read = bis.read(buffer)) {
              this.outBin.write(buffer);
            }
            this.outBin.flush();
            System.out.println("Transfer finished");
          } else if (inpFile.isDirectory()) {
            System.out.println("Directory not supported: " + inpFile.getAbsolutePath());
          } else {
            System.out.println("File not found: " + inpFile.getAbsolutePath());
          }
        } else {
          this.out.println(inp);
          this.out.flush();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        this.in.close();
        this.out.close();
        this.inBin.close();
        this.outBin.close();
        this.sock.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
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
