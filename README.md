# Server-ClientAPI
This API allows you to easily create both a server and a client and be able to send information between them asynchronously.

In addition, you can create your own information packet with the desired content.

```java
public class HelloWorldPacket extends Packet {
    private String info = "hello world";
    public HelloWorldPacket(String info) {
        super(1);
        this.info = info;
    }
    public HelloWorldPacket() {
        super(1);
    }
    @Override
    public Packet serialize(ByteArrayInputStream info) throws ReadPacketException {
        return new HelloWorldPacket(PacketUtilities.getString(info));
    }
    @Override
    public ByteArrayOutputStream deserialize(ByteArrayOutputStream toInfo) throws WritePacketException {
        PacketUtilities.writeString(info, toInfo);
        return toInfo;
    }   
    public String getInfo() {
        return info;
    }
}
```

And you need register this packet on instance...

```java
ServerClientAPI scAPI = new ServerClientAPI();
scAPI.addPacket(new HelloWorldPacket());
```

The most important, you need run the server to send and receibe the information packets...

```java
List<ClientServer> clients = new ArrayList<ClientServer>; // its ideally you use HashMap ;)
ServerConnection server = new ServerConnection(777) {
    @Override
    public void onStart() {
        
    }
    @Override
    public void onConnect(AsynchronousSocketChannel clientConnection) {
        clients.add(new ClientServer(clientConnection, scAPI)); // there action to create the client instance...
    }
    @Override
    public void onStop() {
        
    }           
};
// if you want connect to server use clients.add(new ClientServer("localhost", 777, scAPI));
```

Previously, as you saw, we used a client object... We will have to create this because it is an abstract class and we will have to define its methods inside...

```java
public class ClientServer extends ClientConnection {
    public ClientServer(AsynchronousSocketChannel connection, ServerClientAPI packetManager) {
        super(connection, packetManager); // server-client || client connected to server
    }  
    public ClientServer(String ip, int port, ServerClientAPI packetManager) throws IOException {
        super(ip, port, packetManager); // client-server || client connect to server
    }
    @Override
    public void onConnect() {
        System.out.println("IM CONNECTED!");
    }
    @Override
    public void onFailedConnect() {}
    @Override
    public void onRecibe(Packet packet) {
        if (packet instanceof HelloWorldPacket) {
            HelloWorldPacket hipacket = (HelloWorldPacket) packet;
            System.out.println((getClientConnectionType().equals(ClientConnectionType.CLIENT_TO_SERVER ? "Server says: " : "Client says: ")) + hipacket.getInfo());
        }
    }
    @Override
    public void onDropCanReconnect() {}
    @Override
    public void onReconnect() {}
    @Override
    public void onDisconnect() {}
}
```

There is also the possibility of being able to send files, which means that the maximum number of bytes per packet can be edited according to the needs of the client/server.

In order to receive files we must first accept the file on the client/server adding this code on ClientConnection class:

```java
@Override
public boolean canReceiveFile(String dest, String fileType, long bytesPerPacket, long fileLenght) {
    return true;
}
@Override
public void onReceiveFile(String dest, String fileType, long fileLenght) {
    // bla bla bla
}
```

This way the client/server will accept any file it receives. The arguments to the method are the destination of the file, the type of file, the bytes per packet to send the file (default 1MB), and the weight of the file.

While sending a file you can continue sending packages normally. Although you have to keep in mind that if the number of bytes per packet is too high it could cause delays in client/server communication, I always advise leaving it by default (1MB) until you develop a system that calculates the most optimal number of bytes per packet. for each client and connections.

To send a file we will simply use the ```sendFile(String path, String dest, long bytesPerPacket(not recommended use))``` method of the ClientConnection class. This will return a code which the system uses to identify the shipment. We also have the method ```changeBytesPerPacketOfSender(String code, long bytes)``` which allows us to change the bytes per packet sent when sending a file (which I previously commented recommend leaving by default).

When making a connection between devices locally this can be quite a tedious job, since the device's IP can sometimes change depending on the network mask. To do this, I have developed a system to detect open local IPs with a specific port in our local connection. To use this function we will use the following code:

```java
new CalculatorRedMaskOpenned(new RedMaskFindedCallback<String>() {
    @Override
    public void done(String ip, Exception exception) {
        System.out.println("Local IP openned finded: " + ip);
    }           
}, 999);
````

With this we will have the possibility of both connecting the client and sending information between both :)