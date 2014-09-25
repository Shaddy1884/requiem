import java.io.*;

// RandomAccessFile that can also read/write 4-char atom types and int/long arrays
class AtomRandomAccessFile extends RandomAccessFile {
  AtomRandomAccessFile(String f, String mode) throws IOException {
    super(f, mode);
  }
  AtomRandomAccessFile(File f, String mode) throws IOException {
    super(f, mode);
  }
  public String readType() throws IOException {
    int x = readInt();
    String t = "";
    for (int i = 0; i < 4; i++) {
      t += (char)((x >> (24 - 8 * i)) & 0xff);
    }
    return t;
  }
  public void writeType(String t) throws IOException {
    assert t.length() == 4;
    int x = 0;
    for (int i = 0; i < 4; i++) {
      assert (t.charAt(i) & 0xff) == t.charAt(i) : t.charAt(i);
      x += t.charAt(i) << (24 - 8 * i);
    }
    writeInt(x);
  }
  
  public int readInt(long offset) throws IOException {
    seek(offset);
    return readInt();
  }
  
  public long readLong(long offset) throws IOException {
    seek(offset);
    return readLong();
  }
  
  public String readType(long offset) throws IOException {
    seek(offset);
    return readType();
  }
  public byte[] readFully(long offset, long size) throws IOException {
    if (size != (int)size) throw new IllegalArgumentException("size too big " + size);
    byte[] data = new byte[(int)size];
    seek(offset);
    readFully(data);
    return data;
  }
  public byte[] readFully() throws IOException {
      return readFully(0, length());
  }
  public int[] readIntArray(long offset, int count) throws IOException {
    byte[] data = readFully(offset, count * 4);
    int[] result = new int[count];
    for (int i = 0; i < count; i++) {
      result[i] = (data[4 * i] << 24) + ((data[4 * i + 1] & 0xff) << 16) + ((data[4 * i + 2] & 0xff) << 8) + (data[4 * i + 3] & 0xff);
    }
    return result;
  }
  public long[] readLongArray(long offset, int count) throws IOException {
    byte[] data = readFully(offset, count * 8);
    long[] result = new long[count];
    for (int i = 0; i < count; i++) {
      result[i] = ((data[8 * i] & 0xffL) << 56) + ((data[8 * i + 1] & 0xffL) << 48) + ((data[8 * i + 2] & 0xffL) << 40) + ((data[8 * i + 3] & 0xffL) << 32) +
                  ((data[8 * i + 4] & 0xffL) << 24) + ((data[8 * i + 5] & 0xffL) << 16) + ((data[8 * i + 6] & 0xffL) << 8) + (data[8 * i + 7] & 0xffL);
    }
    return result;
  }
}
