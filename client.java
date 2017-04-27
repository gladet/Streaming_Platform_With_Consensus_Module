import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class client {
	public static void main(String[] args) throws IOException {
		
		if (args.length != 1) { // currently 1 arguments: client name
			System.err.println(
												 "ERROR: missing argument: client name");
			System.exit(1);
		}
		
		String clientName = args[0];
		
		ArrayList<ServerInfo> servers = new ArrayList<ServerInfo>();
		boolean hasSvrsFile = true;
		int numServers = 0;
		
		Scanner inServers = null;
		//String localDir = "/tmp/92476/stream";
		//String localDir = "/Users/gladet/csc502/stream";
		String localDir = "../stream";
		String inFileName = localDir+"/serversInfo"; //the local file to store the servers info, using absolute path
		try {
			inServers = new Scanner(new File(inFileName));
		}catch (FileNotFoundException exception) {
			System.out.println(clientName + "> " + "[main] DEBUG: input file [" + inFileName + "] does not exist");
			hasSvrsFile = false;//no serversInfo file yet
		}
		
		if(hasSvrsFile == false) {//no svrsInfo file yet
			System.out.println(clientName + "> " + "please add servers to the stream platform");//wait for user input of the servers' info.
		}
		else {//serversInfo file available //servers' info available in the local file, so continue to read the file
			//serversAdded = true;
			numServers = inServers.nextInt();//read the number of servers from serversInfo file
			for(int i = 0; i < numServers; i++) {//2 should be replaced by numServers
				String serverName = inServers.next();
				String serverIP = inServers.next();
				int serverPort = inServers.nextInt();
				servers.add(new ServerInfo(serverName, serverIP, serverPort));//add the ServerInfo into the servers
																																			//lineScanner.hasChar(); //read ')'
				System.out.println(clientName + "> " + "[main] DEBUG: " + serverName + " " + serverIP + " " + serverPort);
			}
			inServers.close();//close the scanner when no more info to read
			
			//connect with servers to establish the listen channels
			for(int i = 0; i < servers.size(); i++) {
				ClientThread w = new ClientThread(clientName, servers, servers.get(i), "listen");
				Thread t = new Thread(w);//create a thread bound with the clientSocket
				t.start();//start the newly created thread
			}
		}
		
		try{
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			String fromServer;
			String fromUser;
			
			while (true) {
				System.out.print(clientName + "> ");
				//wait for user input
				fromUser = stdIn.readLine();
				if (fromUser != null) {// what is the case that fromUser == null
					System.out.println(clientName + "> " + "[main] DEBUG: user input: " + fromUser);
					
					if (fromUser.equalsIgnoreCase("quit"))//quit the client program
					{
						for(int i = 0; i < servers.size(); i++) {
							try {
								Socket socket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
								PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
								out.println(clientName + " " + fromUser);//throws exception if out stream broken due to server disconnected
								out.close();
								socket.close();
							} catch (UnknownHostException e) {
								System.err.println("[main] DEBUG: Cannot connect to " + servers.get(i).getName());
								System.exit(1);
							} catch (IOException e) {
								System.err.println("[main] DEBUG: Cannot connect to " + servers.get(i).getName());
								System.exit(1);
							}
						}
						
						System.exit(1);//exit the client program
					}
					
					Scanner lineScanner = new Scanner(fromUser);
					//lineScanner = new Scanner(line);
					String cmd = "";
					cmd = lineScanner.next();
					if(cmd.equalsIgnoreCase("add")) {
						doLocalAdd(lineScanner, servers, clientName, fromUser);
					}/*
					else if(cmd.equalsIgnoreCase("get")) {
						doGet(lineScanner, servers, clientName, fromUser);
					}*/
					else {
						doSendCmd(servers, clientName, fromUser);
					}
				}
			}
		} catch (UnknownHostException e) {
			System.err.println("[main] DEBUG: Don't know about host ");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("[main] DEBUG: Don't know about host ");
			System.exit(1);
		}
	}
	
	private static int doSvrMap(String str, ArrayList<ServerInfo> servers) {
		return Math.abs(str.hashCode())%servers.size();
	}
	
	private static void doGet(Scanner lineScanner, ArrayList<ServerInfo> servers, String clientName, String fromUser) throws IOException, UnknownHostException {
		if(!lineScanner.hasNext()) {
			System.out.println("[doGet] DEBUG: missing the topic name");
			return;
		}
		String topicName = lineScanner.next().substring(7);//skip "(topic=" at the beginning
		if(!lineScanner.hasNext()) {
			System.out.println("[doGet] DEBUG: missing the partition number");
			return;
		}
		String str = lineScanner.next();
		str = str.substring(10);//skip the 'partition=' at the beginning
		str = str.substring(0, str.length()-1);//ship ")" at the end
		int partNum = Integer.parseInt(str);
		
		str = topicName + " partition " + partNum;
		int svrNum = doSvrMap(str, servers);//get the index of the server (where the partition locates) in servers
		
		String serverName = servers.get(svrNum).getName();
		String serverIP = servers.get(svrNum).getIP();
		int serverPort = servers.get(svrNum).getPort();
		System.out.println(clientName + "> " + "[doGet] DEBUG: " + serverName + " " + serverIP + " " + serverPort);
		
		try {
			Socket socket = new Socket(serverIP, serverPort);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			System.out.println(clientName + "> " + "[doGet] DEBUG: connected to the server " + serverName);
			out.println(clientName + " " + fromUser); //send the get cmd to the backup server
			/*
			String fromServer = in.readLine();
			if(fromServer == null) {
				System.out.println(clientName + "> " + serverName + ": " + fromServer);
			}
			else {
				System.out.println(clientName + "> " + serverName + ": " + fromServer);
			}
			*/
			in.close();
			out.close();
			socket.close();
		} catch (UnknownHostException e) {
			System.err.println("[doGet] DEBUG: cannot connect to " + serverName + ", will try to connect to the backup server");
		} catch (IOException e) {
			System.err.println("[doGet] DEBUG: cannot connect to " + serverName + ", will try to connect to the backup server");
			
			//int index = (svrNum+1)%servers.size();//select the following server in servers as the backup serverString serverName = servers.get(svrNum).getName();
			int index = (svrNum-1+servers.size())%servers.size();//select the previous server in servers as the backup serverString serverName = servers.get(svrNum).getName();
			serverName = servers.get(index).getName();
			serverIP = servers.get(index).getIP();
			serverPort = servers.get(index).getPort();
			Socket socket = new Socket(serverIP, serverPort);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			System.out.println(clientName + "> " + "[doGet] DEBUG: connected to the backup server " + serverName);
			out.println(clientName + " " + fromUser); //send the get cmd to the backup server
			/*
			String fromServer = in.readLine();
			if(fromServer == null) {
				System.out.println(clientName + "> " + serverName + ": " + fromServer);
			}
			else {
				System.out.println(clientName + "> " + serverName + ": " + fromServer);
			}
			*/
			in.close();
			out.close();
			socket.close();
		}
	}
	
	private static void doSendCmd(ArrayList<ServerInfo> servers, String clientName, String fromUser) {
		for(int i = 0; i < servers.size(); i++) {
			try {
				Socket socket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				//BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out.println(clientName + " " + fromUser);//throws exception if out stream broken due to server disconnected
				/*
				String fromServer = in.readLine();
				//System.out.println(clientName + "> " + servers.get(i).getName() + ": " + fromServer);
				System.out.println(clientName + "> " + fromServer); //NOT display server name to avoid possible arrayoutofbound access exception
				*/
				//in.close();
				out.close();
				socket.close();
			} catch (UnknownHostException e) {
				System.err.println("[doSendCmd] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			} catch (IOException e) {
				System.err.println("[doSendCmd] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			}
		}
	}
	
	private static void doLocalAdd(Scanner lineScanner, ArrayList<ServerInfo> servers, String clientName, String fromUser) throws IOException, UnknownHostException {
		ArrayList<Socket> socket = new ArrayList<Socket>(); //store the sockets connected with the servers
		ArrayList<PrintWriter> out = new ArrayList<PrintWriter>(); //out streams to servers
		ArrayList<BufferedReader> in = new ArrayList<BufferedReader>();//in streams fr servers
		
		ArrayList<ServerInfo> currSvrs = new ArrayList<ServerInfo>();
		
		//read the servers info to be added
		while(lineScanner.hasNext()){
			//for(int i = 0; i < 2; i++) {//2 servers, how to determine the number of servers
			String serverName = lineScanner.next().substring(6);//skip the '(name='
			String serverIP = lineScanner.next().substring(3);//skip the 'ip='
																												//int serverPort = lineScanner.nextInt();
			String portStr = lineScanner.next().substring(5);//skip the 'port='
			int serverPort = Integer.parseInt(portStr.substring(0, portStr.length()-1));//skip the ')'
			servers.add(new ServerInfo(serverName, serverIP, serverPort));//add the ServerInfo into the servers
			currSvrs.add(new ServerInfo(serverName, serverIP, serverPort));
			System.out.println(clientName + "> " + "[doLocalAdd] DEBUG: " + serverName + " " + serverIP + " " + serverPort);
		}
		
		//connect with the newly added servers to establish the listen channels
		for(int i = 0; i < currSvrs.size(); i++) {
			//ServerInfo currSvr = new ServerInfo(servers.getName(), servers.getIP(), servers.getPort());
			ClientThread w = new ClientThread(clientName, servers, currSvrs.get(i), "listen");
			Thread t = new Thread(w);//create a thread bound with the clientSocket
			t.start();//start the newly created thread
		}
		
		String svrName = "";
		try {
			//create sockets and corresponding streams to communciate with servers
			for(int i = 0; i < servers.size(); i++) {
				svrName = servers.get(i).getName();
				socket.add(new Socket(servers.get(i).getIP(), servers.get(i).getPort()));//initiate the sockets
				out.add(new PrintWriter(socket.get(i).getOutputStream(), true));//initiate the out streams
				in.add(new BufferedReader(new InputStreamReader(socket.get(i).getInputStream())));
			}
		} catch (UnknownHostException e) {
			System.err.println("[doLocalAdd] DEBUG: cannot connect to " + svrName);
			System.exit(1);
		} catch (IOException e) {
			System.err.println("[doLocalAdd] DEBUG: cannot connect to " + svrName);
			System.exit(1);
		}
		
		if(servers.size() != currSvrs.size()){//NOT add cmd when the client program starts, should be a later add cmd
			//generate the add cmd including all the servers info
			String addCmd = "add ";
			for(int i = 0; i < servers.size(); i++) {
				addCmd += "(name=" + servers.get(i).getName() + " " + "ip=" + servers.get(i).getIP() + " " + "port=" + servers.get(i).getPort() + ") ";
				System.out.println("[doLocalAdd] DEBUG: " + addCmd);
			}
			
			//send the add cmd to the previously added servers
			for(int i = 0; i < servers.size()-currSvrs.size(); i++) {
				out.get(i).println(clientName + " " + fromUser);
			}
			
			//send the add cmd to the newly added servers
			for(int i = 0; i < currSvrs.size(); i++) {
				int index = servers.size()-currSvrs.size()+i;
				out.get(index).println(clientName + " " + addCmd);
			}
		}
		else {//add cmd when the client program starts
			for(int i = 0; i < servers.size(); i++) {
				out.get(i).println(clientName + " " + fromUser);//throws exception if out stream broken due to server disconnected
			}
		}
		
		String fromServer;
		//get and display the ack message from servers
		for(int i = 0; i < servers.size(); i++) {//possibly the servers size already changed
			fromServer = in.get(i).readLine();
			//System.out.println(clientName + "> " + fromServer);
			System.out.println(clientName + "> [doLocalAdd] DEBUG: " + servers.get(i).getName() + ": " + fromServer);
		}
		
		//close the sockets and corresponding streams
		for(int i = 0; i < servers.size(); i++) {
			out.get(i).close();
			in.get(i).close();
			socket.get(i).close();
		}
	}
}