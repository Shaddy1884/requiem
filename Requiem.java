
import java.io.*;
import java.util.*;
import java.util.logging.*;
/*
 * Requiem.java
 *
 * Created on May 31, 2008, 4:58 PM
 */
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;


/**
 *
 * @author  Razzmatazz
 */
public class Requiem extends javax.swing.JFrame {
    private static final Logger log = Logger.getLogger(Requiem.class.getName());

    // set by drag&drop in MacConfig, command args in main routine below
    static java.util.List<File> dragDropFiles = null;

    int successes;
    Vector<String> errors;

    /** Creates new form Requiem */
    public Requiem() {
        initComponents();
        successes = 0;
        errors = new Vector<String>();
        new DropTarget(this, new DropTargetAdapter() {
            public void dragEnter(DropTargetDragEvent e) {
              jDropBorder.setVisible(true);
            }
            public void dragExit(DropTargetEvent e) {
              jDropBorder.setVisible(false);
            }
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent e) {
              jDropBorder.setVisible(false);
              try {
                e.acceptDrop(e.getSourceActions()); // TODO: huh?  But it works...
                log.info("dropped " + e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                processDroppedFiles((java.util.List<File>)e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
              } catch (UnsupportedFlavorException f) {
                log.log(Level.WARNING, "drop failed; ignoring " + e, f);
              } catch (IOException f) {
                log.log(Level.WARNING, "drop failed; ignoring " + e, f);
              }
            }
          });
    }

    /** This method is called from within the constructor to
     * initialize the form.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane = new javax.swing.JScrollPane();
        jScrollPane.setPreferredSize(new Dimension(800,600));
        tabFiles = new javax.swing.JTable() {
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
              Component comp = super.prepareRenderer(renderer, row, column);  
              Color c = Color.white;
              if (column == 1) {
                String value = (String) getValueAt(row, 1);
                if (value.equals("queued")) {
                  // white
                } else if (value.equals("looking for DRM") || value.equals("working")) {
                  c = new Color(0xff, 0xff, 0x00); // yellow
                } else if (value.equals("DRM removed")) {
                  c = new Color(0x66, 0xcc, 0x66); // light green
                } else {
                  c = new Color(0xff, 0x99, 0x99); // light red
                }
              }
              comp.setBackground(c);
              return comp;
            }
          };
        tabFiles.setSelectionForeground(new Color(0x00, 0x00, 0xff)); // blue

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Requiem 4.1");

        tabFiles.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
            },
            new String [] {
                "File", "State"
            }
        ) {
            public boolean isCellEditable(int rowIndex, int columnIndex) {
              return false;
            }
        });
        tabFiles.getColumnModel().getColumn(0).setPreferredWidth(1000);
        tabFiles.getColumnModel().getColumn(1).setPreferredWidth(500);
        jScrollPane.setViewportView(tabFiles);
        getContentPane().add(jScrollPane);
        jDropBorder = new JPanel();
        jDropBorder.setBorder(BorderFactory.createLineBorder(new Color(161,204,255), 3));
        jDropBorder.setOpaque(false);
        jDropBorder.setVisible(false);
        setGlassPane(jDropBorder);
        pack();
    }

  private void startDecrypt() {
    (new Thread() {@Override public void run() { doDecryptWorker(); } }).start();
  }
  class GuiNotifier implements Notifier {
    Notifier chain;
    GuiNotifier(Notifier n) {
      chain = n;
    }
    HashMap<File,Integer> row_map = new HashMap<File,Integer>();
    public void check(File a) {
      chain.check(a);
      String filename = a.getName();
      DefaultTableModel model = (DefaultTableModel)tabFiles.getModel();
      if (model.getRowCount() > 0 &&
          model.getValueAt(model.getRowCount() - 1, 1).equals("looking for DRM")) {
        model.setValueAt(filename, model.getRowCount() - 1, 0);
      } else {
        Vector<String> row = new Vector<String>();
        row.add(filename);
        row.add("looking for DRM");
        model.addRow(row);
      }
    }
    public void nodrm(File a) {
      chain.nodrm(a);
    }
    public void queue(File a) {
      chain.queue(a);
      DefaultTableModel model = (DefaultTableModel)tabFiles.getModel();
      model.setValueAt("queued", model.getRowCount() - 1, 1);
      row_map.put(a, model.getRowCount() - 1);
    }
    public void error(File a, String msg) {
      chain.error(a, msg);
      DefaultTableModel model = (DefaultTableModel)tabFiles.getModel();
      model.setValueAt(msg, model.getRowCount() - 1, 1);
      row_map.put(a, model.getRowCount() - 1);
      errors.add(a.getName() + " (" + msg + ")");
    }
    public void noMoreChecks() {
      chain.noMoreChecks();
      // remove the last check we did, if any
      DefaultTableModel model = (DefaultTableModel)tabFiles.getModel();
      if (model.getRowCount() > 0 && 
          model.getValueAt(model.getRowCount() - 1, 1).equals("looking for DRM")) {
        model.removeRow(model.getRowCount() - 1);
      }
    }
    public void work(File a) {
      chain.work(a);
      int row = row_map.get(a);
      tabFiles.getModel().setValueAt("working", row, 1);
    }
    public void success(File a) {
      chain.success(a);
      int row = row_map.get(a);
      tabFiles.getModel().setValueAt("DRM removed", row, 1);
      successes++;
    }
    public void failure(File a, String msg) {
      chain.failure(a, msg);
      if (msg.startsWith("java.lang.RuntimeException: ")) msg = msg.substring(28);
      int row = row_map.get(a);
      tabFiles.getModel().setValueAt(msg, row, 1);
      errors.add(a.getName() + " (" + msg + ")");
    }
  }
  GuiNotifier notifier;
  void addNotifier() {
    UnDrmQueue.notifier = notifier = new GuiNotifier(UnDrmQueue.notifier);
  }
  void processDroppedFiles(java.util.List<File> files) {
    UnDrmQueue.queueBegin();
    UnDrmQueue.queueList(files);
    UnDrmQueue.queueEnd();
  }
  private void doDecryptWorker() {
    try {
      // process initial work, if any
      boolean fromLibrary = false;
      if (dragDropFiles != null) {
        processDroppedFiles(dragDropFiles);
      } else if (!Config.isShiftDown()) {
        String[] args = new String[]{};
        if (System.getProperty("requiem.outputlib") != null) {
          args = new String[]{System.getProperty("requiem.outputlib")};
        }
        ModifyLib.queue(args);
        fromLibrary = true;
      }
      
      // now loop doing and waiting for more work
      while (true) {
        UnDrmQueue.go();
        int msgIcon;
        String finishMessage;
        String trash = System.getProperty("os.name").equals("Mac OS X") ? "trash" : "recycle bin";
        if (successes == 0) {
          finishMessage = "";
        } else if (successes == 1) {
          finishMessage = "Successfully removed DRM from 1 file.";
          finishMessage += "\r\nThe DRM-laden file has been moved to the " + trash + ".";
        } else {
          finishMessage = "Successfully removed DRM from " + successes + " files.";
          finishMessage += "\r\nThe DRM-laden files have been moved to the " + trash + ".";
        }
        if (errors.size() == 0) {
          msgIcon = JOptionPane.INFORMATION_MESSAGE;
          if (successes == 0) {
            finishMessage = fromLibrary ? "No DRM found in library" : "No DRM found in files";
          }
        } else {
          msgIcon = JOptionPane.WARNING_MESSAGE;
          finishMessage += "\r\nThe following files could not be decrypted:";
          final int MAX_ERRORS = 20;
          for (int i = 0; i < errors.size() && i < MAX_ERRORS; i++) {
            finishMessage += "\r\n"+errors.elementAt(i);
          }
          if (errors.size() > MAX_ERRORS) {
            finishMessage += "\r\n...";
          }
        }
        JOptionPane.showMessageDialog(rootPane, finishMessage, "Finished", msgIcon);
        fromLibrary = false;
      }
    } catch (Exception ex) {
      log.log(Level.SEVERE, "", ex);
      JOptionPane.showMessageDialog(rootPane, ex.toString(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }
  
  public static void main(String args[]) {
      Config.guiMods();
      if (args.length > 0) {
        // Windows does drag/drop by adding the file to the command line
        dragDropFiles = new ArrayList<File>();
        for (String file_name : args) {
          log.info("input file " + file_name);
          dragDropFiles.add(new File(file_name));
        }
      }
      java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
          Requiem r = new Requiem();
          r.addNotifier();
          r.setVisible(true);
          r.startDecrypt();
        }
      });
  }
  
  private javax.swing.JScrollPane jScrollPane;
  private javax.swing.JTable tabFiles;
  private JPanel jDropBorder;
}
