package memorymatchgame;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;

/**
 *
 * @author igor
 */
public class MemoryMatchGame extends Application implements Runnable{
    //Networking
    private String host = "127.0.0.1";
    private int port = 12345;
    private ServerSocket serverSocket;
    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private Thread thread;
    private boolean isServer = false;
    private boolean clientConnected = false;
    private boolean lostConnection = false;
    private int errorCount = 0;
    
    //Game logic
    private final String[] wordList = {"Manzana", "Pera", "Cereza", "Uva",
        "Naranja", "Platano", "Ciruela", "Frezon"};
    private Map<String, Integer> wordCount = new HashMap<>();
    private Random random = new Random();
    private boolean isMyTurn = false;
    private String[] board;
    private int myMatchCount = 0, opponentMatchCount = 0;
    private ToggleButton selected1, selected2;
    private ToggleButton lastSelected;
    private boolean isGameStateChanged = false;
    private boolean isGameOver = false;
    
    //GUI
    private final int rowCount;
    private final int columnCount;
    private static final int TILE_SIZE = 100;
    private TilePane tilePane;
    private Stage stage;
    
    public MemoryMatchGame(){
        this.rowCount = (int)Math.ceil(Math.sqrt(2 * this.wordList.length));
        this.columnCount = this.rowCount;
        for(String word: this.wordList){
            this.wordCount.put(word, 0);
        }
        
        if(!connectToServer()){
            initServer();
            //Set the board up
            this.board = new String[2 * this.wordList.length];
            outer:
            for(int i = 0; i < this.board.length; ++i){
                while(true){
                    int randIndex = this.random.nextInt(this.wordList.length);
                    String word = this.wordList[randIndex];
                    int count = this.wordCount.get(word);
                    if(count < 2){
                        ++count;
                        this.wordCount.put(word, count);
                        this.board[i] = word;
                        continue outer;
                    }
                }
            }
        } else {
            fetchGameBoardFromServer();
        }
        
        this.thread = new Thread(this, "Demo");
        this.thread.start();
    }
    
