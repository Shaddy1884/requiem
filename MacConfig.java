import java.util.regex.*;
import java.util.logging.*;
import java.io.*;
import com.apple.eawt.*;

class MacConfig extends Config.BaseConfig {
  private static final Logger log = Logger.getLogger(MacConfig.class.getName());
  static {
    try {
      for (Handler h : Logger.getLogger("").getHandlers()) {
        h.setFormatter(new OneLineFormatter());
        h.setEncoding("UTF-8");
      }
      Handler h = new FileHandler(System.getProperty("user.home") + "/Library/Logs/Requiem.log");
      h.setFormatter(new OneLineFormatter());
      h.setEncoding("UTF-8");
      Logger.getLogger("").addHandler(h);
      log.info("os: " + System.getProperty("os.name"));
      log.info("jvm: " + System.getProperty("sun.arch.data.model") + " bit");
      
      File f = Util.extractTempResource("libNative" + System.getProperty("sun.arch.data.model") + ".dylib");
      System.load(f.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  byte[] macAddress() {
    try {
      Process p = Runtime.getRuntime().exec("/sbin/ifconfig en0");
      BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
      Pattern x = Pattern.compile("\\s*ether (\\w{2}):(\\w{2}):(\\w{2}):(\\w{2}):(\\w{2}):(\\w{2})\\s*");
      while (true) {
        String line = r.readLine();
        if (line == null) throw new RuntimeException("can't find mac address");
        Matcher m = x.matcher(line);
        if (m.matches()) {
          byte[] macAddress = new byte[6];
          for (int i = 0; i < 6; i++) {
            macAddress[i] = (byte)Integer.parseInt(m.group(i + 1), 16);
          }
          p.destroy();
          return macAddress;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  String keyStoreDirectory() {
    return "/Users/Shared/SC Info";
  }
  String iTunesLibrary() {
    // default location
    String directory = System.getProperty("user.home") + "/Music/iTunes";
    
    // Check config file for a non-default location
    String config;
    try {
      Process p = Runtime.getRuntime().exec(new String[]{"/usr/bin/plutil", "-convert", "xml1", "-o", "-",
                                                         System.getProperty("user.home") + "/Library/Preferences/com.apple.iTunes.plist"});
      config = new String(Util.read(p.getInputStream()));
    } catch (IOException e) {
      log.severe("can't read config file " + e);
      config = ""; // force use of the default location
    }
    String key = "alis:1:iTunes Library Location";
    if (config.contains(key)) {
      try {
        // grab alias info for current library
        byte[] data = Util.base64decode(Util.findInString(config, key, "<data>", "</data>"));
        
        // write alias information to the resource fork of a file
        File f = File.createTempFile("alias", "");
        DataOutputStream d = new DataOutputStream(new FileOutputStream(f.toString() + "/..namedfork/rsrc"));
        d.writeInt(0x100);
        d.writeInt(0x100 + data.length + 4);
        d.writeInt(data.length + 4);
        d.writeInt(0x32);
        d.write(new byte[0xf0]);
        d.writeInt(data.length);
        d.write(data);
        byte[] map = new byte[0x32];
        map[25] = 0x1c;
        map[27] = 0x32;
        map[30] = 0x61;
        map[31] = 0x6c;
        map[32] = 0x69;
        map[33] = 0x73;
        map[37] = 0x0a;
        map[40] = (byte)0xff;
        map[41] = (byte)0xff;
        d.write(map);
        d.close();
        
        // figure out its destination
        directory = resolveAlias(f.toString());
        f.delete();
        if (directory == null) throw new RuntimeException("can't resolve iTunes library link");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    // find library in directory
    File f = new File(directory + "/iTunes Library.itl");
    if (f.exists()) return f.toString();
    f = new File(directory + "/iTunes Library");
    if (f.exists()) return f.toString();
    throw new RuntimeException("can't find iTunes library (" + f + ")");
  }
  void trash(File f) throws FileNotFoundException {
    if (!com.apple.eio.FileManager.moveToTrash(f)) {
      // moveToTrash doesn't work on network drives.
      File g = new File(f.toString() + ".drm");
      if (g.exists()) throw new RuntimeException("DRM file " + g + " in the way");
      f.renameTo(g);
    }
  }
  boolean iTunesIsRunning() throws IOException {
    Process p = Runtime.getRuntime().exec("ps x");
    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
    while (true) {
      String line = r.readLine();
      if (line == null) return false;
      if (line.contains("/Applications/iTunes.app/Contents/MacOS/iTunes ")) return true;
    }
  }
  void guiMods() {
    Application a = Application.getApplication();
    a.setOpenFileHandler(new OpenFilesHandler() {
        public void openFiles(AppEvent.OpenFilesEvent e) {
          log.info("drag&drop " + e.getFiles());
          Requiem.dragDropFiles = e.getFiles();
        }
      });
  }
  boolean isShiftDown() {
    return isShiftDownNative();
  }
  native private static boolean isShiftDownNative();
  native private static String resolveAlias(String file);
}
