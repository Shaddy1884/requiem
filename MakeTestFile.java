import java.io.*;

// Generates a file to pass as the first argument to decrypt_track.
// Used for testing/development only.

class MakeTestFile {
  public static void main(String[] args) throws IOException {
    AtomRandomAccessFile a = new AtomRandomAccessFile(args[0], "r");
    Mp4 mp4 = new Mp4(a);
    Mp4.Atom trak = mp4.find("moov.trak"); // first track only
    
    DataOutputStream out = new DataOutputStream(new FileOutputStream(args[1]));
    out.writeByte(1);
    out.write(Config.macAddress());
    byte[] encoded_keystoredir = Config.keyStoreDirectory().getBytes(); // TODO: I want default encoding, right?
    out.writeInt(encoded_keystoredir.length);
    out.write(encoded_keystoredir);
    out.writeByte(2);
    out.writeLong(trak.offset);
    Mp4.Atom stsd = trak.find("mdia.minf.stbl.stsd");
    Mp4.Atom drm = stsd.find("drms");
    if (drm == null) drm = stsd.find("drmi");
    if (drm == null) drm = stsd.find("p608");
    if (drm == null) drm = stsd.find("drmt");
    if (drm == null) throw new RuntimeException("no drm");
    Mp4.Atom sinf = drm.find("sinf");
    Mp4.Atom uuid = drm.find("UUID");
    if (sinf != null) {
      out.writeInt((int)sinf.size);
      out.write(sinf.readFully(0));
    } else {
      out.writeInt(0);
    }
    if (uuid != null) {
      out.writeInt((int)uuid.size);
      out.write(uuid.readFully(0));
    } else {
      out.writeInt(0);
    }
    
    out.writeByte(3);
    out.writeLong(trak.offset);
    
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
          if (sample_size >= 16) { // TODO: other larger size?
            out.writeInt(sample_size);
            out.write(a.readFully(sample_offset, sample_size));
            out.writeByte(4);
            out.writeByte(5);
            out.close();
            return;
          }
          sample_offset += sample_size;
        }
      }
    }
  }
}
