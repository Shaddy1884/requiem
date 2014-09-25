import java.io.*;
import java.util.logging.*;

class WindowsConfig extends Config.BaseConfig {
  private static final Logger log = Logger.getLogger(WindowsConfig.class.getName());
  static {
    try {
      for (Handler h : Logger.getLogger("").getHandlers()) {
        h.setFormatter(new OneLineFormatter());
        h.setEncoding("UTF-8");
      }
      Handler h = new FileHandler(System.getenv("APPDATA") + "\\Requiem.log");
      h.setFormatter(new OneLineFormatter());
      h.setEncoding("UTF-8");
      Logger.getLogger("").addHandler(h);
      log.info("os: " + System.getProperty("os.name"));
      log.info("jvm: " + System.getProperty("sun.arch.data.model") + " bit");
      
      File f = Util.extractTempResource("Native" + System.getProperty("sun.arch.data.model") + ".dll");
      System.load(f.toString());
      f.delete();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  byte[] macAddress() {
    return macAddressNative();
  }
  String keyStoreDirectory() {
    if (System.getenv("PROGRAMDATA") != null) {
      // Vista, 7
      return System.getenv("PROGRAMDATA") + "\\Apple Computer\\iTunes\\SC Info";
    } else if (System.getenv("ALLUSERSAPPDATA") != null) {
      // Vista, 7
      return System.getenv("ALLUSERSAPPDATA") + "\\Apple Computer\\iTunes\\SC Info";
    } else {
      // XP
      return System.getenv("ALLUSERSPROFILE") + "\\Application Data\\Apple Computer\\iTunes\\SC Info";
    }
  }
  String iTunesLibrary() {
    try {
      String iTunesPrefFile = System.getenv("APPDATA") + "\\Apple Computer\\iTunes\\iTunesPrefs.xml";
      String text = new String(Util.read(iTunesPrefFile));
      String base64 = Util.findInString(text, "<key>iTunes Library XML Location:1</key>", "<data>", "</data>");
      byte[] raw_filename = Util.base64decode(base64);
      char[] filename = new char[raw_filename.length / 2];
      for (int i = 0; i < filename.length; i++) {
        filename[i] = (char)((raw_filename[2 * i] & 0xff) + (raw_filename[2 * i + 1] & 0xff) * 256);
      }
      String xmlfile = new String(filename);
      int j = xmlfile.lastIndexOf('\\');
      if (j < 0) throw new RuntimeException("can't find iTunes library (" + xmlfile + ")");
      String libfile = xmlfile.substring(0, j + 1) + "iTunes Library.itl";
      if (!new File(libfile).exists()) throw new RuntimeException("can't find iTunes library [" + libfile + "]");
      return libfile;
    } catch (IOException e) {
      throw new RuntimeException("can't find iTunes Library", e);
    }
  }
  void trash(File f) throws FileNotFoundException {
    boolean success = trashNative(f.getAbsolutePath());
    if (!success) throw new RuntimeException("move to trash failed for " + f);
  }
  boolean iTunesIsRunning() throws IOException {
    File f = File.createTempFile("processes",".vbs");
    try {
      PrintWriter w = new PrintWriter(f);
      w.println("for each process in CreateObject(\"WbemScripting.SWbemLocator\").ConnectServer().ExecQuery(\"select name from Win32_Process\")");
      w.println("  wscript.echo process.name");
      w.println("next");
      w.close();
      Process p = Runtime.getRuntime().exec("cscript " + f);
      BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
      while (true) {
        String line = r.readLine();
        if (line == null) return false;
        if (line.equals("iTunes.exe")) return true;
      }
    } finally {
      f.delete();
    }
  }
  void guiMods() {
  }
  boolean isShiftDown() {
    return isShiftDownNative();
  }
  native private static boolean trashNative(String filename);
  native private static byte[] macAddressNative();
  native private static boolean isShiftDownNative();
}
