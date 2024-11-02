package com.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends WebSocketServer {
    private static final List<String> PLAYER_NAMES = Arrays.asList("A", "B");

    private Map<WebSocket, String> clients;
    private List<String> availableNames;
    private Map<String, JSONObject> clientMousePositions = new HashMap<>();

    public static Map<String, Map<String, JSONObject>> selectableObjects = new HashMap<>();

    public Main(InetSocketAddress address) {
        super(address);
        clients = new ConcurrentHashMap<>();
        resetAvailableNames();
    }

    private void resetAvailableNames() {
        availableNames = new ArrayList<>(PLAYER_NAMES);
        Collections.shuffle(availableNames);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientName = getNextAvailableName();
        clients.put(conn, clientName);
        System.out.println("WebSocket client connected: " + clientName);
        sendClientsList();

        // Definir ships en base al clientId del cliente
        Map<String, JSONObject> ships = new HashMap<>();
        JSONObject obj0 = new JSONObject();
        obj0.put("objectId", "O0");
        obj0.put("x", 50);
        obj0.put("y", 50);
        obj0.put("initial_x", 300);
        obj0.put("initial_y", 50);
        obj0.put("cols", 4);
        obj0.put("rows", 1);
        obj0.put("placed", false);
        obj0.put("cellsTouch", 0);
        obj0.put("touched", false);
        ships.put("O0", obj0);

        JSONObject obj1 = new JSONObject();
        obj1.put("objectId", "O1");
        obj1.put("x", 300);
        obj1.put("y", 100);
        obj1.put("initial_x", 300);
        obj1.put("initial_y", 100);
        obj1.put("cols", 1);
        obj1.put("rows", 3);
        obj1.put("placed", false);
        obj1.put("cellsTouch", 0);
        obj1.put("touched", false);
        ships.put("O1", obj1);

        JSONObject obj2 = new JSONObject();
        obj2.put("objectId", "O2");
        obj2.put("x", 310);
        obj2.put("y", 150);
        obj2.put("initial_x", 310);
        obj2.put("initial_y", 150);
        obj2.put("cols", 2);
        obj2.put("rows", 1);
        obj2.put("placed", false);
        obj2.put("cellsTouch", 0);
        obj2.put("touched", false);
        ships.put("O2", obj2);

        // Usa el nombre del cliente como userId para almacenar sus objetos
        // seleccionables
        selectableObjects.put(clientName, ships);
        sendCountdown();
    }

    private String getNextAvailableName() {
        if (availableNames.isEmpty()) {
            resetAvailableNames();
        }
        return availableNames.remove(0);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String userId = clients.get(conn);
        clients.remove(conn);
        availableNames.add(userId);
        System.out.println("WebSocket client disconnected: " + userId);
        sendClientsList();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj = new JSONObject(message);
        String userId = clients.get(conn);
        if (obj.has("type")) {
            String type = obj.getString("type");

            switch (type) {
                case "clientReady":
                    
                  //  Map<String, Map<String, JSONObject>> objects = (Map<String, Map<String, JSONObject>>) obj.get("objects");

                    sendServerSelectableObjects();
                    JSONObject msg = new JSONObject();
                    System.out.println("hola");
                    msg.put("type", "playerReady");
                    msg.put("user", userId);
                    broadcastMessage(msg.toString(), null);
                    
                    break;
                case "clientMouseMoving":
                    // Guarda la posición del mouse para el usuario que envió el mensaje
                    clientMousePositions.put(userId, obj.getJSONObject("data"));

                    JSONObject rst0 = new JSONObject();
                    rst0.put("type", "serverMouseMoving");
                    rst0.put("positions", clientMousePositions);

                    // Envia el mensaje a todos los clientes conectados
                    broadcastMessage(rst0.toString(), null);
                    break;
                case "clientSelectableObjectMoving":
                    String objectId = obj.getJSONObject("data").getString("objectId");

                    Map<String, JSONObject> shipsList = selectableObjects.get(userId);
                    if (shipsList != null && shipsList.containsKey(objectId)) {
                        shipsList.put(objectId, obj.getJSONObject("data"));
                    }

                    System.out.println("USER ID: " + userId);
                    System.out.println(selectableObjects);

                    sendServerSelectableObjects();
                    break;

                // Aquí puedes añadir más tipos de mensajes que maneje el servidor
                case "finishGame":
            
                    // Mensaje directo a el otro jugador cin la orden de detener su juego
                    obj.put("type", "gameOver");
                    System.out.println(obj.toString());
                    String messageString = obj.toString();
                    broadcastMessage(messageString, null);
                    break;

                case "changeTorn":
                    obj.put("type", "changeTorn");
                    //System.out.println(obj.toString());
                    messageString = obj.toString();
                    broadcastMessage(messageString, null);
                break;
            }
        }
    }

    private void broadcastMessage(String message, WebSocket sender) {
        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            WebSocket conn = entry.getKey();
            if (conn != sender) {
                try {
                    conn.send(message);
                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client " + entry.getValue() + " not connected.");
                    clients.remove(conn);
                    availableNames.add(entry.getValue());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPrivateMessage(String destination, String message, WebSocket senderConn) {
        boolean found = false;

        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            if (entry.getValue().equals(destination)) {
                found = true;
                try {
                    // Donde lo recive??
                    
                    JSONObject confirmation = new JSONObject();
                    confirmation.put("type", "confirmation");
                    confirmation.put("message", message);

                    // A mi
                    senderConn.send(confirmation.toString());

                    // A el oponente
                    entry.getKey().send(confirmation.toString());

                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client " + destination + " not connected.");
                    clients.remove(entry.getKey());
                    availableNames.add(destination);
                    notifySenderClientUnavailable(senderConn, destination);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        if (!found) {
            System.out.println("Client " + destination + " not found.");
            notifySenderClientUnavailable(senderConn, destination);
        }
    }

    private void notifySenderClientUnavailable(WebSocket sender, String destination) {
        JSONObject rst = new JSONObject();
        rst.put("type", "error");
        rst.put("message", "Client " + destination + " not available.");

        try {
            sender.send(rst.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendClientsList() {
        JSONArray clientList = new JSONArray();
        for (String clientName : clients.values()) {
            clientList.put(clientName);
        }

        Iterator<Map.Entry<WebSocket, String>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WebSocket, String> entry = iterator.next();
            WebSocket conn = entry.getKey();
            String clientName = entry.getValue();

            JSONObject rst = new JSONObject();
            rst.put("type", "clients");
            rst.put("id", clientName);
            rst.put("list", clientList);

            try {
                conn.send(rst.toString());
            } catch (WebsocketNotConnectedException e) {
                System.out.println("Client " + clientName + " not connected.");
                iterator.remove();
                availableNames.add(clientName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendCountdown() {
        int requiredNumberOfClients = 2;
        if (clients.size() == requiredNumberOfClients) {
            for (int i = 5; i >= 0; i--) {
                JSONObject msg = new JSONObject();
                msg.put("type", "countdown");
                msg.put("value", i);
                broadcastMessage(msg.toString(), null);
                if (i == 0) {
                    sendServerSelectableObjects();
                } else {
                    try {
                        Thread.sleep(750);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void sendServerSelectableObjects() {
        // Map<String, Map<String, JSONObject>> objects
        // if (objects == null){
        //     objects = selectableObjects;
        // }
        // Prepara el mensaje de tipo 'serverObjects' con las posiciones de todos los
        // clientes
        JSONObject rst1 = new JSONObject();
        rst1.put("type", "serverSelectableObjects");
        rst1.put("selectableObjects", selectableObjects);

        // Envia el mensaje a todos los clientes conectados
        broadcastMessage(rst1.toString(), null);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static String askSystemName() {
        StringBuilder result = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("uname", "-r");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Error: El proceso ha finalizado con código " + exitCode;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
        return result.toString().trim();
    }

    public static void main(String[] args) {
        String systemName = askSystemName();

        // WebSockets server
        Main server = new Main(new InetSocketAddress(3000));
        server.start();

        LineReader reader = LineReaderBuilder.builder().build();
        System.out.println("Server running. Type 'exit' to gracefully stop it.");

        try {
            while (true) {
                String line = null;
                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                line = line.trim();

                if (line.equalsIgnoreCase("exit")) {
                    System.out.println("Stopping server...");
                    try {
                        server.stop(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                } else {
                    System.out.println("Unknown command. Type 'exit' to stop server gracefully.");
                }
            }
        } finally {
            System.out.println("Server stopped.");
        }
    }
}
