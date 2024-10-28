package com.client;


import java.net.URL;
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

    public static Map<String, Map<String, JSONObject>> touchedPositions = new HashMap<>();

    private String touch = "";




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

        
        // Que se guarde en las posiciones tocadas ***

        Map<String, JSONObject> userObjects = positionShips.get(Main.userId);
        if (userObjects != null) {
            for (String objectId : userObjects.keySet()) {
                JSONObject obj = userObjects.get(objectId);
                int objX = obj.getInt("x");
                int objY = obj.getInt("y");
                int cols = obj.getInt("cols");
                int rows = obj.getInt("rows");


                if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                    ship = objectId;// ??
                    mouseDragging = true;
                    mouseOffsetX = event.getX() - objX;
                    mouseOffsetY = event.getY() - objY;
                    // Barco tocado
                    obj.put("touched", true);

                    Map<String, JSONObject> userTouchs = new HashMap<>();
                    JSONObject touch = new JSONObject();
                    touch.put("objectId", objectId);
                    touch.put("x", objX);
                    touch.put("y", objY);
                    touch.put("cols", cols);
                    touch.put("rows", rows);
                    userTouchs.put("O0", touch);
                    touchedPositions.put(Main.userId, userTouchs);
                    System.out.println("Has tocado un barco!! user: " + Main.userId);
                    break;
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
               // Aqui sumar numeros al cells touched del obj
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
            gc.setFill("A".equals(clientId) ? Color.BLUE : Color.GREEN);
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }
        if (showFPS) { animationTimer.drawFPS(gc); }  
    }


    public void drawGrid() {
        gc.setStroke(Color.BLACK);


        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = grid.getStartX() + col * cellSize;
                double y = grid.getStartY() + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }


    public void drawpositionShips(String objectId, Boolean touched, JSONObject obj) {
        double cellSize = grid.getCellSize();

       // System.out.println(obj.toString());

        int x = obj.getInt("x");
        int y = obj.getInt("y");
        double width = obj.getInt("cols") * cellSize;
        double height = obj.getInt("rows") * cellSize;


        Color color;
        if (touched){
                color = Color.RED;
        }
        else {   
            color = Color.GRAY;     
        }


        gc.setFill(color);
        gc.fillRect(x, y, width, height);


        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);


        gc.setFill(Color.BLACK);
        gc.fillText(objectId, x + 5, y + 15);
    }
}