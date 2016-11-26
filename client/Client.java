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
  private static final int BUFFER_SIZE = 16 * 1000;

  private String username;
  private File userDir;
  private Socket sock;

  private PrintWriter out;
  private BufferedReader in;
  private BufferedOutputStream outBin;

  private Dumper dumper;

  public Client(String proposedUsername, String serverAddr) {
    String connectionStr = "CONNECT: " + proposedUsername;

    try {
      this.sock = new Socket(serverAddr, SERVER_PORT);

      InputStreamReader is = new InputStreamReader(this.sock.getInputStream());
      this.in = new BufferedReader(is);

      this.out = new PrintWriter(this.sock.getOutputStream());
      this.outBin = new BufferedOutputStream(this.sock.getOutputStream());

    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      this.out.println(connectionStr);
      this.out.flush();
      String ret = this.in.readLine();
      if (ret.trim().startsWith("Logged")) {
        this.username = ret.split(":", 2)[1];
        this.userDir = new File(System.getProperty("user.home") + File.separator
                                + this.username);
        if (!this.userDir.exists()) {
          this.userDir.mkdir();
        }

        this.dumper = new Dumper(this.in,
                                 new BufferedInputStream(this.sock.getInputStream()),
                                 this.userDir);
        Thread dThread = new Thread(this.dumper);
        dThread.start();
      } else {
        System.out.println(ret);
        System.exit(0);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class Dumper implements Runnable {
    private BufferedReader in;
    private BufferedInputStream inBin;
    private File userDir;

    public Dumper(BufferedReader in, BufferedInputStream inBin, File userDir) {
      this.in = in;
      this.inBin = inBin;
      this.userDir = userDir;
    }

    @Override
    public void run() {
      System.out.println("Dumper Started!");
      String res;
      try {
        while ((res = in.readLine()) != null) {
          if (res.contains("file:")) {
            String[] resList = res.split(": ?", 2);
            String fileProp = resList[1].split(": ?", 2)[1];

            String[] pathList = fileProp.split(", ?")[0].trim().split(File.separator);
            String filePath = (this.userDir.getAbsolutePath() + File.separator +
                               pathList[pathList.length - 1]);
            int fileLen = Integer.parseInt(fileProp.split(", ?")[1].split(":")[1].trim());

            System.out.println("Incoming file " + filePath + " from user " + resList[0]);
            System.out.println("file len" + fileLen);
            System.out.flush();
            /* TODO: make user directories */
            File newFile = new File(filePath);
            byte[] buffer = new byte[BUFFER_SIZE];

            BufferedOutputStream outStream =
              new BufferedOutputStream(new FileOutputStream(newFile));
            for (int read = 0, totalRead = 0; totalRead < fileLen; totalRead += read) {
              if (BUFFER_SIZE < (fileLen - totalRead))
                read = this.inBin.read(buffer, 0, BUFFER_SIZE);
              else
                read = this.inBin.read(buffer, 0, (fileLen - totalRead));

              System.out.println("chunk got");
              System.out.println(read + " " + totalRead + " " + fileLen);
              System.out.flush();
              outStream.write(buffer, 0, read);
            }
            System.out.println("File saved at " + newFile.getAbsolutePath());
            System.out.flush();

            outStream.close();
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
          if (inpFile.exists() && inpFile.isFile()) {
            inp = inp + ", length: " + inpFile.length();
            this.out.println(inp);
            this.out.flush();

            System.out.println("Sending file...");
            BufferedInputStream bis =
              new BufferedInputStream(new FileInputStream(inpFile));

            byte[] buffer = new byte[BUFFER_SIZE];
            for (int read = bis.read(buffer); read >= 0; read = bis.read(buffer)) {
              this.outBin.write(buffer, 0, read);
              this.outBin.flush();
            }
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
