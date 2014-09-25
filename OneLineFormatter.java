import java.util.logging.*;
import java.io.*;
import java.text.*;
import java.util.Date;

class OneLineFormatter extends Formatter {
  private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
  
  public String format(LogRecord r) {
    StringBuilder s = new StringBuilder();
    s.append(dateFormatter.format(new Date(r.getMillis())));
    s.append(" ");
    s.append(r.getLevel());
    s.append(" ");
    Throwable t = r.getThrown();
    if (t == null) {
      s.append(r.getSourceClassName());
      s.append(":");
      s.append(r.getSourceMethodName());
      s.append(" ");
      s.append(r.getMessage());
      s.append("\n");
    } else {
      if (!r.getMessage().equals("")) {
        s.append(r.getMessage());
        s.append(" ");
      }
      CharArrayWriter c = new CharArrayWriter();
      PrintWriter p = new PrintWriter(c);
      t.printStackTrace(p);
      p.flush();
      s.append(c.toString());
    }
    return s.toString();
  }
}
