import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.lang.Runtime;
import java.util.Scanner;

public class server {
	public static void main(String[] args) throws IOException {
		
		if (args.length != 1) {
			System.err.println("missing argument: server name");
			System.exit(1);
		}
		
		ServerData svrData = new ServerData();//create the ServerData to be used by server and ServerThread objects
		RaftData raftData = new RaftData();//create the ServerData to be used by server and ServerThread objects
		ArrayList<ServerInfo> servers = svrData.getServers();
		
		String localServerName = args[0];
		int portNumber = 0;//for automatic port number allocation
		
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket(portNumber);//create the serversocket with a port number automatically allocated by the system
			//System.out.println("DEBUG: " + localServerName + " at IP address " + serverSocket.getInetAddress().getHostAddress() + " and port number: " + serverSocket.getLocalPort());
			System.out.println("[server main] DEBUG: " + localServerName + " at IP address " + serverSocket.getInetAddress().getLocalHost().getHostAddress() + " and port number: " + serverSocket.getLocalPort());
		} catch (IOException e) {
			//System.err.println("Could not listen on port " + portNumber);
			System.err.println("creating serversocket failed");
			System.exit(-1);
		}
		
		//check if the server is restarted due to the previous crash
		//boolean svrCrashed = true;
		boolean svrCrashed = false;
		
		//ArrayList<ServerInfo> servers = new ArrayList<ServerInfo>();
		boolean serversAdded = true;
		//boolean serversAdded = false;//by default no server added yet
		int numServers = 0;
		
		Scanner inServers = null;
		//String localDir = "/tmp/92476/stream";
		String localDir = "/Users/gladet/csc502/stream";
		String inFileName = localDir+"/serversInfo"; //the local file to store the servers info, using absolute path
		try {
			inServers = new Scanner(new File(inFileName));
		}catch (FileNotFoundException exception) {
			System.out.println("[server main] DEBUG: local info file [" + inFileName + "] does not exist");
			//numServers == 0;//no serversInfo file yet
			serversAdded = false;//no serversInfo file yet
		}
		//int numServers = inServers.nextInt();//read the number of servers from serversInfo file
		String fromUser = "";
		int index = 0;
		//if(numServers == 0) {
		if(serversAdded == true) {//serversInfo file available //servers' info available in the local file, so continue to read the file
			numServers = inServers.nextInt();//read the number of servers from serversInfo file
			for(int i = 0; i < numServers; i++) {//2 should be replaced by numServers
				String serverName = inServers.next();
				String serverIP = inServers.next();
				int serverPort = inServers.nextInt();
				if(localServerName.equals(serverName)&&(serverSocket.getLocalPort()!=serverPort)){
					System.out.println("Do you want to restart? (\"y\" or \"n\")");
					System.out.println("y: will restart the previous session based on the local info files");
					System.out.println("n: will start a new session and DELETE the local info files");
					
					Scanner in = new Scanner(System.in);
					String cmd = "";
					while(in.hasNext()) {
						cmd = in.next();
						if(cmd.equalsIgnoreCase("y")) {
							svrCrashed = true;
							index = i;
							serverPort = serverSocket.getLocalPort();
							System.out.println("[server main] DEBUG: " + serverName + " restarted");
							break;
						}
						else if(cmd.equalsIgnoreCase("n")) {
							try {
								Runtime.getRuntime().exec("rm " + inFileName);//remove the file
								inFileName = localDir+"/"+localServerName+"_data";
								Runtime.getRuntime().exec("rm " + inFileName);//remove the file
								inFileName = localDir+"/"+localServerName+"_info";
								Runtime.getRuntime().exec("rm " + inFileName);//remove the file
								break;
							} catch(IOException e) {
								System.out.println("[server main] DEBUG: cannot delete local info files: "+inFileName);
								e.printStackTrace();
							}
						}
						else System.out.println("please input \"y\" or \"no\"");
					}
					if(cmd.equalsIgnoreCase("n")) {
						break;
					}
				}
				ServerInfo svrInfo = new ServerInfo(serverName, serverIP, serverPort);
				servers.add(svrInfo);//add the ServerInfo into the servers
			}
			inServers.close();//close the scanner when no more info to read
			if(svrCrashed == false) {//NOT need to add servers here
				for(int i = servers.size()-1; i >= 0; i--) {
					servers.remove(i);
				}
			}
		}
		
