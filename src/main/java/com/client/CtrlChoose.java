package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlChoose implements Initializable {

    @FXML
    private Canvas canvas;

    @FXML
    private Label secondsLeft;

    @FXML
    private Button startButton; // Inyectar el botón StartButton

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
        this.gc = canvas.getGraphicsContext2D();

        // Establecer el evento de clic para el botón
        startButton.setOnMouseClicked(event -> onStartButtonClick());

        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> {
            onSizeChanged();
        });

        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        grid = new PlayGrid(25, 25, 25, 10, 10);

        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    public void playersReady() {
        UtilsViews.setViewAnimating("ViewGame");
    }

    public void startSecondsLeft() {
        Thread countdownThread = new Thread(() -> {
            int timeRemaining = 30;
            while (timeRemaining >= 0) {
                final int displayTime = timeRemaining;

                // Actualizar el Label en el hilo de la interfaz usando Platform.runLater
                Platform.runLater(() -> secondsLeft.setText(String.valueOf(displayTime)));

                // Esperar 1 segundo
                try {
                    Thread.sleep(1000); // Espera 1000 ms (1 segundo)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break; // Salir del bucle si el hilo es interrumpido
                }

                // Disminuir el contador
                timeRemaining--;
            }

            Platform.runLater(() -> {
                UtilsViews.setViewAnimating("ViewGame");
            });

        });

        // Configurar el hilo para que se cierre al salir de la aplicación
        countdownThread.setDaemon(true);
        countdownThread.start(); // Iniciar el hilo de conteo regresivo
    }

    private void onStartButtonClick() {
        // Acciones a realizar cuando se hace clic en el botón Start
        String client = Main.userId;

        if (allShipsPlaced()) {
            JSONObject clientInfo = new JSONObject();
            clientInfo.put("type", "clientReady");
            clientInfo.put("clientId", client);

            Main.sendMessageToServer("clientReady", clientInfo);
            System.out.println("Todos los barcos puestos.");
        }else{
            System.out.println("No has puesto todos los barcos todavía.");
        }

    }

    public boolean allShipsPlaced() {
        Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);

        for (String objectId : userObjects.keySet()) {
            JSONObject obj = userObjects.get(objectId);
            if (!obj.getBoolean("placed")) {
                return false;
            }
        }
        return true;

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

                obj.put("objectId", selectedObject);

                Main.sendMessageToServer("clientSelectableObjectMoving", obj);
                setOnMouseMoved(event);
            }
        }
    }

    public boolean isShipOverriding(String objectId) {

        Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
        JSONObject obj = userObjects.get(objectId);

        if (obj == null || !obj.has("col") || !obj.has("row") || !obj.has("cols") || !obj.has("rows")) {
            System.out.println("Objeto no encontrado o faltan claves: " + objectId);
            return false;
        }

        int objFirstCol = obj.getInt("col");
        int objFirstRow = obj.getInt("row");
        int objLastCol = obj.getInt("cols") + objFirstCol - 1;
        int objLastRow = obj.getInt("rows") + objFirstRow - 1;

        for (String otherObjectId : userObjects.keySet()) {
            if (!otherObjectId.equals(objectId)) {
                JSONObject otherObj = userObjects.get(otherObjectId);

                if (otherObj == null || !otherObj.has("col") || !otherObj.has("row") || !otherObj.has("cols")
                        || !otherObj.has("rows")) {
                    continue;
                }

                int otherFirstCol = otherObj.getInt("col");
                int otherFirstRow = otherObj.getInt("row");
                int otherLastCol = otherObj.getInt("cols") + otherFirstCol - 1;
                int otherLastRow = otherObj.getInt("rows") + otherFirstRow - 1;

                if (objFirstCol <= otherLastCol && objLastCol >= otherFirstCol &&
                        objFirstRow <= otherLastRow && objLastRow >= otherFirstRow) {
                    return true;
                }
            }
        }
        return false;
    }

    private void onMouseReleased(MouseEvent event) {
        if (!selectedObject.isEmpty()) {
            Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
            if (userObjects != null && userObjects.containsKey(selectedObject)) {
                JSONObject obj = userObjects.get(selectedObject);

                int objCol = obj.getInt("col");
                int objRow = obj.getInt("row");

                if (isShipOverriding(selectedObject)) {
                    obj.put("x", obj.get("initial_x"));
                    obj.put("y", obj.get("initial_y"));
                    obj.put("placed", false);
                } else if (objCol != -1 && objRow != -1) {
                    obj.put("x", grid.getCellX(objCol));
                    obj.put("y", grid.getCellY(objRow));
                    obj.put("placed", true);
                } else if (objCol <= -1 || objRow <= -1) {
                    obj.put("x", obj.get("initial_x"));
                    obj.put("y", obj.get("initial_y"));
                    obj.put("placed", false);
                }

                obj.put("objectId", obj.getString("objectId"));

                Main.sendMessageToServer("clientSelectableObjectMoving", obj);
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

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) {
            return;
        }

        // Update objects and animations here
    }

    // Draw game to canvas
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

        Map<String, JSONObject> userObjects = selectableObjects.get(Main.userId);
        if (userObjects != null) {
            for (String objectId : userObjects.keySet()) {
                JSONObject obj = userObjects.get(objectId);
                drawSelectableObject(objectId, obj);
            }
        }

        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);
            gc.setFill("A".equals(clientId) ? Color.BLUE : Color.GREEN);
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
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
        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillRect(x, y, width, height);
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        gc.setFill(Color.BLACK);
        gc.fillText(objectId, x + 5, y + 15);
    }
}
