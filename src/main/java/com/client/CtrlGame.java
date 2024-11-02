package com.client;


import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import org.json.JSONObject;
//import org.w3c.dom.Text;
import javafx.scene.text.Text;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.layout.AnchorPane;


public class CtrlGame implements Initializable {


    @FXML
    private Canvas canvas;
    @FXML
    private Text textTorn;
    @FXML
    private Text stringUser;
    @FXML
    private Text counter;

    @FXML
    private AnchorPane anchor;

   // public static String stringTorn;
    private GraphicsContext gc;
    private Boolean showFPS = false;


    private PlayTimer animationTimer;
    private PlayGrid grid;


    public Map<String, JSONObject> clientMousePositions = new HashMap<>();
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    // selectableObjects == ships
    public static Map<String, Map<String, JSONObject>> positionShips = new HashMap<>();

    private String ship = "";

    //public static Map<String, Map<String, JSONObject>> touchedPositions = new HashMap<>();
    public static ArrayList<JSONObject> touchedCellssWater = new  ArrayList<JSONObject>();

    public static String torn = "A";

    private String opposite;

    

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.gc = canvas.getGraphicsContext2D();

        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
       
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        
       
        grid = new PlayGrid(25, 25, 25, 10, 10);


        animationTimer = new PlayTimer(this::run, this::draw, 0);
       
        
        // if (torn.equals("A")){
        //     //textTorn = new Text("Es tu tuno de atacar");
        //    //textTorn.setText("Es tu tuno de atacar");
        //     setTextTorn("Es tu tuno de atacar");
        // }else {
        //    // textTorn = new Text("Es el tuno de tu oponente");
        //     //textTorn.setText("Es el tuno de tu oponente");
        //     setTextTorn("Es el tuno de tu oponente");
        // }
       
        // setOpposite(Main.userId.equals("A")? "B" : "A");
        // setStringUser(Main.userId);
        // setTextTorn(Main.userId);
        