		if(svrCrashed == true) {
			//listen to all servers
			for(int i = 0; i < servers.size(); i++) {
				new ListenThread(localServerName, servers, servers.get(i), raftData).start();
			}
			//start the FollowerThread
			new FollowerThread(localServerName, servers, raftData).start();
			/*
			if(servers.size()==1) {//only one server
				recoverFrFile(svrData, localServerName);
			}*/
			recoverFrFile(svrData, localServerName, raftData);//need to recover the backup data
			//print log
			raftData.printLog();
			
			//notify all the servers to rewrite the server port number in the servers file
			//connected to all the servers except itself
			String backupSvrName = backupSvr(servers, localServerName);//generate the backup server name of localServerName
			
			for(int i = 0; i < servers.size(); i++) {
				if(!localServerName.equals(servers.get(i).getName())) {
					try {
						//create the socket and corresponding streams to communicate with the server
						Socket currSocket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
						PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
						Scanner inSvr = new Scanner(currSocket.getInputStream());
						System.out.println("[server main] DEBUG: " + localServerName + " " + "restart" + " " + serverSocket.getLocalPort() + " " + index);
						outSvr.println(localServerName + " " + "restart" + " " + localServerName + " " + serverSocket.getLocalPort() + " " + index);//send the restart message to the server
						//get and display ack message fr the server
						String ackMsg = inSvr.nextLine();
						System.out.println("[server main] DEBUG: from " + servers.get(i).getName() + ": " + ackMsg);
						/* //NOT recover fr backup server after deploying consensus mdoule
						// check if servers.get(i) is the backup server of localServerName
						if(backupSvrName.equals(servers.get(i).getName())) { //servers.get(i) is the back up server
							//recover from backup server
							recoverfrBkpSvr(svrData, inSvr);
						}
						*/
						// check if localServerName is the backup server of servers.get(i)
						String rvBkpSvr = backupSvr(servers, servers.get(i).getName());
						if(localServerName.equals(rvBkpSvr)) {
							recoverBkp(svrData, inSvr);
						}
						//close the socket and corresponding streams
						inSvr.close();
						outSvr.close();
						currSocket.close();
					} catch (UnknownHostException e) {
						System.err.println("[server main] DEBUG: Cannot connect to " + servers.get(i).getName());
						//System.exit(1);
					} catch (IOException e) {
						System.err.println("[server main] DEBUG: Cannot connect to " + servers.get(i).getName());
						//System.exit(1);
					}
				}
			}
			
			//generate the svrPartMap
			//genSvrPartMap(svrData, servers);
			
			String outFileName = null;
			PrintWriter outServers = null;
			
			//initialize the serversInfo file reader
			try {
				//localDir = "/tmp/92476/stream";
				localDir = "/Users/gladet/csc502/stream";
				outFileName = localDir+"/serversInfo";
				
				//outFileName = "serversInfo"; //the local file to store the servers info, using relative path
				outServers = new PrintWriter(outFileName);
			}catch (FileNotFoundException exception) {
				System.out.println("[server main] DEBUG: ERROR: output file [" + outFileName + "] does not exist");
			}
			outServers.println(servers.size());//write the number of servers into local file
			for(int i = 0; i < servers.size(); i++) {//2 servers
				outServers.println(servers.get(i).getName() + " " + servers.get(i).getIP() + " " + servers.get(i).getPort());//write the servers' info to the local file
			}
			outServers.close();//close the out stream when no more info to write to the local file
			
			//chmod 777
			try {
				File file = new File(outFileName);
				Runtime.getRuntime().exec("chmod 777 " + outFileName);
			} catch(IOException e) {
				e.printStackTrace();
			}
			//***
		}
		else {
			for(int i = servers.size()-1; i >= 0; i--) {
				servers.remove(i);
			}
		}
		
