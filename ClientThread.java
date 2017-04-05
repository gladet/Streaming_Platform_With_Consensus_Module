import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class ClientThread implements Runnable {
	//instance variable
	String clientName;
	ArrayList<ServerInfo> servers;
	ServerInfo svrInfo;
	String fromUser;
	//Socket socket;
	
	//constructor
	//public ClientThread(String clientName, ArrayList<ServerInfo> servers, String fromUser) {
	public ClientThread(String clientName, ArrayList<ServerInfo> servers, ServerInfo svrInfo, String fromUser) {
		this.clientName = clientName;
		this.servers = servers;
		this.svrInfo = svrInfo;
		this.fromUser = fromUser;
		//this.socket = socket;
	}
	
	public void run() {
		String fromServer;
		
		try {
			if(fromUser.equals("listen")) {
				Socket socket = new Socket(svrInfo.getIP(), svrInfo.getPort());
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				
				System.out.println(clientName + "> " + "Client: The address of the endpoint the client socket [" + svrInfo.getName() + "] is bound to " + socket.getLocalSocketAddress());//the address info. of the local sockets
				System.out.println(clientName + "> " + "DEBUG: " + clientName + " " + socket.getInetAddress().getHostAddress() + " " + socket.getLocalPort());
				out.println(clientName + " " + "listen" + " " + clientName + " " + socket.getInetAddress().getHostAddress() + " " + socket.getLocalPort()); //send the client info to the servers
				
				while(true) {
					fromServer = in.readLine();
					if(fromServer == null) {
						//System.out.println(clientName + "> " + svrInfo.getName() + ": " + fromServer);
					}
					else {
						//System.out.println("Client: " + line);
						System.out.println(clientName + "> " + svrInfo.getName() + ": " + fromServer);
						Scanner lineScanner = new Scanner(fromServer);
						//lineScanner = new Scanner(line);
						String cmd = "";
						cmd = lineScanner.next();
						if(cmd.equalsIgnoreCase("restart")) {
							doLtnRestart(lineScanner);
						}
						if(cmd.equalsIgnoreCase("add")) {
							doLtnAdd(lineScanner);
						}
						if(cmd.equalsIgnoreCase("delete")) {
							doLtnDel(lineScanner);
							
							in.close();
							out.close();
							socket.close();
							return;
							//System.exit(1);
						}
					}
				}
			}
			else {
				
			}
		} catch (UnknownHostException e) {
			System.err.println("Don't know about host ");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Don't know about host ");
			System.exit(1);
		}
	}
	
	private void doLtnRestart(Scanner lineScanner) {
		String serverName = lineScanner.next();
		//String serverIP = lineScanner.next();
		int serverPort = lineScanner.nextInt();
		int index = lineScanner.nextInt();
		for(int i = 0; i < servers.size(); i++) {
			//if(serverName.equals(servers.get(index).getName())&&(serverPort!=servers.get(index).getPort())) {
			if(serverName.equals(servers.get(i).getName())&&(serverPort!=servers.get(i).getPort())) {//the restarted server found
				//servers.get(i) = new serversInfo(serverName, servers.get(i).getIP(), serverPort);
				servers.get(i).setPort(serverPort);//update the serverPort
				ServerInfo server = new ServerInfo(servers.get(i).getName(), servers.get(i).getIP(), servers.get(i).getPort());
				ClientThread w = new ClientThread(clientName, servers, server, "listen");//reconnect to the restarted server
				Thread t = new Thread(w);//create a thread bound with the clientSocket
				t.start();//start the newly created thread
				break;
			}
		}
	}
	
	private void doLtnAdd(Scanner lineScanner) {
		ArrayList<ServerInfo> currSvrs = new ArrayList<ServerInfo>();
		//read the newly added servers info and add to the [servers]
		while(lineScanner.hasNext()){
			//for(int i = 0; i < 2; i++) {//2 servers, how to determine the number of servers
			String serverName = lineScanner.next().substring(6);//skip the '(name='
			String serverIP = lineScanner.next().substring(3);//skip the 'ip='
																												//int serverPort = lineScanner.nextInt();
			String portStr = lineScanner.next().substring(5);//skip the 'port='
			int serverPort = Integer.parseInt(portStr.substring(0, portStr.length()-1));//skip the ')'
			servers.add(new ServerInfo(serverName, serverIP, serverPort));//add the ServerInfo into the servers
			currSvrs.add(new ServerInfo(serverName, serverIP, serverPort));
			System.out.println(clientName + "> " + "DEBUG: " + serverName + " " + serverIP + " " + serverPort);
		}
		
		//connect to the newly added servers
		for(int i = 0; i < currSvrs.size(); i++) {
			//ServerInfo currSvr = new ServerInfo(servers.getName(), servers.getIP(), servers.getPort());
			ClientThread w = new ClientThread(clientName, servers, currSvrs.get(i), "listen");
			Thread t = new Thread(w);//create a thread bound with the clientSocket
			t.start();//start the newly created thread
		}
	}
	
	private void doLtnDel(Scanner lineScanner) {
		//ArrayList<ServerInfo> currSvrs = new ArrayList<ServerInfo>();
		while(lineScanner.hasNext()){
			String serverName = lineScanner.next().substring(6);//skip the '(name='
			String serverIP = lineScanner.next().substring(3);//skip the 'ip='
																												//int serverPort = lineScanner.nextInt();
			String portStr = lineScanner.next().substring(5);//skip the 'port='
			int serverPort = Integer.parseInt(portStr.substring(0, portStr.length()-1));//skip the ')'
			
			for(int j = 0; j < servers.size(); j++) {
				if(servers.get(j).getName().equals(serverName)) {//found this server in servers
					servers.remove(j);//remove this server's info from servers
				}
			}
			System.out.println(clientName + "> " + "DEBUG: " + serverName + " " + serverIP + " " + serverPort + " deleted");
		}
	}
}