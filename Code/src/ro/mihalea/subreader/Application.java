package ro.mihalea.subreader;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.List;

import static java.awt.EventQueue.getCurrentEvent;
import static java.awt.EventQueue.invokeLater;

/**
 * Created with IntelliJ IDEA.
 * User: Mircea
 * Date: 9/1/13
 * Time: 6:45 PM
 */
public class Application {

    private final String title = "SubReader";
    private final Dimension size = new Dimension(960, 600);

    private JFrame frame;
    private DefaultListModel<Entry> listModel;


    private JList list;
    private JTextField input;
    private JLabel statusField;
    private JButton button;

    private Image icon;
    private TrayIcon normalTray, newTray;
    private boolean trayPreferred = false;
    private boolean trayIsSupported = false;

    private Status status = Status.stopped;

    private int unreadCount = 0, lastCount = 0;

    private Timer updateTimer;
    private int delay = 1; //in minutes

    private String subReddit = null;

    private List<Entry> newPosts;

    public Application(){
        frame = new JFrame();

        listModel = new DefaultListModel<Entry>();

        newPosts = new ArrayList<Entry>();

        updateTimer = new Timer(5000, new TimedAction());
        updateTimer.setInitialDelay(500);

        setupUI();
    }


    private void setupUI() {
        trayIsSupported = setupTray();
        setupMenu();
        setupContent();
        setupFrame();

    }

    private void setupContent() {
        JPanel content = new JPanel();
        content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        content.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        JLabel nameLabel = new JLabel("Subreddit: ");
        c.fill = GridBagConstraints.NONE;
        c.weightx = 1;
        c.weighty = 1;
        c.gridx = 0;
        c.gridy = 0;
        content.add(nameLabel, c);


        input = new JTextField();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.ipady = 6;
        c.weightx = 25;
        c.weighty = 1;
        c.gridx = 1;
        c.gridy = 0;
        content.add(input, c);

        button = new JButton("Start");
        button.addActionListener(new ButtonListener());
        c.ipady = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0.25;
        c.gridx = 2;
        content.add(button, c);

        list = new JList(listModel);
        list.setCellRenderer(new ListRenderer());
        list.addMouseListener(new ListInteraction());
        JScrollPane scrollPane = new JScrollPane(list);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 15;
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 3;
        content.add(scrollPane, c);

        statusField = new JLabel("Status: " + status);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 2;
        c.weighty = 1;
        c.weightx = 1;
        c.gridwidth = 2;
        content.add(statusField, c);

        frame.setContentPane(content);

    }

    private void setupMenu() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu("File");

