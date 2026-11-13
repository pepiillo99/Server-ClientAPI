package me.pepe.ServerClientAPI.Connections;
public interface ConnectionStateListener {
    void onStateChanged(ClientConnection connection, ConnectionState previous, ConnectionState current);
}
