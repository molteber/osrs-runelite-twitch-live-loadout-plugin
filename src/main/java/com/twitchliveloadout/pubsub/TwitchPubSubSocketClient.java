package com.twitchliveloadout.pubsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.twitchliveloadout.pubsub.messages.INeedAuth;
import com.twitchliveloadout.pubsub.messages.MessageData;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Slf4j
public class TwitchPubSubSocketClient {
    private WebSocket webSocket;
    private final Gson gson;
    private final SocketListener socketListener;
    private final String oAuthToken;

    private boolean shouldReconnect = true;
    private boolean socketOpen = false;


    private static final int PING_TIMER = 180_000; // ms (3 minutes)
    private static final int PING_TIMEOUT = 10_000; // ms

    private long lastPong;
    private boolean pingSent;

    public TwitchPubSubSocketClient(Gson gson, SocketListener socketListener, String oAuthToken)
    {
        this.gson = gson;
        this.socketListener = socketListener;
        this.oAuthToken = oAuthToken;
    }

    public boolean isConnected()
    {
        return socketOpen;
    }

    public boolean awaitingPing() {
        return pingSent;
    }

    private synchronized void initWebSocket()
    {
        String socketUrl = "wss://pubsub-edge.twitch.tv";
        log.debug("socketCheck: initWebsocket() socketUrl = {}", socketUrl);
        OkHttpClient client = new OkHttpClient();

        var request = new Request.Builder().url(socketUrl).build();
        webSocket = client.newWebSocket(request, webSocketListener);
        client.dispatcher().executorService().shutdown();
    }

    public void connect()
    {
        log.debug("socketCheck: connect()");
        shouldReconnect = true;
        initWebSocket();
    }

    public synchronized void reconnect()
    {
        log.debug("socketCheck: reconnect()");
        initWebSocket();
    }

    public void pingCheck()
    {
        if (!isConnected()) {
            return;
        }

        if (pingSent && System.currentTimeMillis() - lastPong >= PING_TIMEOUT) {
            log.debug("Ping timeout, disconnecting");
            disconnect();
            return;
        }

        if (!pingSent && System.currentTimeMillis() - lastPong >= PING_TIMER) {
            try {
                sendMessage(new Message<>("PING"));
                pingSent = true;
            } catch (IOException ex) {
                log.debug("Ping failure, disconnecting", ex);
                disconnect();
            }
        }
    }

    public <T extends MessageData> void sendMessage(Message<T> message) throws IOException, RuntimeException
    {

        if (!socketOpen) {
            return;
        }

        var type = new TypeToken<Message<T>>() {}.getType();
        if (message.data instanceof INeedAuth) {
            ((INeedAuth) message.data).setAuthToken(oAuthToken);
        }

        var jsonMessage = gson.toJson(message, type);

        log.debug("socketCheck: sendMessage({})", jsonMessage);


        webSocket.send(jsonMessage);
    }

    public void disconnect()
    {
        if (socketOpen) {
            webSocket.close(1000, "Do not need connection anymore");
        }
        shouldReconnect = false;
    }

    public interface SocketListener {
        public void onReady();
        public void onMessage(String message, JsonObject dataObject);
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response)
        {
            socketOpen = true;
            log.debug("socketCheck: onOpen()");
            pingCheck();

            socketListener.onReady();
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            JsonObject result = (new JsonParser()).parse(text).getAsJsonObject();
            String messageType = result.get("type").getAsString();

            log.debug("socketCheck: onMessage({})", text);

            switch (messageType) {
                case "PONG" -> {
                    lastPong = System.currentTimeMillis();
                    pingSent = false;
                }
                case "RECONNECT" -> reconnect();
                case "AUTH_REVOKED" -> disconnect();
                case "RESPONSE" -> {
                    String responseMessage = result.get("error").getAsString();
                    if (responseMessage.equals("ERR_BADAUTH")) {
                        disconnect();
                    }
                }
                default -> socketListener.onMessage(messageType, result.get("data").getAsJsonObject());
            }
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.debug("socketCheck: onClosing()");
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.debug("socketCheck: onClosed()");
            socketOpen = false;
            if (shouldReconnect) {
                reconnect();
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            log.debug("socketCheck: onFailure()");
            if (shouldReconnect) {
                reconnect();
            }
        }
    };
}

