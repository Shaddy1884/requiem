import java.util.*;
import java.io.*;

public class Mp4 {
  private AtomRandomAccessFile r; // underlying file (not owned)
  private Atom root;              // cached atom structure
  Mp4(AtomRandomAccessFile r) throws IOException {
    this.r = r;
    root = parse();
  }

  public class Atom {
    String type; // 4-character code
    long offset; // position in file
    long size;   // length of atom
    public final List<Atom> children = new ArrayList<Atom>();
    public Atom(String type, long offset, long size) {
      this.type = type;
      this.offset = offset;
      this.size = size;
    }

    String readType(long atom_offset) throws IOException {
      return r.readType(offset + atom_offset);
    }
    int readInt(long atom_offset) throws IOException {
      return r.readInt(offset + atom_offset);
    }
    long readLong(long atom_offset) throws IOException {
      return r.readLong(offset + atom_offset);
    }
    byte[] readFully(long atom_offset, long len) throws IOException {
      return r.readFully(offset + atom_offset, len);
    }
    byte[] readFully(long atom_offset) throws IOException {
      return readFully(atom_offset, size - atom_offset);
    }
    int[] readIntArray(long atom_offset, int count) throws IOException {
      return r.readIntArray(offset + atom_offset, count);
    }
    long[] readLongArray(long atom_offset, int count) throws IOException {
      return r.readLongArray(offset + atom_offset, count);
    }
    
    public void print() {
      print("");
    }
    private void print(String indent) {
      System.out.println(indent + type + " " + offset + " " + size);
      for (Atom child : children) {
        child.print(indent + "  ");
      }
    }
    
    public Atom find(String types) {
      if (types.equals("")) return this;
      String first = types.substring(0, 4);
      String rest;
      if (types.length() == 4) {
        rest = "";
      } else {
        assert types.charAt(4) == '.';
        rest = types.substring(5);
      }
      for (Atom child : children) {
        if (child.type.equals(first)) {
          Atom a = child.find(rest);
          if (a != null) return a;
        }
      }
      return null;
    }
  }
  
  public Atom find(String types) {
    return root.find(types);
  }
  
  // Information about what offset the children of an atom start.
  // Any atom not in this map is assumed to have no children.
  // TODO: needs to be adjusted for 64-bit atoms?
  private static HashMap<String,Integer> children_offset = new HashMap<String,Integer>();
  static {
    children_offset.put("moov", 8);
    children_offset.put("trak", 8);
    children_offset.put("mdia", 8);
    children_offset.put("minf", 8);
    children_offset.put("stbl", 8);
    children_offset.put("stsd", 16);
    children_offset.put("drms", 36);
    children_offset.put("drmi", 86);
    children_offset.put("p608", 16);
    children_offset.put("drmt", 46);
    children_offset.put("sinf", 8);
    children_offset.put("schi", 8);
  }
  
  private Atom parse() throws IOException {
    long length = r.length();
    Atom atom = new Atom("file", 0, length); // fake wrapper atom
    r.seek(0);
    atom.children.addAll(parse(length));
    return atom;
  }
  private List<Atom> parse(long stop_offset) throws IOException {
    List<Atom> atoms = new ArrayList<Atom>();
    while (r.getFilePointer() < stop_offset) {
      long offset = r.getFilePointer();
      long size = r.readInt() & 0xffffffffL;
      String type = r.readType();
      int hdr_size = 8;
      if (size == 1) { size = r.readLong(); hdr_size = 16; }
      Atom atom = new Atom(type, offset, size);
      if (children_offset.containsKey(type)) {
        r.skipBytes(children_offset.get(type) - hdr_size); // TODO: fix for 64-bit atoms?
        atom.children.addAll(parse(offset + size));
      } else {
        r.seek(offset + size);
      }
      atoms.add(atom);
      assert r.getFilePointer() == offset + size;
    }
    return atoms;
  }
  
  public static void main(String[] args) throws IOException {
    for (String arg : args) {
      AtomRandomAccessFile r = new AtomRandomAccessFile(arg, "r");
      Mp4 mp4 = new Mp4(r);
      mp4.root.print();
      r.close();
    }
  }
}
