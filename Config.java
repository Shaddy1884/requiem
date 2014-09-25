import java.io.*;
import java.util.*;
import java.util.logging.*;

public class Config {
  private static final Logger log = Logger.getLogger(Config.class.getName());
  public static byte[] macAddress() {
    if (System.getProperty("requiem.mac") == null) {
      byte[] mac = config.macAddress();
      String macstr = String.format("%02x%02x%02x%02x%02x%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
      log.info("mac: " + macstr);
      System.setProperty("requiem.mac", macstr);
    }
    byte[] mac = new byte[6];
    for (int i = 0; i < 6; i++) mac[i] = (byte)Integer.parseInt(System.getProperty("requiem.mac").substring(2*i, 2*i+2), 16);
    return mac;
  }
  public static String keyStoreDirectory() {
    if (System.getProperty("requiem.keystore") == null) {
      System.setProperty("requiem.keystore", config.keyStoreDirectory());
      log.info("keyStoreDirectory: " + System.getProperty("requiem.keystore"));
    }
    return System.getProperty("requiem.keystore");
  }
  public static String iTunesLibrary() {
    if (System.getProperty("requiem.ituneslibrary") == null) {
      System.setProperty("requiem.ituneslibrary", config.iTunesLibrary());
      log.info("iTunesLibrary: " + System.getProperty("requiem.ituneslibrary"));
    }
    return System.getProperty("requiem.ituneslibrary");
  }
  public static void trash(File f) throws FileNotFoundException { config.trash(f); }
  public static boolean iTunesIsRunning() throws IOException { return config.iTunesIsRunning(); }
  public static void guiMods() { config.guiMods(); }
  public static boolean isShiftDown() { return config.isShiftDown(); }
  
  private static BaseConfig config;
  static {
    String os = System.getProperty("os.name");
    String classname;
    if (os.equals("Mac OS X")) classname = "MacConfig";
    else if (os.startsWith("Windows ")) classname = "WindowsConfig";
    else throw new RuntimeException("unknown os: " + os);
    try {
      config = (BaseConfig)Class.forName(classname).newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  abstract static class BaseConfig {
    abstract byte[] macAddress();
    abstract String keyStoreDirectory();
    abstract String iTunesLibrary();
    abstract void trash(File f) throws FileNotFoundException;
    abstract boolean iTunesIsRunning() throws IOException;
    abstract void guiMods();
    abstract boolean isShiftDown();
  }

  public static void main(String[] args) throws Exception {
    macAddress();
    keyStoreDirectory();
    iTunesLibrary();
    if (args.length > 0) {
      trash(new File(args[0]));
    }
  }
}
