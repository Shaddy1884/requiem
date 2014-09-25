import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.util.logging.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

class Util {
  private static final Logger log = Logger.getLogger(Util.class.getName());
  public static void printBuf(String name, byte[] buf) {
    printBuf(name, buf, 0, buf.length);
  }
  public static void printBuf(String name, byte[] buf, int offset, int len) {
    log.fine(name);
    for (int i = 0; i < len; i += 16) {
      String line = String.format("%08x: ", i);
      for (int j = 0; j < 16; j++) {
        line += i + j < len ? String.format("%02x ", buf[offset + i + j] & 0xff) : "   ";
      }
      line += "  ";
      for (int j = 0; j < 16; j++) {
        line += i + j < len && buf[offset + i + j] >= 0x20 && buf[offset + i + j] < 0x7f ? (char)buf[offset + i + j] : ' ';
      }
      log.fine(line);
    }
  }
  public static byte[] read(File file) throws IOException {
    FileInputStream i = new FileInputStream(file);
    byte[] result = read(i);
    i.close();
    return result;
  }
  public static byte[] read(String file) throws IOException {
    FileInputStream i = new FileInputStream(file);
    byte[] result = read(i);
    i.close();
    return result;
  }
  public static byte[] read(InputStream i) throws IOException {
    return read(i, Integer.MAX_VALUE);
  }
  public static byte[] read(InputStream i, int maxBytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[Math.min(maxBytes, 4096)];
    while (maxBytes > 0) {
      int c = i.read(buf, 0, Math.min(maxBytes, buf.length));
      if (c < 0) break;
      out.write(buf, 0, c);
      maxBytes -= c;
    }
    return out.toByteArray();
  }
  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[4096];
    while (true) {
      int c = in.read(buf);
      if (c < 0) break;
      out.write(buf, 0, c);
    }
  }
  
  // a directory to hold temporary files.
  public static File tempDir;
  static {
    try {
      tempDir = File.createTempFile("requiemTemp", "dir");
      if (!tempDir.delete()) throw new IOException("couldn't delete temp dir");
      if (!tempDir.mkdir()) throw new IOException("couldn't make temp dir");
      tempDir.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  // loads input stream into the file.  Closes the input stream when done.
  public static void extractStream(InputStream in, File f) throws IOException {
    OutputStream out = new FileOutputStream(f);
    copy(in, out);
    out.close();
    in.close();
  }
  public static void extractResource(String resource, File file) throws IOException {
    extractStream(Util.class.getResourceAsStream(resource), file);
  }
  public static File extractTempResource(String resource, String name) throws IOException {
    File f = new File(tempDir, name);
    extractResource(resource, f);
    f.deleteOnExit();
    return f;
  }
  public static File extractTempResource(String resource) throws IOException {
    return extractTempResource(resource, resource);
  }
  public static File makeTempDir(String name) throws IOException {
    File f = new File(tempDir, name);
    if (!f.mkdir()) throw new IOException("couldn't make temp directory");
    f.deleteOnExit();
    return f;
  }
  public static File makeTempFile(String name, byte[] contents) throws IOException {
    File f = new File(tempDir, name);
    OutputStream o = new FileOutputStream(f);
    o.write(contents);
    o.close();
    f.deleteOnExit();
    return f;
  }
  
  // hacky way of parsing xml
  static String findInString(String data, String pre1, String pre2, String post) {
    int a = data.indexOf(pre1);
    if (a < 0) throw new RuntimeException("couldn't find " + pre1);
    a += pre1.length();
    int b = data.indexOf(pre2, a);
    if (b < 0) throw new RuntimeException("couldn't find " + pre2);
    b += pre2.length();
    int c = data.indexOf(post, b);
    if (c < 0) throw new RuntimeException("couldn't find " + post);
    return data.substring(b, c);
  }
  static byte[] base64decode(String data) throws IOException {
    // strip whitespace
    StringBuilder s = new StringBuilder();
    for (char c : data.toCharArray()) {
      if (!Character.isWhitespace(c)) s.append(c);
    }
    return new sun.misc.BASE64Decoder().decodeBuffer(s.toString());
  }
  static int getInt32(byte[] data, int offset) {
    return ((data[offset+0]&0xff)<<24) + ((data[offset+1]&0xff)<<16) + ((data[offset+2]&0xff)<<8) + (data[offset+3]&0xff);
  }
}
