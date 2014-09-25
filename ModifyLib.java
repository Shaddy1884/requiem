import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.GeneralSecurityException;
import java.util.zip.Inflater;
import java.util.zip.Deflater;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.net.*;

class ModifyLib {
  private static final Logger log = Logger.getLogger(ModifyLib.class.getName());
  
  // read 4-byte big-endian number
  private static int read32(byte[] data, int offset) {
    return (data[offset]&0xff)*256*256*256 + (data[offset+1]&0xff)*256*256 + (data[offset+2]&0xff)*256 + (data[offset+3]&0xff);
  }
  private static void write32(byte[] data, int offset, int v) {
    data[offset] = (byte)(v >> 24);
    data[offset+1] = (byte)(v >> 16);
    data[offset+2] = (byte)(v >> 8);
    data[offset+3] = (byte)v;
  }
  private static void write32(ByteArrayOutputStream o, int v) {
    byte[] data = new byte[4];
    write32(data, 0, v);
    o.write(data, 0, 4);
  }
  
  static class LibraryData {
    File save; // place to save updated versions
    byte[] header;
    byte[] body;
  }
  
  private static LibraryData loadLibrary(File f) throws Exception {
    LibraryData library_data = new LibraryData();

    int len = (int)f.length();
    byte[] data = new byte[len];
    RandomAccessFile s = new RandomAccessFile(f, "r");
    s.readFully(data);
    
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    
    // first 4 bytes - "hdfm"
    if (read32(data, 0) != 0x6864666d) throw new RuntimeException("bad iTunes library format");
    // next 4 bytes - size of header (always 0x90?)
    int header_size = read32(data, 4);
    // next 4 bytes - size of file
    if (read32(data, 8) != data.length) throw new RuntimeException("bad iTunes library format");
    // next 4 bytes - ???
    // next byte - version # (length, data)
    int version_length = data[16];
    String version = "";
    for (int i = 0; i < version_length; i++) version += (char)data[17 + i];
    log.info("iTunes library version " + version);
    if (version.startsWith("11.")) {
      // I think this code works for all 10.x iTunes libraries.
    } else {
      throw new RuntimeException("old (or new) unhandled library version " + version);
    }
    // rest of header - ???
    
    // decrypt all full blocks in the body
    Key key = new SecretKeySpec("BHUILuilfghuila3".getBytes(), "AES");
    Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key);
    int crypt_size = (data.length - header_size) & ~0xf;
    int max_crypt_size = read32(data, 92);
    if (max_crypt_size != 0) crypt_size = Math.min(crypt_size, max_crypt_size);
    cipher.doFinal(data, header_size, crypt_size, data, header_size);
    
    // un-zip body
    Inflater inflater = new Inflater();
    inflater.setInput(data, header_size, data.length - header_size);
    byte[] clear_data = new byte[65536];
    while (!inflater.finished()) {
      int n = inflater.inflate(clear_data);
      o.write(clear_data, 0, n);
    }
    