		while(true) {
			ServerThread w;
			try{
				//server.accept() returns a client socket
				Socket clientSocket = serverSocket.accept(); //connect with the client socket
				//System.out.println("DEBUG: The automatically allocated port number of the clientSocket is: " + clientSocket.getLocalPort());
				//System.out.println("DEBUG: The IP address of the clientSocket is: " + clientSocket.getInetAddress());
				
				//w = new ServerThread(clientSocket, localServerName, svrData);
				w = new ServerThread(clientSocket, localServerName, svrData, raftData);
				Thread t = new Thread(w);//create a thread bound with the clientSocket
				t.start();//start the newly created thread
			} catch (IOException e) {
				System.out.println("[server main] DEBUG: Accept failed");
				System.exit(-1);
			}
		}
	}
	
	//find the index of the server in [servers] where the partition should locate
	private static int doSvrMap(String str, ArrayList<ServerInfo> servers) {
		return Math.abs(str.hashCode())%servers.size();
	}
	
	//generate the new SvrPartMap
	private static void genSvrPartMap(ServerData svrData, ArrayList<ServerInfo> servers) {
		Map<String, ArrayList<ArrayList<Record>>> topics = svrData.getTopics();
		HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap = svrData.getSvrPartMap();
		
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {
			String currTopic = e.getKey();//current topic name
			int numPart = e.getValue().size();//number of partitions of the current topic
			for(int i = 0; i < numPart; i++) {//iterate over parts
				String str = currTopic + " partition " + i;//create the string by combining the currTopic and partition number
				int svrNum = doSvrMap(str, servers);//servers already added to the stream platform by a clent
				if(svrPartMap.get(servers.get(svrNum).getName()) == null) {//this server is not in svrPartMap yet
					svrPartMap.put(servers.get(svrNum).getName(), new HashMap<String, HashSet<Integer>>());
				}
				if(svrPartMap.get(servers.get(svrNum).getName()).get(currTopic) == null) {//topicName is not associated with this server yet
					svrPartMap.get(servers.get(svrNum).getName()).put(currTopic, new HashSet<Integer>());
				}
				svrPartMap.get(servers.get(svrNum).getName()).get(currTopic).add(i);//associate partition i of topicName with this server
			}
		}
	}
	
	//find the backup server of svrName, return null if cannot find it
	private static String backupSvr(ArrayList<ServerInfo> servers, String svrName) {
		for(int i = 0; i < servers.size(); i++) {
			if(servers.get(i).getName().equals(svrName)) {//find the index of the server in servers
				//int index = (i+1)%servers.size();//select the following server in servers as the backup server
				int index = (i-1+servers.size())%servers.size();//select the previous server in servers as the backup server
				return servers.get(index).getName();
			}
		}
		return null;
	}

	//receive the topics and svrPartMap from reverse-backup server and store the data: topics->bkpTopics
	private static void recoverBkp(ServerData svrData, Scanner inSvr) {
		//recover the backup topics from the reverse-backup server!
		Map<String, ArrayList<ArrayList<Record>>> bkpTopics = svrData.getBkpTopics();
		String line = inSvr.nextLine();
		System.out.println("[recoverBkp] DEBUG "+line);//print out the data&info message for debug purpose
		Scanner lineScanner = new Scanner(line);//not really necessary to use this scanner
		int numTopics = lineScanner.nextInt();//read number of topics
		for(int i = 0; i < numTopics; i++) {//iterate over the topics
			String currTopic = lineScanner.next();//read topic name
			int numPart = lineScanner.nextInt();//read number of parts
			ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
			for(int j = 0; j < numPart; j++) {//iterate over the parts
				topic.add(new ArrayList<Record>());
				int numRecord = lineScanner.nextInt();//read number of records
				for(int k = 0; k < numRecord; k++) {//add records
					topic.get(j).add(new Record(lineScanner.next(), lineScanner.nextInt()));
				}
			}
			bkpTopics.put(currTopic, topic);
		}
		svrData.setBkpTopics(bkpTopics);
		//receive and store the svr-parts map info of reverse-backup server
		HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap = svrData.getSvrPartMap();
		String currSvr = lineScanner.next();//read the server name
		svrPartMap.put(currSvr, new HashMap<String, HashSet<Integer>>());
		numTopics = lineScanner.nextInt();//read number of topics
		for(int j = 0; j < numTopics; j++) {
			String currTopic = lineScanner.next();
			svrPartMap.get(currSvr).put(currTopic, new HashSet<Integer>());
			int numPart = lineScanner.nextInt();//read number of parts
			for(int k = 0; k < numPart; k++) {
				svrPartMap.get(currSvr).get(currTopic).add(lineScanner.nextInt());
			}
		}
	}
	
	//recover the data and info sent from the the backup server
	private static void recoverfrBkpSvr(ServerData svrData, Scanner inSvr) {
		//recover the topics from the backup topics of the backup server
		Map<String, ArrayList<ArrayList<Record>>> topics = svrData.getTopics();
		String line = inSvr.nextLine();
		System.out.println("[recoverfrBkpSvr] DEBUG "+line);//print out the data&info message for debug purpose
		Scanner lineScanner = new Scanner(line);//not really necessary to use this scanner
		int numTopics = lineScanner.nextInt();//read number of topics
		for(int i = 0; i < numTopics; i++) {//iterate over the topics
			String currTopic = lineScanner.next();//read topic name
			int numPart = lineScanner.nextInt();//read number of parts
			ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
			for(int j = 0; j < numPart; j++) {//iterate over the parts
				topic.add(new ArrayList<Record>());
				int numRecord = lineScanner.nextInt();//read number of records
				for(int k = 0; k < numRecord; k++) {//add records
					topic.get(j).add(new Record(lineScanner.next(), lineScanner.nextInt()));
				}
			}
			topics.put(currTopic, topic);
		}
		svrData.setTopics(topics);
		
		HashMap<String, Integer> numSub = new HashMap<String, Integer>();//record the number of subscribers of each topic
		HashMap<String, ArrayList<String>> subList = new HashMap<String, ArrayList<String>>();//record the subscribers of each topic
		HashMap<String, String[]> partSubMap = new HashMap<String, String[]>();//record the subscriber of each partition of each topic
		HashMap<String, String[]> partSvrMap = new HashMap<String, String[]>();//record the server where each partition of each topic locates
		HashMap<String, int[]> now = new HashMap<String, int[]>();//record the size of each partition of each topic
		HashMap<String, int[]> offset = new HashMap<String, int[]>();//record the current offset of each partition of each topic
		HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap = new HashMap<String, HashMap<String, HashSet<Integer>>>();//record the partition number of each topic that the client subscribes
		
		//read now info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numPart = lineScanner.nextInt();
			now.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
			for(int j = 0; j < numPart; j++) {
				now.get(currTopic)[j] = lineScanner.nextInt();
			}
		}
		svrData.setNow(now);
		
		//read offset info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numPart = lineScanner.nextInt();
			offset.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
			for(int j = 0; j < numPart; j++) {
				offset.get(currTopic)[j] = lineScanner.nextInt();
			}
		}
		svrData.setOffset(offset);
		
		//read numsub info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numS = lineScanner.nextInt();
			numSub.put(currTopic, numS);
		}
		svrData.setNumSub(numSub);
		
		//read sublist info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numS = lineScanner.nextInt();
			subList.put(currTopic, new ArrayList<String>());
			for(int j = 0; j < numS; j++) {
				subList.get(currTopic).add(lineScanner.next());
			}
		}
		svrData.setSubList(subList);
		
		//read partsubmap info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numPart = lineScanner.nextInt();
			partSubMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
			for(int j = 0; j < numPart; j++) {
				partSubMap.get(currTopic)[j] = lineScanner.next();
			}
		}
		svrData.setPartSubMap(partSubMap);
		
		//read partsvrmap info
		for(int i = 0; i < numTopics; i++) {
			String currTopic = lineScanner.next();
			int numPart = lineScanner.nextInt();
			partSvrMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
			for(int j = 0; j < numPart; j++) {
				partSvrMap.get(currTopic)[j] = lineScanner.next();
			}
		}
		svrData.setPartSvrMap(partSvrMap);
		
		//read svrPartMap info -> might be INCOMPLETE or INACCURATE!
		int numSvr = lineScanner.nextInt();//read number of servers
		for(int i = 0; i < numSvr; i++) {
			String currSvr = lineScanner.next();//read the server name
			svrPartMap.put(currSvr, new HashMap<String, HashSet<Integer>>());
			numTopics = lineScanner.nextInt();//read number of topics
			for(int j = 0; j < numTopics; j++) {
				String currTopic = lineScanner.next();
				svrPartMap.get(currSvr).put(currTopic, new HashSet<Integer>());
				int numPart = lineScanner.nextInt();//read number of parts
				for(int k = 0; k < numPart; k++) {
					svrPartMap.get(currSvr).get(currTopic).add(lineScanner.nextInt());
				}
			}
		}
		svrData.setSvrPartMap(svrPartMap);
	}
	
	//recover the data and info from the local file
	private static void recoverFrFile(ServerData svrData, String localServerName, RaftData raftData) {
		//recover log
		Scanner inLog = null;
		//String localDir = "/tmp/92476/stream";
		String localDir = "/Users/gladet/csc502/stream";
		String inFileName = localDir+"/"+localServerName+"_log"; //the local file to store the log, using absolute path
		try {
			inLog = new Scanner(new File(inFileName));
			
			ArrayList<LogEntry> log = raftData.getLog();
			log = new ArrayList<LogEntry>();
			int numEntries = inLog.nextInt();//read number of log entries
			for(int i = 0; i < numEntries; i++) {
				log.add(new LogEntry(inLog.nextInt(), inLog.next(), inLog.nextInt(), inLog.nextLine().substring(1)));
			}
			raftData.setLog(log);//reset log in raftData
			
			int lastApplied = inLog.nextInt();
			raftData.setLastApplied(lastApplied);
			int committedIndex = inLog.nextInt();
			raftData.setCommittedIndex(committedIndex);
		}catch (FileNotFoundException exception) {
			System.out.println("[recoverFrFile] DEBUG: local data file [" + inFileName + "] does not exist, cannot recover data");
		}
		
		Scanner inData = null;
		localDir = "/tmp/92476/stream";
		//String localDir = "/Users/gladet/csc502/stream";
		inFileName = localDir+"/"+localServerName+"_data"; //the local file to store the servers info, using absolute path
		try {
			inData = new Scanner(new File(inFileName));
			
			//recover topics
			Map<String, ArrayList<ArrayList<Record>>> topics = svrData.getTopics();
			int numTopics = inData.nextInt();//read number of topics
			for(int i = 0; i < numTopics; i++) {//iterate over the topics
				String currTopic = inData.next();//read topic name
				int numPart = inData.nextInt();//read number of parts
				ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
				for(int j = 0; j < numPart; j++) {//iterate over the parts
					topic.add(new ArrayList<Record>());
					int numRecord = inData.nextInt();//read number of records
					for(int k = 0; k < numRecord; k++) {//add records
						topic.get(j).add(new Record(inData.next(), inData.nextInt()));
					}
				}
				topics.put(currTopic, topic);
			}
			
			//recover backup topics
			Map<String, ArrayList<ArrayList<Record>>> bkpTopics = svrData.getBkpTopics();
			numTopics = inData.nextInt();//read number of topics
			for(int i = 0; i < numTopics; i++) {//iterate over the topics
				String currTopic = inData.next();//read topic name
				int numPart = inData.nextInt();//read number of parts
				ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
				for(int j = 0; j < numPart; j++) {//iterate over the parts
					topic.add(new ArrayList<Record>());
					int numRecord = inData.nextInt();//read number of records
					for(int k = 0; k < numRecord; k++) {//add records
						topic.get(j).add(new Record(inData.next(), inData.nextInt()));
					}
				}
				bkpTopics.put(currTopic, topic);
			}
		}catch (FileNotFoundException exception) {
			System.out.println("[recoverFrFile] DEBUG: local data file [" + inFileName + "] does not exist, cannot recover data");
		}
		
		Scanner inInfo = null;
		localDir = "/tmp/92476/stream";
		//localDir = "/Users/gladet/csc502/stream";
		inFileName = localDir+"/"+localServerName+"_info"; //the local file to store the servers info, using absolute path
		try {
			inInfo = new Scanner(new File(inFileName));
			int numTopics = svrData.getTopics().size();
			
			Map<String, Integer> numSub = svrData.getNumSub();
			Map<String, ArrayList<String>> subList = svrData.getSubList();
			Map<String, String[]> partSubMap = svrData.getPartSubMap();
			Map<String, String[]> partSvrMap = svrData.getPartSvrMap();
			Map<String, int[]> now = svrData.getNow();
			Map<String, int[]> offset = svrData.getOffset();
			HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap = svrData.getSvrPartMap();
			
			//read now info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numPart = inInfo.nextInt();
				now.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					now.get(currTopic)[j] = inInfo.nextInt();
				}
			}
			
			//read offset info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numPart = inInfo.nextInt();
				offset.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					offset.get(currTopic)[j] = inInfo.nextInt();
				}
			}
			
			//read numsub info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numS = inInfo.nextInt();
				numSub.put(currTopic, numS);
			}
			
			//read sublist info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numS = inInfo.nextInt();
				subList.put(currTopic, new ArrayList<String>());
				for(int j = 0; j < numS; j++) {
					subList.get(currTopic).add(inInfo.next());
				}
			}
			
			//read partsubmap info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numPart = inInfo.nextInt();
				partSubMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					partSubMap.get(currTopic)[j] = inInfo.next();
				}
			}
			
			//read partsvrmap info
			for(int i = 0; i < numTopics; i++) {
				String currTopic = inInfo.next();
				int numPart = inInfo.nextInt();
				partSvrMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					partSvrMap.get(currTopic)[j] = inInfo.next();
				}
			}
			
			//read partsvrmap info
			int numSvr = inInfo.nextInt();//read number of servers
			for(int i = 0; i < numSvr; i++) {
				String currSvr = inInfo.next();//read the server name
				svrPartMap.put(currSvr, new HashMap<String, HashSet<Integer>>());
				numTopics = inInfo.nextInt();//read number of topics
				for(int j = 0; j < numTopics; j++) {
					String currTopic = inInfo.next();
					svrPartMap.get(currSvr).put(currTopic, new HashSet<Integer>());
					int numPart = inInfo.nextInt();//read number of parts
					for(int k = 0; k < numPart; k++) {
						svrPartMap.get(currSvr).get(currTopic).add(inInfo.nextInt());
					}
				}
			}
		} catch (FileNotFoundException exception) {
			System.out.println("[recoverFrFile] DEBUG: local info file [" + inFileName + "] does not exist, cannot recover info");
		}
	}
}
