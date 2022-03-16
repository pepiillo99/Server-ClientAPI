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

And the most important, you need run the server to send and receibe the information packets...

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
		startRead(); // THIS IS MOST IMPORTANT BECAUSE IF YOU NOT USE THIS THE CLIENT NOT START READ THE PACKETS !!!!!!!!!!!
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

With this we will have the possibility of both connecting the client and sending information between both :)
