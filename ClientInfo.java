public class ClientInfo {
	String clientName;
	String clientIP;
	int clientPort;
	
	public ClientInfo(String clientName, String clientIP, int clientPort) {
		this.clientName = clientName;
		this.clientIP = clientIP;
		this.clientPort = clientPort;
	}
	
	public String getName() {
		return clientName;
	}
	
	public String getIP() {
		return clientIP;
	}
	
	public int getPort() {
		return clientPort;
	}
}