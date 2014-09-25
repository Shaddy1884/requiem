import java.util.*;
import java.util.logging.*;
import java.io.*;
import javax.crypto.*;

class UnDrmQueue {
  private static final Logger log = Logger.getLogger(UnDrmQueue.class.getName());
  static class LogNotifier implements Notifier {
    public void check(File a) {
      log.info("checking " + a);
    }
    public void nodrm(File a) {
      log.info("nodrm " + a);
    }
    public void queue(File a) {
      log.info("queue " + a);
    }
    public void error(File a, String msg) {
      log.severe("error " + a + ": " + msg);
    }
    public void noMoreChecks() {
      log.info("drm search done");
    }
    public void work(File a) {
      log.info("working on " + a);
    }
    public void success(File a) {
      log.info("success " + a);
    }
    public void failure(File a, String msg) {
      log.severe("failure " + a + ": " + msg);
    }
  }
  static Notifier notifier = new LogNotifier();
  
  static class Task {
    File file;
    Task(File f) {
      file = f;
    }
  }
  static List<Task> tasks = new ArrayList<Task>();
  
  static Object lock = new Object();
  static boolean queueing = false;
  static boolean working = false;
  
  // call before you start adding new files via queueFile/queueList
  static void queueBegin() {
    synchronized (lock) {
      assert !working;
      assert !queueing;
      queueing = true;
    }
  }
  
  // adds a file to work on
  static void queueFile(File f) {
    assert queueing;
    if (!f.isFile()) return;
    if (f.length() < 12) return;
    
    // at this point, it is worth marking our progress
    notifier.check(f);
    
    try {
      if (UnDrm.canRemoveDrm(f)) {
        Task t = new Task(f);
        tasks.add(t);
        notifier.queue(f);
      } else {
        notifier.nodrm(f);
      }
    } catch (Exception e) {
      String msg = e.toString();
      if (msg.startsWith("java.lang.RuntimeException: ")) msg = e.getMessage();
      notifier.error(f, msg);
    }
  }
  
  // adds a list of files to work on (and recursively, if one is a directory)
  static void queueList(List<File> files) {
    assert queueing;
    for (File f : files) {
      if (f.isDirectory()) {
        queueList(Arrays.asList(f.listFiles()));
      } else if (f.isFile()) {
        queueFile(f);
      }
    }
  }
  
  // call after you are done adding new files via queueFile/queueList
  static void queueEnd() {
    synchronized (lock) {
      assert !working;
      assert queueing;
      queueing = false;
      working = true;
      notifier.noMoreChecks();
      lock.notify();
    }
  }
  
  // waits until some files are queued, then processes them.
  static void go() throws InterruptedException {
    synchronized (lock) {
      while (!working) lock.wait();
      assert !queueing;
      for (Task t : tasks) {
        File f = t.file;
        try {
          notifier.work(f);
          File g = new File(f.toString() + ".tmp");
          UnDrm.unDrm(f, g);
          
          // commit the unDRMed file
          if (System.getProperty("requiem.write") == null ||
              !System.getProperty("requiem.write").equals("0")) {
            log.info("committing new file");
            Config.trash(f); // move DRMed file to the trash
            g.renameTo(f);   // move unDRMed file to replace it
          } else {
            log.info("destroying new file");
            Config.trash(g); // move unDRMed file to the trash
          }
          
          notifier.success(f);
        } catch (Exception e) {
          notifier.failure(f, e.toString());
        }
      }
      tasks.clear();
      working = false;
    }
  }
}
