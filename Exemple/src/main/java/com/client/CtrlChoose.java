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
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlChoose implements Initializable {

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;

    public Map<String, JSONObject> clientMousePositions = new HashMap<>();
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    public static Map<String, Map<String, JSONObject>> selectableObjects = new HashMap<>();
    private String selectedObject = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // Define grid
        grid = new PlayGrid(25, 25, 25, 10, 10);

        // Start run/draw timer loop
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

        JSONObject msgObj = clientMousePositions.get(Main.userId);
        msgObj.put("type", "clientMouseMoving");
        msgObj.put("clientId", Main.userId);
    
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }

    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = "";
        mouseDragging = false;

        Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
        if (userObjects != null) {
            for (String objectId : userObjects.keySet()) {
                JSONObject obj = userObjects.get(objectId);
                int objX = obj.getInt("x");
                int objY = obj.getInt("y");
                int cols = obj.getInt("cols");
                int rows = obj.getInt("rows");

                if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                    selectedObject = objectId;
                    mouseDragging = true;
                    mouseOffsetX = event.getX() - objX;
                    mouseOffsetY = event.getY() - objY;
                    break;
                }
            }
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
            if (userObjects != null && userObjects.containsKey(selectedObject)) {
                JSONObject obj = userObjects.get(selectedObject);

                double objX = event.getX() - mouseOffsetX;
                double objY = event.getY() - mouseOffsetY;
                
                obj.put("x", objX);
                obj.put("y", objY);
                obj.put("col", grid.getCol(objX));
                obj.put("row", grid.getRow(objY));
                obj.put("type", "clientSelectableObjectMoving");
                obj.put("objectId", selectedObject);

                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(obj.toString());
                }
                setOnMouseMoved(event);
            }
        }
    }

    private void onMouseReleased(MouseEvent event) {
        if (!selectedObject.isEmpty()) {
            Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
            if (userObjects != null && userObjects.containsKey(selectedObject)) {
                JSONObject obj = userObjects.get(selectedObject);

                int objCol = obj.getInt("col");
                int objRow = obj.getInt("row");

                if (objCol != -1 && objRow != -1) {
                    obj.put("x", grid.getCellX(objCol));
                    obj.put("y", grid.getCellY(objRow));
                }

                obj.put("type", "clientSelectableObjectMoving");
                obj.put("objectId", obj.getString("objectId"));

                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(obj.toString());
                }
            }

            mouseDragging = false;
            selectedObject = "";
        }
    }


    public void setSelectableObjects(JSONObject objects) {
        selectableObjects.clear();
        for (String userId : objects.keySet()) {
            JSONObject userObjectsJson = objects.getJSONObject(userId);
            Map<String, JSONObject> userObjects = new HashMap<>();
            for (String objectId : userObjectsJson.keySet()) {
                JSONObject objectData = userObjectsJson.getJSONObject(objectId);
                userObjects.put(objectId, objectData);
            }
            selectableObjects.put(userId, userObjects);
        }
    }

    public void setPlayersMousePositions(JSONObject positions) {
        clientMousePositions.clear();
        for (String clientId : positions.keySet()) {
            JSONObject positionObject = positions.getJSONObject(clientId);
            clientMousePositions.put(clientId, positionObject);
        }
    }

    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
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

        for (String userId : selectableObjects.keySet()) {
            Map<String, JSONObject> userObjects = selectableObjects.get(userId);
            if (userObjects != null) {
                for (String objectId : userObjects.keySet()) {
                    JSONObject obj = userObjects.get(objectId);
                    drawSelectableObject(objectId, obj);
                }
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

    public void drawSelectableObject(String objectId, JSONObject obj) {
        double cellSize = grid.getCellSize();

        int x = obj.getInt("x");
        int y = obj.getInt("y");
        double width = obj.getInt("cols") * cellSize;
        double height = obj.getInt("rows") * cellSize;

        Color color;
        switch (objectId.toLowerCase()) {
            case "red":
                color = Color.RED;
                break;
            case "blue":
                color = Color.BLUE;
                break;
            case "green":
                color = Color.GREEN;
                break;
            case "yellow":
                color = Color.YELLOW;
                break;
            default:
                color = Color.GRAY;
                break;
        }

        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        gc.setFill(Color.BLACK);
        gc.fillText(objectId, x + 5, y + 15);
    }
}