    private void initServer(){
        System.out.println("Initializing the server...");
        try {
            this.serverSocket = new ServerSocket(this.port);
            this.isServer = true;
            this.isMyTurn = true;
        } catch (IOException ex) {
            Logger.getLogger(MemoryMatchGame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
    }
    
    private boolean connectToServer(){
        System.out.println("Trying to connect to server...");
        try {
            this.socket = new Socket(this.host, this.port);
            this.oos = new ObjectOutputStream(this.socket.getOutputStream());
            this.ois = new ObjectInputStream(this.socket.getInputStream());
            this.isServer = false;
            this.clientConnected = true;
            System.out.println("Successfully connected to the server "
                    + this.host + ":" + this.port);
        } catch (IOException ex) {
            System.out.println("Unable to connect to server "
                    + this.host + ":" + this.port);
            Logger.getLogger(MemoryMatchGame.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        return this.socket != null;
    }

    private void waitForClient(){
        try {
            this.socket = this.serverSocket.accept();
            this.oos = new ObjectOutputStream(this.socket.getOutputStream());
            this.ois = new ObjectInputStream(this.socket.getInputStream());
            this.clientConnected = true;
            this.isServer = true;
            this.isMyTurn = true;
            
            sendBoardStateToTheClient();
            disableTiles(false);
            
        } catch (IOException ex) {
            Logger.getLogger(MemoryMatchGame.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }
    
    private void sendBoardStateToTheClient(){
        try {
            this.oos.writeObject(this.board);
            this.oos.flush();
        } catch (IOException ex) {
            Logger.getLogger(MemoryMatchGame.class.getName()).log(Level.SEVERE,
                    null, ex);
            ++this.errorCount;
        }
    }
    
    private void fetchGameBoardFromServer(){
        this.board = new String[2 * this.wordList.length];
        try {
            String[] fromServer = (String[])this.ois.readObject();
            this.board = fromServer.clone();
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(MemoryMatchGame.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }
    
    private void gameTick(){
        if(this.errorCount > 10){
            this.lostConnection = true;
            setWindowTitle("Unable to communicate with opponent");
        }
        updateBoardState();
        if(this.isMyTurn){
            setWindowTitle("My turn");
        } else {
            setWindowTitle("Opponent's turn");
        }
    }
    
    private void updateBoardState(){
        ToggleButton selectedTile = null;
        if(this.isMyTurn){
            if(this.isGameStateChanged){
                this.isGameStateChanged = false;
                selectedTile = this.lastSelected;
                selectedTile.setDisable(true);
                try {
                    this.oos.writeInt(getTileIndex(selectedTile));
                    this.oos.flush();
                } catch (IOException ex) {
                    Logger.getLogger(MemoryMatchGame.class.getName())
                            .log(Level.SEVERE, null, ex);
                    ++this.errorCount;
                }
            }
        } else {
            if(this.isGameStateChanged){
                try {
                    int tileIndex = this.ois.readInt();
                    selectedTile = (ToggleButton) this.tilePane.getChildren()
                            .get(tileIndex);
                    selectedTile.setSelected(true);
                } catch (IOException ex) {
                    Logger.getLogger(MemoryMatchGame.class.getName())
                            .log(Level.SEVERE, null, ex);
                    ++this.errorCount;
                }
            }
        }
        
        if(this.selected1 == null){
            this.selected1 = selectedTile;
        } else if(this.selected2 == null){
            this.selected2 = selectedTile;
        } else {
            checkMatch();
        }
    }
    
    private void checkMatch(){
        disableTiles(true);
        if(this.selected1.getText().equalsIgnoreCase(this.selected2.getText())){
            disableTiles(false);
            highlightMatch(isMyTurn);
            checkGameOver();
        } else {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(MemoryMatchGame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
            this.selected1.setSelected(false);
            this.selected2.setSelected(false);
            this.isMyTurn = !this.isMyTurn;
            disableTiles(!this.isMyTurn);
            if(this.isMyTurn){
                setWindowTitle(" my turn.");
                disableTiles(false);
            } else {
                setWindowTitle(" opponent's turn.");
                disableTiles(true);
            }
        }
        this.selected1 = null;
        this.selected2 = null;
    }
    
    private void checkGameOver(){
        if(this.myMatchCount + this.opponentMatchCount < this.wordList.length){
            this.isGameOver = false;
        } else {
            if(this.myMatchCount > this.opponentMatchCount){
                setWindowTitle(" winner!");
            } else if(this.myMatchCount < this.opponentMatchCount){
                setWindowTitle(" loser!");
            } else {
                setWindowTitle(" draw!");
            }
            this.isGameOver = true;
        }
    }
    
    private void highlightMatch(boolean myMatch){
        selected1.getStyleClass().clear();
        selected2.getStyleClass().clear();
        if(myMatch){
            selected1.getStyleClass().add("my-match");
            selected2.getStyleClass().add("my-match");
            ++this.myMatchCount;
        } else {
            selected1.getStyleClass().add("op-match");
            selected2.getStyleClass().add("op-match");
            ++this.opponentMatchCount;
        }
    }
    
    private int getTileIndex(ToggleButton btn){
        int index = 0;
        for(Node node: this.tilePane.getChildren()){
            if(node.equals(btn)){
                return index;
            }
            ++index;
        }
        return index;
    }
    
    private void disableTiles(boolean disable){
        this.tilePane.getChildren().stream()
                .filter((node) -> (node instanceof ToggleButton))
                .map((node) -> (ToggleButton)node)
                .filter((btn) -> (!btn.isSelected()))
                .forEachOrdered((btn) -> {
                    btn.setSelected(disable);
        });
    }
    
    private ToggleButton createButton(int i){
        ToggleButton btn = new ToggleButton(this.board[i]);
        btn.setPrefSize(TILE_SIZE, TILE_SIZE);
        btn.setOnAction(e -> {
            ToggleButton sender = (ToggleButton)e.getSource();
            this.lastSelected = sender;
            this.isGameStateChanged = true;
            System.out.println("Button clicked");
        });
        return btn;
    }
    
    private void setWindowTitle(String title){
        String prefix = isServer ? "Server: " : "Client: ";
        if(this.stage != null){
            this.stage.setTitle(prefix + title);
        }
    }
    
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.tilePane = new TilePane();
        for(int i = 0; i < this.board.length; ++i){
            this.tilePane.getChildren().add(createButton(i));
        }
        disableTiles(true);
        StackPane root = new StackPane();
        root.getChildren().add(this.tilePane);
        Scene scene = new Scene(root, this.columnCount * TILE_SIZE,
                this.rowCount * TILE_SIZE);
        scene.getStylesheets().add(this.getClass().getResource("Styles.css")
                .toExternalForm());
        if(this.isServer){
            setWindowTitle("Waiting for client");
        } else {
            setWindowTitle("Connected to the server");
        }
        this.stage.setScene(scene);
        this.stage.show();
    }
    
    @Override
    public void run() {
        if(this.isServer && !this.clientConnected){
            waitForClient();
        }
        while(!this.isGameOver){
            gameTick();
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
        MemoryMatchGame game = new MemoryMatchGame();
    }
}