        start();
    }

    public void initializeGame() {
        setOpposite(Main.userId.equals("A") ? "B" : "A");
        setStringUser(Main.userId);
        setTextTorn("A");
    }
    

    public void onSizeChanged() {
        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }


    public void start() {
        animationTimer.start();
    }


    public void stop() {
        animationTimer.stop();
    }

    public void setTextTorn(String newText){
       
        Platform.runLater(() -> {
            // if (flag){
            //     stringUser.setText(Main.userId);
            // }
            textTorn.setText(newText);
        });
    }

    public void setStringUser(String text){
       
        Platform.runLater(() -> {
            Text stringUser = new Text();
            
            // Configurar propiedades del texto
            stringUser.setText(text);
            stringUser.setFill(Color.WHITE); // Color del texto
            stringUser.setLayoutX(26.0); // Posición X
            stringUser.setLayoutY(466.0); // Posición Y
            stringUser.setStrokeWidth(0.0); // Grosor del trazo
            stringUser.setStyle("-fx-font-weight: 900;");

            // Añadir el texto al AnchorPane
            anchor.getChildren().add(stringUser);
        });

    }

    public void setOpposite(String opp){
        Platform.runLater(() -> {
            System.out.println("REFERENCIAAAAA");
            System.out.println(Main.userId + " cacao");
            opposite = opp;
            
        });
    }

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();


        JSONObject newPosition = new JSONObject();
        newPosition.put("x", mouseX);
        newPosition.put("y", mouseY);
        if (grid.isPositionInsideGrid(mouseX, mouseY)) {                
            newPosition.put("col", grid.getCol(mouseX));
            newPosition.put("row", grid.getRow(mouseY));
        } else {
            newPosition.put("col", -1);
            newPosition.put("row", -1);
        }
        clientMousePositions.put(Main.userId, newPosition);


        Main.sendMessageToServer("clientMouseMoving", newPosition);
    }

   
      
    // Solo funciona si es tu turno
    private void onMousePressed(MouseEvent event) {
        // if is yourTurn
        if (Main.userId.equals(torn)){

            
            double mouseX = event.getX();
            double mouseY = event.getY();

            double startX = grid.getStartX();
            double startY = grid.getStartY();
            double cellSize = grid.getCellSize();

            int touchedCol = (int) ((mouseX - startX) / cellSize);
            int touchedRow = (int) ((mouseY - startY) / cellSize);

            if (touchedCol >= 0 && touchedCol < grid.getCols() && touchedRow >= 0 && touchedRow < grid.getRows()) {
                System.out.println("OPPOSITE: " + opposite);

                Map<String, JSONObject> userObjects = positionShips.get(opposite);
                boolean isWater = true;

                if (userObjects != null) {
                    for (String objectId : userObjects.keySet()) {
                        JSONObject obj = userObjects.get(objectId);
                        int objX = obj.getInt("x");
                        int objY = obj.getInt("y");
                        int cols = obj.getInt("cols");
                        int rows = obj.getInt("rows");

                        if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                            int objCol = (int) ((mouseX - objX) / cellSize);
                            int objRow = (int) ((mouseY - objY) / cellSize);

                            if (!obj.has("touchedCellsShips")) {
                                obj.put("touchedCellsShips", new ArrayList<JSONObject>());
                            }

                            JSONObject touchedCell = new JSONObject();
                            touchedCell.put("col", objCol);
                            touchedCell.put("row", objRow);
                            obj.getJSONArray("touchedCellsShips").put(touchedCell);

                            obj.put("touched", true);
                            isWater = false;

                            System.out.println("Celda tocada del barco " + objectId + ": (" + objCol + ", " + objRow + ")");

                            // Comprueba si el barco está completamente hundido
                            if (isShipSunk(obj)) {
                                obj.put("sunk", true); // Marca el barco como hundido
                                System.out.println(Main.userId + ": ¡Barco " + objectId + " hundido!");
                                //System.out.println(counter.getText());
                                 int shipsSunked = Integer.parseInt(counter.getText()) +1;
                                 counter.setText(String.valueOf(shipsSunked));
                                

                                // Verifica si todos los barcos del jugador están hundidos
                                if (isAllShipsSunk()) {
                                    JSONObject data = new JSONObject();
                                    data.put("winner", Main.userId);
                                    Main.sendMessageToServer("finishGame", data);
                                
                                }
                            }
                            break;
                        }
                    }
                }

                if (isWater) {
                    JSONObject touchedWaterCell = new JSONObject();
                    touchedWaterCell.put("col", touchedCol);
                    touchedWaterCell.put("row", touchedRow);
                    touchedCellssWater.add(touchedWaterCell);
                    System.out.println("Celda de agua tocada: (" + touchedCol + ", " + touchedRow + ")");
                }

                Main.sendMessageToServer("changeTorn", null);
                //this.textTorn.setText(stringTorn);
               
                
            }
        }
    }

    // Método para comprobar si un barco está hundido
    private boolean isShipSunk(JSONObject ship) {
        int cols = ship.getInt("cols");
        int rows = ship.getInt("rows");
        int totalCells = cols * rows;
        int touchedCells = ship.getJSONArray("touchedCellsShips").length();
        return touchedCells == totalCells;
    }

    // Método para verificar si todos los barcos están hundidos
    private boolean isAllShipsSunk() {
        Map<String, JSONObject> userObjects = positionShips.get(opposite);
        for (JSONObject obj : userObjects.values()) {
            if (!obj.optBoolean("sunk", false)) {
                return false; // Si algún barco no está hundido, el jugador aún no ha perdido
            }
        }
        return true; // Todos los barcos están hundidos
    }

    // Método para mostrar el mensaje de fin de juego
    public void showEndGameMessage(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.setOnHidden(evt -> {
                // Cierra todas las ventanas y termina la aplicación 
                // Hacer que vuelva al crlConfig ***
                Stage stage = (Stage) canvas.getScene().getWindow();
                stage.close();
                //Platform.exit();
                //System.exit(0);
            });

            alert.showAndWait();
        });
    }




    public void setpositionShips(JSONObject objects) {
        positionShips.clear();
        for (String userId : objects.keySet()) {
            JSONObject userObjectsJson = objects.getJSONObject(userId);
            Map<String, JSONObject> userObjects = new HashMap<>();
            for (String objectId : userObjectsJson.keySet()) {
                JSONObject objectData = userObjectsJson.getJSONObject(objectId);
                userObjects.put(objectId, objectData);
            }
            positionShips.put(userId, userObjects);
        }
    }


    public void setPlayersMousePositions(JSONObject positions) {
        clientMousePositions.clear();
        for (String clientId : positions.keySet()) {
            if (clientId.equals(Main.userId)){
                JSONObject positionObject = positions.getJSONObject(clientId);
                clientMousePositions.put(clientId, positionObject);
            }
        }
    }


    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
       // System.out.println("CELL SIZE: " + cellSize + " cols: " + cols + " rows: " + rows);
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;


        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;


        return positionX >= objectLeftX && positionX < objectRightX &&
               positionY >= objectTopY && positionY < objectBottomY;
    }

    private void run(double fps) {
        if (animationTimer.fps < 1) { return; }
    }


    public void draw() {
       
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());


        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);


            int col = position.getInt("col");
            int row = position.getInt("row");


            if (row >= 0 && col >= 0) {
                gc.setFill("A".equals(clientId) ? Color.LIGHTBLUE : Color.LIGHTGREEN);
                gc.fillRect(grid.getCellX(col), grid.getCellY(row), grid.getCellSize(), grid.getCellSize());
            }
        }


        drawGrid();


       // Solo pintarlos si hay barco tocado
        Map<String, JSONObject> userObjects = positionShips.get(opposite);
        if (userObjects != null) {
            for (String objectId : userObjects.keySet()) {
                JSONObject obj = userObjects.get(objectId);
                Boolean touchedFlag = obj.optBoolean("touched", false);
                drawpositionShips(objectId, touchedFlag, obj);
            }
        }


        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);
            gc.setFill("A".equals(clientId) ? Color.YELLOW : Color.GREEN);
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }
        if (showFPS) { animationTimer.drawFPS(gc); }  
    }


    public void drawGrid() {
        gc.setStroke(Color.BLACK);
        double cellSize = grid.getCellSize();
        double startX = grid.getStartX();
        double startY = grid.getStartY();

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double x = startX + col * cellSize;
                double y = startY + row * cellSize;
                gc.setFill(Color.GREY);
                gc.fillRect(x, y, cellSize, cellSize);
                gc.setStroke(Color.BLACK);
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }

        if (touchedCellssWater.size() > 0) {
            for (int i = 0; i < touchedCellssWater.size(); i++) {
                JSONObject touchedCell = touchedCellssWater.get(i);
                int touchedCol = touchedCell.getInt("col");
                int touchedRow = touchedCell.getInt("row");
                double touchedX = startX + touchedCol * cellSize;
                double touchedY = startY + touchedRow * cellSize;

                gc.setFill(Color.BLUE);
                gc.fillRect(touchedX, touchedY, cellSize, cellSize);
                gc.setStroke(Color.BLACK);
                gc.strokeRect(touchedX, touchedY, cellSize, cellSize);
            }
        }
    }

    


    public void drawpositionShips(String objectId, Boolean touched, JSONObject obj) {
        double cellSize = grid.getCellSize();
    
        // Coordenadas iniciales del barco
        int x = obj.getInt("x");
        int y = obj.getInt("y");
    
        // Si el barco ha sido tocado, dibuja cada celda en la lista de celdas tocadas
        if (touched && obj.has("touchedCellsShips")) {
            for (int i = 0; i < obj.getJSONArray("touchedCellsShips").length(); i++) {
                JSONObject touchedCell = obj.getJSONArray("touchedCellsShips").getJSONObject(i);
    
                // Obtén la columna y fila de la celda tocada
                int touchedCol = touchedCell.getInt("col");
                int touchedRow = touchedCell.getInt("row");
    
                // Calcula la posición de la celda tocada
                double touchedX = x + touchedCol * cellSize;
                double touchedY = y + touchedRow * cellSize;
    
                // Dibuja la celda tocada en rojo
                gc.setFill(Color.RED);
                gc.fillRect(touchedX, touchedY, cellSize, cellSize);
                gc.setStroke(Color.BLACK);
                gc.strokeRect(touchedX, touchedY, cellSize, cellSize);
            }
        }
    }
    
}
