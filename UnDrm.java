import java.util.*;
import java.util.logging.*;
import java.io.*;

class UnDrm {
  private static final Logger log = Logger.getLogger(UnDrm.class.getName());

  private static abstract class Transform {
    abstract void prepare(byte[] data, int off, int len) throws IOException;
    abstract void transform(byte[] data, int off, int len) throws IOException;
  }
  
  private static class AtomTransform extends Transform {
    byte[] data;
    AtomTransform(String type) {
      this.data = new byte[4];
      for (int i = 0; i < 4; i++) data[i] = (byte)type.charAt(i);
    }
    void prepare(byte[] data, int off, int len) {}
    void transform(byte[] data, int off, int len) {
      assert len == 4 : len;
      System.arraycopy(this.data, 0, data, off, 4);
    }
  }
  
  private static class DecryptTrackTransform extends Transform {
    Decrypter decrypter;
    long track_id;
    DecryptTrackTransform(Decrypter decrypter, long track_id) {
      this.decrypter = decrypter;
      this.track_id = track_id;
    }
    void prepare(byte[] data, int off, int len) throws IOException {
      decrypter.sendRequest(track_id, data, off, len);
    }
    void transform(byte[] data, int off, int len) throws IOException {
      decrypter.readResponse(data, off, len);
    }
  }
  
  // a transform at a particular place in the file
  private static class TransformInstance implements Comparable<TransformInstance> {
    long offset;
    int size;
    Transform transform;
    
    TransformInstance(long offset, int size, Transform transform) {
      this.offset = offset;
      this.size = size;
      this.transform = transform;
    }
    
    public int compareTo(TransformInstance t) { // sorts smallest offset first
      if (offset < t.offset) return -1;
      if (offset > t.offset) return 1;
      return 0;
    }
  }
  