    library_data.header = new byte[header_size];
    System.arraycopy(data, 0, library_data.header, 0, header_size);
    library_data.body = o.toByteArray();
    return library_data;
  }

  private static void saveLibrary(LibraryData f) throws IOException {
    // zip body
    ByteArrayOutputStream zipped = new ByteArrayOutputStream();
    Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    deflater.setInput(f.body);
    deflater.finish();
    byte[] packed_data = new byte[65536];
    while (!deflater.finished()) {
      int n = deflater.deflate(packed_data);
      zipped.write(packed_data, 0, n);
    }
    byte[] body = zipped.toByteArray();
    
    // encrypt body
    try {
      Key key = new SecretKeySpec("BHUILuilfghuila3".getBytes(), "AES");
      Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      int crypt_size = body.length & ~0xf;
      int max_crypt_size = read32(f.header, 92);
      if (max_crypt_size != 0) crypt_size = Math.min(crypt_size, max_crypt_size);
      cipher.doFinal(body, 0, crypt_size, body, 0);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
    
    // modify size in header
    byte[] modified_header = new byte[f.header.length];
    System.arraycopy(f.header, 0, modified_header, 0, f.header.length);
    int len = f.header.length + body.length;
    modified_header[8] = (byte)(len >> 24);
    modified_header[9] = (byte)(len >> 16);
    modified_header[10] = (byte)(len >> 8);
    modified_header[11] = (byte)len;
    
    FileOutputStream out = new FileOutputStream(f.save);
    out.write(modified_header);
    out.write(body);
    out.close();
  }
  
  // patch to apply to [offset,offset+data.length) of some other array.
  static class Modification {
    int offset;
    byte[] data;
    Modification(int offset, byte[] data) {
      this.offset = offset;
      this.data = data;
    }
    void apply(byte[] body) {
      System.arraycopy(data, 0, body, offset, data.length);
    }
  }
  
  // convert from URIs to Files and back.  Just need to add/remove localhost reference.
  private static File fromURI(String uri) {
    assert uri.startsWith("file://localhost/");
    try {
      // get rid of localhost.  The File(URI) constructor doesn't like having localhost there.
      String no_localhost_uri = "file:" +uri.substring(16);
      return new File(new URI(no_localhost_uri));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  private static String toURI(File f) {
    // put back localhost.
    assert f.toURI().toString().startsWith("file:");
    return "file://localhost" + f.toURI().toString().substring(5);
  }

  static class ModifyLibNotifier implements Notifier {
    Notifier chain;
    LibraryData library;
    ModifyLibNotifier(Notifier n, LibraryData lib) {
      chain = n;
      library = lib;
    }
    public void check(File a) {
      chain.check(a);
    }
    public void nodrm(File a) {
      chain.nodrm(a);
    }
    public void queue(File a) {
      chain.queue(a);
    }
    public void error(File a, String msg) {
      chain.error(a, msg);
    }
    public void noMoreChecks() {
      chain.noMoreChecks();
    }
    public void work(File a) {
      chain.work(a);
    }
    public void success(File a) {
      chain.success(a);
      if (System.getProperty("requiem.write") == null ||
          !System.getProperty("requiem.write").equals("0")) {
        try {
          log.info("updating library");
          if (Config.iTunesIsRunning()) throw new RuntimeException("detected iTunes launch - quit iTunes and run again.");
          List<Modification> modifications = modification_table.get(a);
          for (Modification m : modifications) {
            m.apply(library.body);
            saveLibrary(library);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
    public void failure(File a, String msg) {
      chain.failure(a, msg);
    }
  };
  
  static Map<File,List<Modification>> modification_table = new HashMap<File,List<Modification>>();
  
  private static void checkDrm(String uri, List<Modification> modifications) throws IOException {
    if (!uri.startsWith("file://localhost/")) return;
    
    // special case for iTunes Extras
    if (uri.endsWith("%20-%20iTunes%20Extras.ite/")) {
      try {
        File f = fromURI(uri.substring(0, uri.length() - 1));
        File audio = new File(f, "audio");
        if (audio.isDirectory()) {
          for (File g : audio.listFiles()) {
            checkDrm(toURI(g), new ArrayList<Modification>());
          }
        }
        File videos = new File(f, "videos");
        if (videos.isDirectory()) {
          for (File g : videos.listFiles()) {
            checkDrm(toURI(g), new ArrayList<Modification>());
          }
        }
      } catch (Exception e) {
        // ignore these, probably something wrong with the Extras format.
        log.log(Level.WARNING, "bad extras " + uri, e);
      }
      return;
    }
    
    File f = fromURI(uri);
    modification_table.put(f, modifications);
    UnDrmQueue.queueFile(f);
  }
  
  private static void findDrmFile(byte[] input, int base, int a, int b) throws IOException {
    String uri = null;
    List<Modification> modifications = new ArrayList<Modification>();
    
    while (a < b) {
      String type = new String(input, a, 4);
      int h1 = read32(input, a + 4);
      int h2 = read32(input, a + 8);
      int h3 = read32(input, a + 12);
      if (type.equals("hohm") && h3 == 6) { // kind
        byte[] protected_ = "Protected ".getBytes();
        boolean found_protected = true;
        for (int i = 0; i < protected_.length; i++) {
          if (protected_[i] != input[a + 40 + i]) {
            found_protected = false;
            break;
          }
        }
        if (found_protected) {
          modifications.add(new Modification(a + 40, "Purchased ".getBytes()));
        }
      }
      if (type.equals("hohm") && h3 == 11) { // local url
        uri = new String(input, a + 40, h2 - 40);
        byte[] uri_bytes = uri.getBytes();
        assert uri_bytes.length == uri.length(); // URIs should be encoded so no non-ASCII chars are in it.
        if (uri.endsWith(".m4p")) {
          //modifications.add(new Modification(a + 40 + uri_bytes.length - 1, "a")); // TODO: do this?
        }
        if (uri.endsWith(".epub")) {
          // modify htim record to convince iTunes that the file isn't encrypted
          modifications.add(new Modification(base + 0x70, new byte[]{0,0,0,0})); // clobber user id
          modifications.add(new Modification(base + 0x88, new byte[]{0,0,0,0})); // clobber key id
          modifications.add(new Modification(base + 0x198, new byte[]{0,0,0,0})); // clobber user id
          modifications.add(new Modification(base + 0x1a8, new byte[]{0,0,0,0})); // clobber key id
        }
      }
      a += h2;
    }
    if (uri != null) {
      checkDrm(uri, modifications);
    }
  }
  private static void findDrmSection(byte[] input, int a, int b) throws IOException {
    while (a < b) {
      String type = new String(input, a, 4);
      int h1 = read32(input, a + 4);
      int h2 = read32(input, a + 8);
      int h3 = read32(input, a + 12);
      if (type.equals("htim")) { // music/video track info
        findDrmFile(input, a, a + h1, a + h2);
        a += h2;
      } else if (type.equals("htlm")) {
        a += h1;
      } else {
        a += h2;
      }
    }
  }
  private static void findDrmFiles(byte[] input, int a, int b) throws IOException {
    while (a < b) {
      String type = new String(input, a, 4);
      int h1 = read32(input, a + 4);
      int h2 = read32(input, a + 8);
      int h3 = read32(input, a + 12);
      if (type.equals("hdsm") && (h3 == 1 || h3 == 13)) { // music and video track sections
        findDrmSection(input, a + h1, a + h2);
      }
      a += h2;
    }
  }
  private static void findDrmFiles(byte[] input) throws IOException {
    UnDrmQueue.queueBegin();
    findDrmFiles(input, 0, input.length);
    UnDrmQueue.queueEnd();
  }
  
  public static void queue(String[] args) throws Exception {
    if (Config.iTunesIsRunning()) throw new RuntimeException("Please quit iTunes before running Requiem.");
    
    File f;
    File g;
    if (args.length == 0) {
      f = g = new File(Config.iTunesLibrary());
    } else if (args.length == 1) {
      f = new File(Config.iTunesLibrary());
      g = new File(args[0]);
    } else {
      f = new File(args[0]);
      g = new File(args[1]);
    }
    
    // read library file
    LibraryData library_data = loadLibrary(f);
    library_data.save = g;
    
    // intercept notifications as each file is successfully unDRMed, so we can
    // update the library accordingly.
    UnDrmQueue.notifier = new ModifyLibNotifier(UnDrmQueue.notifier, library_data);
    
    // back up old library file if we're overwriting it
    if (f == g) {
      OutputStream copy = new FileOutputStream(f.toString() + ".requiem_backup");
      Util.copy(new FileInputStream(f), copy);
      copy.close();
    }
    
    // queue up the files we need to work on.
    findDrmFiles(library_data.body);
  }
  public static void main(String[] args) throws Exception {
    queue(args);
    UnDrmQueue.go();
  }
}