        JRadioButtonMenuItem toTray = new JRadioButtonMenuItem("Minimize to tray");
        toTray.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                trayPreferred = !trayPreferred;
            }
        });


        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        file.add(toTray);
        file.add(exit);


        JMenu help = new JMenu("Help");

        JMenuItem about = new JMenuItem("About");
        about.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(frame, "Software created by Mihalea Mircea", "About",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        help.add(about);

        JMenu edit = new JMenu("Edit");

        JMenuItem markRead = new JMenuItem("Mark as read");
        markRead.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Enumeration<Entry> enumeration = listModel.elements();
                while(enumeration.hasMoreElements()) {
                    enumeration.nextElement().newPost = false;
                    list.repaint();
                }
            }
        });

        edit.add(markRead);

        menuBar.add(file);
        menuBar.add(edit);
        menuBar.add(help);


        frame.setJMenuBar(menuBar);
    }

    private void setupFrame() {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);

                SystemTray.getSystemTray().remove(normalTray);
                SystemTray.getSystemTray().remove(newTray);
            }

            @Override
            public void windowIconified(WindowEvent e) {
                super.windowIconified(e);
                if(trayIsSupported && trayPreferred){
                    try {
                        //SystemTray.getSystemTray().add(normalTray);
                        frame.setVisible(false);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });


        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.pack();
        frame.setLocation(screen.width / 2 - size.width / 2, screen.height / 2 - size.height / 2);
        frame.setTitle(title);
        frame.setSize(size);
        frame.setIconImage(icon);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);

    }

    private boolean setupTray() {
        if(SystemTray.isSupported() == false)
            return false;

        try {
            icon = ImageIO.read(getClass().getResource("images/normal.gif"));
            normalTray = new TrayIcon(icon, "SubReader");
            normalTray.setImageAutoSize(true);
            normalTray.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() >= 2)
                        bringToFront();
                }
            });

            SystemTray.getSystemTray().add(normalTray);

            newTray = new TrayIcon(ImageIO.read(getClass().getResource("images/unread.gif")), "SubReader");
            newTray.setImageAutoSize(true);
            newTray.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount() >= 2)
                        bringToFront();
                }
            });


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return true;
    }


    private void updateTray() {
        SystemTray systemTray = SystemTray.getSystemTray();

        try {
            if(unreadCount > 0 && lastCount == 0) {
                systemTray.add(newTray);
                systemTray.remove(normalTray);
            } else if (unreadCount == 0 && lastCount > 0){
                systemTray.add(normalTray);
                systemTray.remove(newTray);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        lastCount = unreadCount;
    }

    private void bringToFront() {
        frame.setExtendedState(Frame.NORMAL);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        frame.setAlwaysOnTop(false);
    }

    private void updateStatus(){
        statusField.setText("Status: " + status);
    }



    private void startWorker(String subReddit) {
        listModel.clear();
        this.subReddit = subReddit;
        updateTimer.start();
        input.setEnabled(false);
    }

    private void stopWorker(){
        updateTimer.stop();
        input.setEnabled(true);
    }


    private static boolean isReachable(String address){
        try {
            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            Object data = conn.getContent();
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    private String getData(String subReddit) throws Exception{
        String address = "http://www.reddit.com/r/" + subReddit + "/new/.json";
        if(isReachable(address) == false)
            throw new Exception("Could not contact reddit");

        URL url = new URL(address);
        InputStream stream = url.openStream();
        StringBuffer buffer = new StringBuffer("");

        int inByte = stream.read();
        while(inByte != -1){
            buffer.append((char)inByte);
            inByte = stream.read();
        }

        return buffer.toString();
    }

    private void openWebpage(URL url){
        try {
            openWebpage(url.toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void openWebpage(URI uri){
        if(Desktop.isDesktopSupported()){
            Desktop desktop = Desktop.getDesktop();

            if(desktop.isSupported(Desktop.Action.BROWSE)){
                try {
                    desktop.browse(uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    enum Status {
        stopped,
        waiting,
    }


    private class Entry{
        public String title, url;
        public boolean newPost;


        public Entry(String t, String u){
            title = t;
            url = u;
            newPost = true;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public boolean equals(Object obj) {
            return url.equals(((Entry)obj).url);
        }
    }

    private class Time{
        int m,s;

        public Time(){
            reset();
        }

        public void reset(){
            m = s = 0;
        }

        public void tick(){
            s++;
            if(s>60){
                s = 0;
                m++;
            }
        }

        @Override
        public String toString() {
            return (m > 9 ? m : ("0" + m)) + ":" + (s > 9 ? s : ("0" + s));
        }
    }


    private class ListRenderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            Entry e = listModel.getElementAt(index);

            if(e.newPost) {
                if(isSelected) {
                    c.setForeground(Color.WHITE);
                    c.setBackground(Color.RED);
                } else {
                    c.setForeground(Color.RED);
                    c.setBackground(Color.WHITE);
                }
            } else {
                c.setForeground(Color.BLACK);
                c.setBackground(Color.WHITE);
            }

            return c;
        }
    }

    private class ListInteraction extends MouseAdapter{
        @Override
        public void mouseClicked(MouseEvent e) {
            JList source = (JList)e.getSource();
            int index = -1;
            if(e.getClickCount() == 2)
                index = source.getSelectedIndex();
            if(index >= 0) {
                Entry selected = listModel.getElementAt(index);
                try {
                    openWebpage(new URL("http://www.reddit.com" + selected.url));
                    selected.newPost = false;
                    unreadCount--;
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                }
            }

        }
    }

    private class ButtonListener implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                String raw = input.getText();
                String trimmed = raw.trim();
                if(status == Status.stopped) {
                    if(trimmed.isEmpty() == false){
                        startWorker(trimmed);
                        status = Status.waiting;
                        button.setText("Stop");
                        updateStatus();
                    }
                } else if (status == Status.waiting) {
                    stopWorker();
                    status = Status.stopped;
                    button.setText("Start");
                    updateStatus();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private class TimedAction implements ActionListener{

        @Override
        public void actionPerformed(ActionEvent e) {
            new Worker().execute();
        }
    }

    private class Worker extends SwingWorker<String, String>{

        @Override
        protected String doInBackground() throws Exception {
            String data = getData(subReddit);

            Map json = new Gson().fromJson(data, Map.class);
            LinkedTreeMap temp = (LinkedTreeMap) json.get("data");
            List<LinkedTreeMap> list = (List<LinkedTreeMap>) temp.get("children");


            newPosts.clear();
            for(LinkedTreeMap node : list){
                LinkedTreeMap deeper = (LinkedTreeMap) node.get("data");
                Entry e = new Entry((String) deeper.get("title"), (String) deeper.get("permalink"));
                if(listModel.contains(e) == false)
                    newPosts.add(e);
            }

            System.out.println(listModel.size());

            return "void";
        }

        @Override
        protected void done() {

            int count = 0;

            for(Entry e : newPosts)
                listModel.addElement(e);

            Enumeration<Entry> enumeration = listModel.elements();
            while(enumeration.hasMoreElements()) {
                Entry e = enumeration.nextElement();
                if(e.newPost == true)
                    count++;
            }

            unreadCount = count;
            updateTray();
            if(count > 0) {
                invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        bringToFront();
                    }
                });
            }
        }
    }




}