  private static File decrypt_track;
  static {
    try {
      String os = System.getProperty("os.name");
      if (os.equals("Mac OS X")) {
        decrypt_track = Util.extractTempResource("decrypt_track");
        decrypt_track.setExecutable(true);
        Util.extractTempResource("CoreFP-2.1.34/CoreFP.i386", "CoreFP.i386");
        Util.extractTempResource("CoreFP-2.1.34/CoreFP.icxs", "CoreFP.icxs");
        Util.extractTempResource("CoreFP1-1.14.34/CoreFP1.i386", "CoreFP1.i386");
        Util.extractTempResource("CoreFP1-1.14.34/CoreFP1.icxs", "CoreFP1.icxs");
        Util.makeTempDir("Resources");
        Util.makeTempFile("Resources/Info.plist", new byte[0]);
      } else if (os.startsWith("Windows ")) {
        decrypt_track = Util.extractTempResource("decrypt_track");
        decrypt_track.setExecutable(true);
        Util.extractTempResource("CoreFPWin-2.2.19/CoreFP.dll", "CoreFP.dll");
      } else throw new RuntimeException("uknown os: " + os);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private static class Decrypter {
    Process process;
    DataOutputStream requestStream;
    InputStream responseStream;
    boolean outstanding_request;
    Thread log_thread;
    RuntimeException error;
    
    Decrypter() throws IOException {
      log.info("starting native decrypter process");
      process = Runtime.getRuntime().exec(new String[]{decrypt_track.toString()}, null, Util.tempDir);
      requestStream = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
      responseStream = process.getInputStream();
      outstanding_request = false;
      
      // start a logging thread to pull messages from the decrypter stderr process
      log_thread = new Thread(){
          public void run() {
            try {
              BufferedReader logReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
              while (true) {
                String line = logReader.readLine();
                if (line == null) throw new EOFException();
                log.info("native: " + line);
                if (line.equals("ending")) break;
                if (line.startsWith("ERROR: ")) {
                  // save the error, as it needs to be thrown by the main thread
                  error = new RuntimeException(line.substring(7));
                  break;
                }
              }
            } catch (IOException e) {
              log.log(Level.SEVERE, "decryption process died prematurely " + e);
              error = new RuntimeException(e);
            }
          }
        };
      log_thread.start();
    }
    void sendInit(byte[] mac_addr, String keystoredir) throws IOException {
      try {
        requestStream.writeByte(1); // init
        requestStream.write(mac_addr);
        byte[] encoded_keystoredir = keystoredir.getBytes(); // TODO: I want default encoding, right?
        requestStream.writeInt(encoded_keystoredir.length);
        requestStream.write(encoded_keystoredir);
      } catch (IOException e) {
        try { log_thread.join(); } catch (InterruptedException i) {}
        if (error != null) throw error;
        throw e;
      }
    }
    
    Transform initTrack(long track_id, Mp4.Atom sinf, Mp4.Atom uuid) throws IOException {
      try {
        requestStream.writeByte(2); // init track
        requestStream.writeLong(track_id);
        requestStream.writeInt((int)sinf.size);
        requestStream.write(sinf.readFully(0));
        if (uuid != null) {
          requestStream.writeInt((int)uuid.size);
          requestStream.write(uuid.readFully(0));
        } else {
          requestStream.writeInt(0);
        }
        return new DecryptTrackTransform(this, track_id);
      } catch (IOException e) {
        try { log_thread.join(); } catch (InterruptedException i) {}
        if (error != null) throw error;
        throw e;
      }
    }
    void sendRequest(long track_id, byte[] data, int off, int len) throws IOException {
      try {
        requestStream.writeByte(3); // request decryption
        requestStream.writeLong(track_id);
        requestStream.writeInt(len);
        requestStream.write(data, off, len);
        outstanding_request = true;
      } catch (IOException e) {
        try { log_thread.join(); } catch (InterruptedException i) {}
        if (error != null) throw error;
        throw e;
      }
    }
    void readResponse(byte[] data, int off, int len) throws IOException {
      try {
        if (outstanding_request) {
          requestStream.writeByte(4); // tell decrypter to run queued requests
          requestStream.flush();
          outstanding_request = false;
        }
        while (len > 0) {
          int n = responseStream.read(data, off, len);
          off += n;
          len -= n;
        }
      } catch (IOException e) {
        try { log_thread.join(); } catch (InterruptedException i) {}
        if (error != null) throw error;
        throw e;
      }
    }
    void close() throws IOException {
      try {
        log.info("closing native decrypter");
        requestStream.writeByte(5); // shutdown
        requestStream.flush();
        try {
          log_thread.join();
          log.info("waiting for subprocess");
          int res = process.waitFor();
          log.info("subprocess exited with code " + res);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        try { log_thread.join(); } catch (InterruptedException i) {}
        if (error != null) throw error;
        throw e;
      }
    }
  }
  
  // returns a pointer to mdia.minf.stbl.stsd.(drms|drmi|p608|drmt)
  static Mp4.Atom getTrackDrmAtom(Mp4.Atom trak) {
    Mp4.Atom stsd = trak.find("mdia.minf.stbl.stsd");
    if (stsd == null) return null;
    Mp4.Atom drm = stsd.find("drms");
    if (drm == null) drm = stsd.find("drmi");
    if (drm == null) drm = stsd.find("p608");
    if (drm == null) drm = stsd.find("drmt");
    return drm;
  }
  
  // adds to transforms the items needed to decrypt the track.
  private static void deDrmTransforms(Mp4.Atom trak, Decrypter decrypter, List<TransformInstance> transforms) throws IOException {
    Mp4.Atom drm = getTrackDrmAtom(trak);
    if (drm == null) return;
    Mp4.Atom sinf = drm.find("sinf");
    Mp4.Atom uuid = drm.find("UUID");
    Mp4.Atom frma = sinf.find("frma");
    
    // initialize decrypter with DRM information from this track
    Transform track_decrypter = decrypter.initTrack(trak.offset, sinf, uuid);
    
    // neuter the sinf atom
    transforms.add(new TransformInstance(sinf.offset + 4, 4, new AtomTransform("pinf")));
    
    // frma contains the new name for drms (or drmi or p608 or drmt)
    transforms.add(new TransformInstance(drm.offset + 4, 4, new AtomTransform(frma.readType(8))));
    
    // decrypt track proper
    Mp4.Atom stbl = trak.find("mdia.minf.stbl");
    Mp4.Atom stsc = stbl.find("stsc");
    Mp4.Atom stsz = stbl.find("stsz");
    Mp4.Atom stco = stbl.find("stco");
    Mp4.Atom co64 = stbl.find("co64");
    
    // get sample size data
    int[] sample_sizes;
    {
      int fixed_sample_size = stsz.readInt(12);
      int nsamples = stsz.readInt(16);
      if (fixed_sample_size != 0) {
        sample_sizes = new int[nsamples];
        for (int i = 0; i < nsamples; i++) sample_sizes[i] = fixed_sample_size;
      } else {
        sample_sizes = stsz.readIntArray(20, nsamples);
      }
    }
    
    // get chunk offsets
    long[] chunk_offsets;
    {
      if (co64 != null) {
        int n = co64.readInt(12);
        chunk_offsets = co64.readLongArray(16, n);
      } else {
        int n = stco.readInt(12);
        chunk_offsets = new long[n];
        int[] x = stco.readIntArray(16, n);
        for (int i = 0; i < n; i++) chunk_offsets[i] = x[i] & 0xffffffffL;
      }
    }
    
    // get chunk group info
    int chunk_groups = stsc.readInt(12);
    int[] chunk_group_info = stsc.readIntArray(16, 3 * chunk_groups);
    
    // Loop through chunk groups, chunks, and samples, queueing a decrypt
    // request for each sample.
    int sample = 0;
    for (int chunk_group = 0; chunk_group < chunk_groups; chunk_group++) {
      int first_chunk = chunk_group_info[chunk_group * 3] - 1;
      int samples_per_chunk = chunk_group_info[chunk_group * 3 + 1];
      int chunks;
      if (chunk_group != chunk_groups - 1) {
        chunks = chunk_group_info[chunk_group * 3 + 3] - 1 - first_chunk;
      } else {
        chunks = chunk_offsets.length - first_chunk;
      }
      for (int chunk = first_chunk; chunk < first_chunk + chunks; chunk++) {
        long sample_offset = chunk_offsets[chunk];
        for (int i = 0; i < samples_per_chunk; i++, sample++) {
          int sample_size = sample_sizes[sample];
          transforms.add(new TransformInstance(sample_offset, sample_size, track_decrypter));
          sample_offset += sample_size;
        }
      }
    }
  }
  
  public static boolean unDrmMp4(File f, File g) throws IOException {
    log.info("removing drm " + f + " -> " + g);
    
    AtomRandomAccessFile r = new AtomRandomAccessFile(f, "r");
    Mp4 mp4 = new Mp4(r);
    Mp4.Atom moov = mp4.find("moov");
    
    Decrypter decrypter = new Decrypter();
    decrypter.sendInit(Config.macAddress(), Config.keyStoreDirectory());
    
    // figure out decryptions required for each track
    List<TransformInstance> transforms = new ArrayList<TransformInstance>();
    for (Mp4.Atom trak : moov.children) {
      deDrmTransforms(trak, decrypter, transforms);
    }
    
    // order them
    Collections.sort(transforms);
    
    // allocate big buffer
    int bufsize = 1048576;
    for (TransformInstance t : transforms) {
      bufsize = Math.max(bufsize, t.size);
    }
    byte[] buf = new byte[bufsize];
    
    // do decryption
    InputStream in = new FileInputStream(f);
    OutputStream out = new FileOutputStream(g);
    
    long offset = 0;       // position in file of first byte in buffer
    int size = 0;          // valid bytes in buffer
    int transform_idx = 0; // next transform to do
    while (true) {
      // fill buffer with some data
      int n = in.read(buf, size, buf.length - size);
      if (n < 0) {
        assert transform_idx == transforms.size();
        out.write(buf, 0, size);
        break;
      }
      size += n;
      
      // prepare the transforms for all the data in the buffer
      int prepare_idx = transform_idx;
      int output_size = size;
      while (prepare_idx < transforms.size()) {
        TransformInstance t = transforms.get(prepare_idx);
        if (t.offset + t.size > offset + size) { // don't have all the data for this transform
          if (t.offset < offset + size) { // save partial data for next iteration
            output_size = (int)(t.offset - offset);
          }
          break;
        }
        t.transform.prepare(buf, (int)(t.offset - offset), t.size);
        prepare_idx++;
      }
      
      // then do the transforms
      for (int i = transform_idx; i < prepare_idx; i++) {
        TransformInstance t = transforms.get(i);
        t.transform.transform(buf, (int)(t.offset - offset), t.size);
      }
      
      // write out transformed data
      out.write(buf, 0, output_size);
      
      // move up remaining data (partial transform)
      System.arraycopy(buf, output_size, buf, 0, size - output_size);
      offset += output_size;
      size -= output_size;
      transform_idx = prepare_idx;
    }
    
    out.close();
    in.close();
    r.close();
    decrypter.close();
    
    return true;
  }

  /** Removes the DRM from f, writes the result to g, and returns true.
      If no DRM is found, g is not written and it returns false. */
  public static boolean unDrm(File f, File g) throws Exception {
    // TODO: books?
    return unDrmMp4(f, g);
  }
  
  static boolean canRemoveDrm(File f) throws IOException {
    AtomRandomAccessFile r = new AtomRandomAccessFile(f, "r");
    try {
      if (r.length() < 12) return false;
      String ftyp = r.readType(4);
      String kind = r.readType(8);
      if (ftyp.equals("ftyp") && (kind.equals("M4A ") || kind.equals("M4V ") || kind.equals("M4B "))) {
        Mp4 mp4 = new Mp4(r);
        Mp4.Atom moov = mp4.find("moov");
        for (Mp4.Atom trak : moov.children) {
          if (!trak.type.equals("trak")) continue;
          Mp4.Atom drm = getTrackDrmAtom(trak);
          if (drm != null) return true;
        }
      }
      return false;
    } finally {
      r.close();
    }
  }
  
  public static void main(String[] args) {
    new Config(); // trigger Config initialization, in particular logging.
    try {
      unDrm(new File(args[0]), new File(args[1]));
    } catch (Throwable t) {
      log.log(Level.SEVERE, "", t);
    }
  }
}
