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

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlChoose = (CtrlChoose) UtilsViews.getController("ViewChoose");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");

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
    
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }
   
    private static void wsMessage(String response) {
        System.out.println(response);
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "clients":
                if (userId == "") {
                    userId = msgObj.getString("id");
                }
                if (UtilsViews.getActiveView() != "ViewWait") {
                    UtilsViews.setViewAnimating("ViewWait");
                }
                List<String> stringList = jsonArrayToList(msgObj.getJSONArray("list"), String.class);
                if (stringList.size() > 0) { ctrlWait.txtPlayer0.setText(stringList.get(0)); }
                if (stringList.size() > 1) { ctrlWait.txtPlayer1.setText(stringList.get(1)); }
                break;
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    UtilsViews.setViewAnimating("ViewChoose");
                    txt = "GO";
                }
                ctrlWait.txtTitle.setText(txt);
                break;
            case "serverMouseMoving":
                // *******************************
                //if (UtilsViews.getActiveView() == "ViewChoose") {
                   // System.out.println("Estas entrando al CHOOSEEEE");
                    //ctrlChoose.setPlayersMousePositions(msgObj.getJSONObject("positions"));
                //}
                if (UtilsViews.getActiveView() == "ViewPlay") {
                    ctrlPlay.setPlayersMousePositions(msgObj.getJSONObject("positions"));    
                }
                break;
            case "serverSelectableObjects":
                
                ctrlChoose.setSelectableObjects(msgObj.getJSONObject("selectableObjects"));
                
               
                //if (UtilsViews.getActiveView().equals("ViewPlay")) {
                //ctrlPlay.setSelectableObjects(msgObj.getJSONObject("selectableObjects"));
                //}
                break;
        }
    }

    private static void wsError(String response) {

        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}