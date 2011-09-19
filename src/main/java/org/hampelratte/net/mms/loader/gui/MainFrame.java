package org.hampelratte.net.mms.loader.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.hampelratte.net.mms.asf.io.ASFInputStream;
import org.hampelratte.net.mms.asf.objects.ASFFilePropertiesObject;
import org.hampelratte.net.mms.asf.objects.ASFObject;
import org.hampelratte.net.mms.asf.objects.ASFToplevelHeader;
import org.hampelratte.net.mms.client.Client;
import org.hampelratte.net.mms.client.listeners.MMSMessageListener;
import org.hampelratte.net.mms.client.listeners.MMSPacketListener;
import org.hampelratte.net.mms.data.MMSHeaderPacket;
import org.hampelratte.net.mms.data.MMSMediaPacket;
import org.hampelratte.net.mms.data.MMSPacket;
import org.hampelratte.net.mms.io.MMSFileDumper;
import org.hampelratte.net.mms.io.RemoteException;
import org.hampelratte.net.mms.io.util.HRESULT;
import org.hampelratte.net.mms.messages.MMSMessage;
import org.hampelratte.net.mms.messages.server.ReportEndOfStream;
import org.hampelratte.net.mms.messages.server.ReportStreamSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainFrame extends JFrame implements ActionListener, MMSMessageListener, MMSPacketListener {

    private static transient Logger logger = LoggerFactory.getLogger(MainFrame.class);

    private JTextField tfUrl, tfDir;
    private JLabel lThroughput;
    private DecimalFormat df = new DecimalFormat("0.00");
    private JButton bBrowse, bStart;
    private JProgressBar progress;

    private boolean running = false;
    private long packetCount, packetReadCount;

    private Client client;

    public MainFrame() {
        initGUI();
    }

    private void initGUI() {
        setSize(600, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("MMS4J Loader");

        setLayout(new BorderLayout());
        JPanel panel = new JPanel(new GridBagLayout());
        add(panel, BorderLayout.CENTER);

        float WEIGHT_LIGHT = 0.2f;
        float WEIGHT_HEAVY = 0.8f;

        // url label and textfield
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = WEIGHT_LIGHT;
        panel.add(new JLabel("URL"), gbc);

        gbc.gridx = 1;
        gbc.weightx = WEIGHT_HEAVY;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        tfUrl = new JTextField();
        panel.add(tfUrl, gbc);

        // directory label, textfield and button
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = WEIGHT_LIGHT / 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Destination directory"), gbc);

        gbc.gridx = 1;
        gbc.weightx = WEIGHT_HEAVY;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        tfDir = new JTextField();
        panel.add(tfDir, gbc);

        gbc.gridx = 2;
        gbc.weightx = WEIGHT_LIGHT / 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        bBrowse = new JButton("Browse...");
        bBrowse.addActionListener(this);
        panel.add(bBrowse, gbc);

        // progress bar and label
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = WEIGHT_LIGHT;
        panel.add(new JLabel("Progress"), gbc);

        gbc.gridx = 1;
        gbc.weightx = WEIGHT_HEAVY;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        progress = new JProgressBar(0, 100);
        progress.setIndeterminate(false);
        progress.setStringPainted(true);
        progress.setValue(0);
        panel.add(progress, gbc);

        // throughput
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = WEIGHT_LIGHT;
        panel.add(new JLabel("Throughput"), gbc);

        gbc.gridx = 1;
        gbc.weightx = WEIGHT_HEAVY;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        lThroughput = new JLabel();
        panel.add(lThroughput, gbc);

        // start button
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.weightx = WEIGHT_LIGHT;
        gbc.fill = GridBagConstraints.NONE;
        bStart = new JButton("Download");
        bStart.addActionListener(this);
        panel.add(bStart, gbc);

        tfUrl.setText("mms://apasf.apa.at/cms-worldwide/2011-09-18_1100_sd_02_ZIB_____2913225__o__0000823759__s2917803___hr_ORF2HiRes_10595712P_11051213P.wmv");
        // tfUrl.setText("mms://a1014.v1252931.c125293.g.vm.akamaistream.net/7/1014/125293/v0001/wm.od.origin.zdf.de.gl-systemhaus.de/none/zdf/11/05/110530_karriere_kuss_37g_vh.wmv");
        tfDir.setText("/tmp");

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == bBrowse) {
            JFileChooser jfc = new JFileChooser();
            jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int selection = jfc.showOpenDialog(this);
            if (selection == JFileChooser.APPROVE_OPTION) {
                System.out.println("Save file in " + jfc.getSelectedFile());
                tfDir.setText(jfc.getSelectedFile().getAbsolutePath());
            }
        } else if (e.getSource() == bStart) {
            running = !running;
            if (running) {
                setUserInteractionEnabled(false);

                String uri = tfUrl.getText();
                String dir = tfDir.getText();
                logger.info("Trying to download {} to directory {}", uri, dir);
                try {
                    startDownload(uri, dir);
                } catch (FileNotFoundException exc) {
                    reset();
                    String msg = "Output directory does not exist";
                    logger.error(msg, exc);
                    progress.setString(msg);
                    running = false;
                    setUserInteractionEnabled(true);
                } catch (URISyntaxException exc) {
                    reset();
                    String msg = "Given URI is invalid";
                    logger.error(msg, exc);
                    progress.setString(msg);
                    running = false;
                    setUserInteractionEnabled(true);
                } catch (Exception exc) {
                    reset();
                    logger.error("Unexpected error", exc);
                    String msg = exc.getLocalizedMessage() != null ? exc.getLocalizedMessage() : exc.getClass().getSimpleName();
                    progress.setString(msg);
                    running = false;
                    setUserInteractionEnabled(true);
                }
            } else {
                client.disconnect(new IoFutureListener<IoFuture>() {
                    @Override
                    public void operationComplete(IoFuture arg0) {
                        running = false;
                        setUserInteractionEnabled(true);
                        reset();
                    }
                });
            }
        }
    }

    private void startDownload(String mmsUri, final String destDir) throws Exception {
        reset();
        bStart.setText("Cancel");
        progress.setIndeterminate(true);
        progress.setString("Connecting to server");

        URI uri = new URI(mmsUri);
        client = new Client(uri);

        File path = new File(uri.getPath());
        String filepath = path.getParentFile().getPath();
        if (filepath.startsWith("/")) {
            filepath = filepath.substring(1);
        }
        final String filename = path.getName();

        // dump the received header and media packets to a file
        File dump = new File(destDir, filename);
        MMSFileDumper dumper = new MMSFileDumper(dump);
        client.addMessageListener(dumper);
        client.addMessageListener(this);
        client.addPacketListener(dumper);
        client.addPacketListener(this);

        client.addAdditionalIoHandler(new IoHandlerAdapter() {
            private int connectExceptionCount = 0;

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
                // ignore the first connect exception, because the client will try another connection
                // with the mms over http protocol
                if (cause instanceof ConnectException) {
                    if (++connectExceptionCount < 2) {
                        return;
                    }
                }

                logger.error("An error occured", cause);
                reset();
                progress.setString(cause.getLocalizedMessage());
                setUserInteractionEnabled(true);
                running = false;

                if (cause.getCause() != null && cause.getCause() instanceof RemoteException) {
                    RemoteException re = (RemoteException) cause.getCause();
                    String msg = HRESULT.hrToHumanReadable(re.getHr());
                    progress.setString(msg);
                }

                client.disconnect(new IoFutureListener<IoFuture>() {
                    @Override
                    public void operationComplete(IoFuture arg0) {
                        // nothing to do
                    }
                });
            }
        });

        // finally connect to the server and start the download
        // if the connection has been established
        client.connect(null);
    }

    private void reset() {
        setProgess(0);
        progress.setString("");
        progress.setIndeterminate(false);
        packetReadCount = 0;
        packetCount = 0;
        lThroughput.setText("");
        bStart.setText("Download");
    }

    private void setUserInteractionEnabled(boolean enabled) {
        tfUrl.setEnabled(enabled);
        tfDir.setEnabled(enabled);
        bBrowse.setEnabled(enabled);
    }

    @Override
    public void packetReceived(MMSPacket mmspacket) {
        if (mmspacket instanceof MMSHeaderPacket) {
            MMSHeaderPacket hp = (MMSHeaderPacket) mmspacket;
            try {
                ByteArrayInputStream bin = new ByteArrayInputStream(hp.getData());
                ASFInputStream asfin = new ASFInputStream(bin);
                ASFObject asfo = asfin.readASFObject();
                if (asfo instanceof ASFToplevelHeader) {
                    ASFToplevelHeader asfHeader = (ASFToplevelHeader) asfo;
                    logger.debug("ASF header: {}", asfHeader);
                    ASFFilePropertiesObject fileprops = (ASFFilePropertiesObject) asfHeader.getNestedHeader(ASFFilePropertiesObject.class);
                    if (fileprops != null) {
                        packetCount = fileprops.getDataPacketCount();
                        progress.setIndeterminate(false);
                        logger.info("Stream consists of {} packets", packetCount);
                        logger.debug("File properties {}", fileprops.toString());
                    } else {
                        packetCount = -1;
                    }
                }
            } catch (Exception e) {
                logger.warn("Ignoring unknown ASF header object", e);
            }
        } else if (mmspacket instanceof MMSMediaPacket) {
            packetReadCount++;
            lThroughput.setText(df.format(client.getSpeed()) + " KiB/s");
            int p = (int) ((double) packetReadCount / (double) packetCount * 100);
            setProgess(p);
            logger.info("{} / {} packets read. {}", new Object[] { packetReadCount, packetCount, lThroughput.getText() });
        }
    }

    private void setProgess(int percentage) {
        progress.setValue(percentage);
        progress.setString(percentage + " %");
    }

    @Override
    public void messageReceived(MMSMessage command) {
        if (command instanceof ReportStreamSwitch) {
            ReportStreamSwitch rss = (ReportStreamSwitch) command;
            logger.debug("StreamSwitch result: {}", rss.getHr());

            // great, we can start the streaming
            long startPacket = 0;

            // start the streaming
            client.startStreaming(startPacket);
        } else if (command instanceof ReportEndOfStream) {
            reset();
            setProgess(100);
            progress.setString("Finished");
            lThroughput.setText("");
            setUserInteractionEnabled(true);
            running = false;

            logger.info("Received end of stream. Closing connection.");
            client.disconnect(new IoFutureListener<IoFuture>() {
                @Override
                public void operationComplete(IoFuture arg0) {
                    logger.info("Connection has been closed");
                }
            });
        } else {
            // here you may react on other messages
        }
    }
}
