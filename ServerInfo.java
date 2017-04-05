public class ServerInfo {
	String serverName;
	String serverIP;
	int serverPort;
	
	public ServerInfo(String serverName, String serverIP, int serverPort) {
		this.serverName = serverName;
		this.serverIP = serverIP;
		this.serverPort = serverPort;
	}
	
	public String getName() {
		return serverName;
	}
	
	public String getIP() {
		return serverIP;
	}
	
	public int getPort() {
		return serverPort;
	}
	
	public void setPort(int serverPort) {
		this.serverPort = serverPort;;
	}
}