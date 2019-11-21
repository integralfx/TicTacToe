import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class TicTacToe extends JFrame {
    private char[][] grid = new char[3][3];
    private JButton[][] gridBtn = new JButton[3][3];
    private char currPlayer = 'O', player;
    private int turnNo = 1;
    private boolean gameOver = false, isConnected = false;
    private ServerSocket serverSocket;
    private Socket opponentSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private JLabel lblInfo;
    private JTextField txtIP;
    private Thread gameThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TicTacToe());
    }

    public TicTacToe() {
        gameThread = new Thread(() -> {
            try {
                while (!gameOver) {
                    if (player != currPlayer)
                        processOpponentMove();

                    Thread.sleep(1);
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        setTitle("Tic Tac Toe");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
        createGUI();
        pack();
        setLocationRelativeTo(null);    // centre window
        setVisible(true);
    }

    private void closeOpponentSocket() throws IOException {
        if (opponentSocket != null && !opponentSocket.isClosed()) {
            System.out.println(
                String.format(
                    "Closing existing connection to %s:%d",
                    opponentSocket.getInetAddress().getHostAddress(),
                    opponentSocket.getPort()
                )
            );
            opponentSocket.close();
        }
    }

    private boolean connectToHost(String hostIP, int port) {
        try {
            closeOpponentSocket();
            opponentSocket = new Socket(hostIP, port);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void setupIOStreams() {
        try {
            out = new ObjectOutputStream(
                new BufferedOutputStream(opponentSocket.getOutputStream())
            );
            out.flush();
            in = new ObjectInputStream(
                new BufferedInputStream(opponentSocket.getInputStream())
            );
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createGUI() {
        JPanel p = new JPanel();
        lblInfo = new JLabel("Input port number");
        p.add(lblInfo);
        add(p);

        p = new JPanel();
        JRadioButton rdoHost = new JRadioButton("Host (O)");
        rdoHost.setSelected(true);
        p.add(rdoHost);
        JTextField txtPort = new JTextField(5);
        txtPort.setToolTipText("Port number used to accept connections");
        p.add(txtPort);
        JButton btnStart = new JButton("Start");
        p.add(btnStart);
        add(p);

        p = new JPanel();
        JRadioButton rdoPlayer = new JRadioButton("Player (X)");
        ButtonGroup group = new ButtonGroup();
        group.add(rdoHost);
        group.add(rdoPlayer);
        p.add(rdoPlayer);
        JTextField txtIP = new JTextField(10);
        txtIP.setToolTipText("Connect to host using <IP>:<port>");
        txtIP.setEnabled(false);
        p.add(txtIP);
        JButton btnConnect = new JButton("Connect");
        btnConnect.setEnabled(false);
        p.add(btnConnect);
        add(p);

        ActionListener l = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == rdoHost) {
                    lblInfo.setText("Input port number");
                    txtPort.setEnabled(true);
                    btnStart.setEnabled(true);
                    txtIP.setEnabled(false);
                    btnConnect.setEnabled(false);
                }
                else if (e.getSource() == btnStart) {
                    String regex = "^\\d{1,5}$";
                    int port = 0;
                    if (!Pattern.matches(regex, txtPort.getText())) {
                        showErrorMsg("Invalid port number");
                        return;
                    }
                    port = Integer.valueOf(txtPort.getText());
                    if (port < 1 || port > 65535) {
                        showErrorMsg("Port must be between 1 and 65535");
                        return;
                    }

                    try {
                        closeOpponentSocket();
                        serverSocket = new ServerSocket(port);
                    }
                    catch (Exception ex) {
                        showErrorMsg("Failed to host on port " + port);
                        ex.printStackTrace();
                        return;
                    }

                    rdoHost.setEnabled(false);
                    txtPort.setEnabled(false);
                    btnStart.setEnabled(false);
                    rdoPlayer.setEnabled(false);
                    txtIP.setEnabled(false);
                    btnConnect.setEnabled(false);

                    new Thread(() -> {
                        synchronized (this) {
                            try {
                                SwingUtilities.invokeLater(() -> 
                                    lblInfo.setText("Waiting for opponent..."));
                                opponentSocket = serverSocket.accept();
                                setupIOStreams();
                                isConnected = true;
                            }
                            catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> 
                                    lblInfo.setText("Error"));
                                ex.printStackTrace();
                                isConnected = false;
                            }
                        }
                    }).start();

                    new Thread(() -> {
                        synchronized (this) {
                            try {
                                while (!isConnected) Thread.sleep(1);

                                showSuccessMsg(
                                    String.format(
                                        "Opponent connected from %s:%d", 
                                        opponentSocket.getInetAddress().getHostAddress(),
                                        opponentSocket.getPort()
                                    )
                                );
            
                                player = 'O';
                                SwingUtilities.invokeLater(() -> {
                                    lblInfo.setText("Turn 1 - Your turn");
                                    setGridEnabled(true);
                                });
            
                                gameThread.start();
                            }
                            catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }).start();
                }
                else if (e.getSource() == rdoPlayer) {
                    lblInfo.setText("Input host IP address and port");
                    txtPort.setEnabled(false);
                    btnStart.setEnabled(false);
                    txtIP.setEnabled(true);
                    btnConnect.setEnabled(true);
                }
                else if (e.getSource() == btnConnect) {
                    String regex = 
                        "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\" + 
                        "d{1,3}):(\\d{1,5})$";
                    Pattern p = Pattern.compile(regex);
                    Matcher m = p.matcher(txtIP.getText());
                    if (!m.matches()) {
                        showErrorMsg("Invalid IP.");
                        return;
                    }

                    final String host = m.group(1);
                    final int port = Integer.valueOf(m.group(2));
                    lblInfo.setText(String.format("Connecting to %s:%d...",
                                                  host, port));

                    rdoHost.setEnabled(false);
                    txtPort.setEnabled(false);
                    btnStart.setEnabled(false);
                    rdoPlayer.setEnabled(false);
                    txtIP.setEnabled(false);
                    btnConnect.setEnabled(false);

                    new Thread(() -> {
                        synchronized (this) {
                            if (connectToHost(host, port)) {
                                setupIOStreams();
        
                                showSuccessMsg(
                                    String.format("Connected to %s:%d", 
                                                  host, port)
                                );
        
                                player = 'X';

                                SwingUtilities.invokeLater(() -> {
                                    lblInfo.setText("Turn 1 - Opponent's turn");
                                    setGridEnabled(true);
                                });
                                
                                gameThread.start();
                            }
                            else {
                                showErrorMsg(
                                    String.format("Failed to connect to %s:%d", host, port)
                                );

                                SwingUtilities.invokeLater(() -> {
                                    if (rdoHost.isSelected()) {
                                        rdoHost.setEnabled(true);
                                        txtPort.setEnabled(true);
                                        btnStart.setEnabled(true);
                                    }
                                    else if (rdoPlayer.isSelected()) {
                                        rdoPlayer.setEnabled(true);
                                        txtIP.setEnabled(true);
                                        btnConnect.setEnabled(true);
                                    }
                                });
                            }
                        }
                    }).start();
                }
            }
        };
        rdoHost.addActionListener(l);
        btnStart.addActionListener(l);
        rdoPlayer.addActionListener(l);
        btnConnect.addActionListener(l);

        createGrid();
    }

    private void createGrid() {
        JPanel panelGrid = new JPanel(new GridLayout(3, 3));
        panelGrid.setPreferredSize(new Dimension(300, 300));
        for (int i = 0; i < 3 * 3; i++) {
            int r = i / 3, c = i % 3;
            grid[r][c] = ' ';

            JButton btn = new JButton(" ");
            btn.setEnabled(false);
            gridBtn[r][c] = btn;
            btn.setFont(new Font("Arial", Font.PLAIN, 50));
            btn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (gameOver) return;

                    if(player == currPlayer)
                        processPlayerMove();
                }

                private void processPlayerMove() {
                    try {
                        out.writeObject(new Position(r, c));
                        out.flush();
            
                        btn.setEnabled(false);
                        btn.setText(String.valueOf(player));
            
                        grid[r][c] = player;
            
                        updatePlayer();
                    }
                    catch (IOException e) {
                        showErrorMsg("Lost connection to opponent");
                        System.exit(-1);
                    }
                }
            }); 
            panelGrid.add(btn);
        }
        add(panelGrid);
    }

    private void setGridEnabled(boolean enable) {
        for (int i = 0; i < gridBtn.length; i++) {
            for (int j = 0; j < gridBtn[i].length; j++) {
                gridBtn[i][j].setEnabled(enable);
            }
        }
    }

    private synchronized void updatePlayer() {
        char winner = getWinner();
        if (winner != ' ') {
            lblInfo.setText("Game over");
            JOptionPane.showMessageDialog(
                getContentPane(),
                (winner == player) ? "You win." : "You lose.", 
                "Game over", 
                JOptionPane.INFORMATION_MESSAGE
            );
            gameOver = true;
        }
        else if (turnNo == 9) {
            lblInfo.setText("Game over");
            JOptionPane.showMessageDialog(
                getContentPane(),
                "Draw.", 
                "Game over", 
                JOptionPane.INFORMATION_MESSAGE
            );
            gameOver = true;
        }
        else {
            if (currPlayer == 'O') currPlayer = 'X';
            else currPlayer = 'O';

            if (player == currPlayer)
                lblInfo.setText(String.format("Turn %d - Your turn", turnNo));
            else
                lblInfo.setText(String.format("Turn %d - Opponent's turn", turnNo));

            turnNo++;
        }
    }

    private void processOpponentMove() {
        try {
            Position p = (Position)in.readObject();
            
            SwingUtilities.invokeLater(() -> {
                gridBtn[p.row][p.col].setEnabled(false);
                gridBtn[p.row][p.col].setText(
                    String.valueOf(currPlayer)
                );

                grid[p.row][p.col] = currPlayer;

                updatePlayer();
            });
        }
        catch (IOException ex) {
            showErrorMsg("Lost connection to opponent");
            System.exit(-1);
        }
        catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            System.exit(-1);
        }
    }

    private char getWinner() {
        for (int i = 0; i < 3; i++) {
            int sumRow = 0, sumCol = 0, sumDiagTL = 0, sumDiagTR = 0;
            for (int j = 0; j < 3; j++) {
                // rows
                switch (grid[i][j]) {
                    case 'O': sumRow += 1; break;
                    case 'X': sumRow += -1; break;
                    default: sumRow += 0;
                }

                // columns
                switch (grid[j][i]) {
                    case 'O': sumCol += 1; break;
                    case 'X': sumCol += -1; break;
                    default: sumCol += 0;
                }

                // diagonal starting from top left
                switch (grid[j][j]) {
                    case 'O': sumDiagTL += 1; break;
                    case 'X': sumDiagTL += -1; break;
                    default: sumDiagTL += 0;
                }

                // diagonal starting from top right
                switch (grid[j][2 - j]) {
                    case 'O': sumDiagTR += 1; break;
                    case 'X': sumDiagTR += -1; break;
                    default: sumDiagTR += 0;
                }
            }

            if (sumRow == 3 || sumCol == 3 || sumDiagTL == 3 || sumDiagTR == 3)
                return 'O';

            if (sumRow == -3 || sumRow == -3 || sumDiagTL == -3 || sumDiagTR == -3)
                return 'X';
        }

        return ' ';
    }

    private void printGrid() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if(grid[i][j] == ' ')
                    System.out.print("- ");
                else System.out.print(grid[i][j] + " ");
            }

            System.out.println();
        }
    }

    private void showErrorMsg(String msg) {
        JOptionPane.showMessageDialog(
            getContentPane(),
            msg, 
            "Error", 
            JOptionPane.ERROR_MESSAGE
        );
    }

    private void showSuccessMsg(String msg) {
        JOptionPane.showMessageDialog(
            getContentPane(),
            msg, 
            "Success", 
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}

class Position implements Serializable {
    public int row, col;
    public Position(int r, int c) {
        row = r;
        col = c;
    }
}