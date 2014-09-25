import java.io.*;

public interface Notifier {
  // first call this for a file
  public void check(File a);
  
  // after check(), call one of these three, one to declare it
  // drm-free, one to queue it for work, another to reject it.
  public void nodrm(File a);
  public void queue(File a);
  public void error(File a, String msg);
  
  public void noMoreChecks();
  
  // once all of the above are done, for each queued file,
  // call work() on a file, then either success() or failure().
  public void work(File a);
  public void success(File a);
  public void failure(File a, String msg);
}
