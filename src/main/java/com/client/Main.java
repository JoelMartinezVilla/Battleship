package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;
    public static String userId = "";
    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlChoose ctrlChoose;
    public static CtrlGame ctrlGame;

    public static void main(String[] args) {
        // Iniciar app JavaFX
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewChoose", "/assets/viewChoose.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlChoose = (CtrlChoose) UtilsViews.getController("ViewChoose");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");

        Scene scene = new Scene(UtilsViews.parentContainer);
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    public static void connectToServer() {
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");

        pauseDuring(1500, () -> { // Give time to show connecting message ...
            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);

            wsClient.onMessage((response) -> {
                Platform.runLater(() -> {
                    wsMessage(response);
                });
            });
            wsClient.onError((response) -> {
                Platform.runLater(() -> {
                    wsError(response);
                });
            });
        });
    }

    private static void wsMessage(String response) {
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "clients":
                // Guarda el userId cuando lo reciba del servidor
                if (userId.equals("")) {
                    userId = msgObj.getString("id");
                }
                if (!UtilsViews.getActiveView().equals("ViewWait")) {
                    UtilsViews.setViewAnimating("ViewWait");
                }
                List<String> stringList = jsonArrayToList(msgObj.getJSONArray("list"), String.class);
                if (stringList.size() > 0) {
                    ctrlWait.txtPlayer0.setText(stringList.get(0));
                }
                if (stringList.size() > 1) {
                    ctrlWait.txtPlayer1.setText(stringList.get(1));
                }
                break;
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    // if (!UtilsViews.getActiveView().equals("ViewChoose")) {
                    UtilsViews.setViewAnimating("ViewChoose");
                    txt = "GO";
                    JSONObject message = new JSONObject();
                    message.put("type", "startReadyCountdown");
                    Main.wsClient.safeSend(message.toString());
                    // }
                    // else {
                    // UtilsViews.setViewAnimating("ViewGame");
                    // }
                }
                ctrlWait.txtTitle.setText(txt);
                break;
            case "serverMouseMoving":
                if (UtilsViews.getActiveView().equals("ViewChoose")) {
                    ctrlChoose.setPlayersMousePositions(msgObj.getJSONObject("positions"));
                }
                if (UtilsViews.getActiveView().equals("ViewGame")) {
                    ctrlGame.setPlayersMousePositions(msgObj.getJSONObject("positions"));
                }
                break;
            case "serverSelectableObjects":
                ctrlChoose.setSelectableObjects(msgObj.getJSONObject("selectableObjects"));
                break;

            case "readyCountdown":
                int readyValue = msgObj.getInt("value");
                if (readyValue == 0) {
                    // if (!UtilsViews.getActiveView().equals("ViewChoose")) {
                    UtilsViews.setViewAnimating("ViewGame");
                    System.out.println(readyValue);
                    // }
                    // else {
                    // UtilsViews.setViewAnimating("ViewGame");
                    // }
                }
                break;

        }
    }

    // Método auxiliar para enviar mensajes con userId incluido
    public static void sendMessageToServer(String type, JSONObject data) {
        JSONObject message = new JSONObject();
        message.put("type", type);
        message.put("userId", userId); // Incluye el userId del cliente
        message.put("data", data); // Datos específicos de la acción
        wsClient.safeSend(message.toString());
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.contains(connectionRefused)) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> ctrlConfig.txtMessage.setText(""));
        }
    }
}
