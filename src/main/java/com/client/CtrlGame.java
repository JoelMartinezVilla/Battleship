package com.client;


import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;


import org.json.JSONObject;


import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;


public class CtrlGame implements Initializable {


    @FXML
    private Canvas canvas;


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

    //private String touch = "";




    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.gc = canvas.getGraphicsContext2D();

        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
       
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        
       
        grid = new PlayGrid(25, 25, 25, 10, 10);


        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
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

   
      
        
    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Verifica si el barco ha sido tocado
        Map<String, JSONObject> userObjects = positionShips.get(Main.userId);
        if (userObjects != null) {
            for (String objectId : userObjects.keySet()) {
                JSONObject obj = userObjects.get(objectId);
                int objX = obj.getInt("x");
                int objY = obj.getInt("y");
                int cols = obj.getInt("cols");
                int rows = obj.getInt("rows");

                int touchedCol = (int)((mouseX - objX) / grid.getCellSize());
                int touchedRow = (int)((mouseY - objY) / grid.getCellSize());

                if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                    // Calcula la columna y la fila dentro del barco que fueron tocadas
                   

                    // Crea una lista de celdas tocadas si no existe
                    if (!obj.has("touchedCellsShips")) {
                        obj.put("touchedCellsShips", new ArrayList<JSONObject>());
                    }

                    // Agrega la celda tocada a la lista de celdas tocadas
                    JSONObject touchedCell = new JSONObject();
                    touchedCell.put("col", touchedCol);
                    touchedCell.put("row", touchedRow);
                    obj.getJSONArray("touchedCellsShips").put(touchedCell);

                    // Indica que el barco ha sido tocado
                    obj.put("touched", true);

                    System.out.println("Celda tocada del barco " + objectId + ": (" + touchedCol + ", " + touchedRow + ")");
                    break;
                }
                // Has tocado agua
                else {
                    touchedCol = (int)((mouseX - grid.getStartX()) / grid.getCellSize());
                    touchedRow = (int)((mouseY - grid.getStartY()) / grid.getCellSize());

                    JSONObject touchedCell = new JSONObject();
                    touchedCell.put("col", touchedCol);
                    touchedCell.put("row", touchedRow);
                    touchedCellssWater.add(touchedCell);
                }
            }
        }
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
        Map<String, JSONObject> userObjects = positionShips.get(Main.userId);
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
