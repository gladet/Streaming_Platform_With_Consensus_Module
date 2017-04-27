import java.util.ArrayList;
import java.lang.String;

import java.io.*;
import java.net.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.lang.NullPointerException;

import java.util.Iterator;
import java.util.GregorianCalendar;

class ServerThread implements Runnable {
	//instance variables
	private Socket client;
	private String localServerName;
	private ServerData svrData;
	private RaftData raftData;
	
	private Map<String, ArrayList<ArrayList<Record>>> topics;
	private Map<String, ArrayList<ArrayList<Record>>> bkpTopics;
	private ArrayList<ServerInfo> servers;
	private ArrayList<ClientInfo> clients;
	private Map<String, Socket> clientSockets;
	private Map<String, Integer> numSub;//record the number of subscribers of each topic
	private Map<String, ArrayList<String>> subList;//record the subscribers of each topic
	private Map<String, String[]> partSubMap;//record the subscriber of each partition of each topic
	private Map<String, String[]> partSvrMap;//record the server where each partition of each topic locates
	private Map<String, int[]> now;//record the size of each partition of each topic
	private Map<String, int[]> offset;//record the current offset of each partition of each topic
	
	private HashMap<String, HashMap<String, HashSet<Integer>>> cliSub;//record the partition number of each topic that the client subscribes
	private HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap;//record the partition number of each topic that the client subscribes
	
	private int state;//the state of the server, can be FOLLOWER, CANDIDATE, LEADER
	private int currTerm;//latest term server has seen
	private String votedFor;
	
	private int votes;
	private int numReplicated;
	private int numReplyVote;
	private ArrayList<LogEntry> log;//replicated log to record all the commands from all the clients in chronological order
	private ArrayList<Integer> nextIndex;
	private ArrayList<Integer> matchIndex;
	private int committedIndex;
	private Map<String, Socket> serverSockets;//store the listen socket of each server
	private long followerStartTime;
	private Map<String, Integer> cltCmdIndex;
	
	private static final int FOLLOWER = 0;
	private static final int CANDIDATE = 1;
	private static final int LEADER = 2;
	
	//Constructor
	ServerThread(Socket client, String localServerName, ServerData svrData, RaftData raftData) {
		this.client = client;
		this.localServerName = localServerName;
		this.svrData = svrData;
		this.topics = svrData.getTopics();
		this.bkpTopics = svrData.getBkpTopics();
		this.servers = svrData.getServers();
		this.clients = svrData.getClients();
		this.clientSockets = svrData.getClientSockets();
		this.numSub = svrData.getNumSub();
		this.subList = svrData.getSubList();
		this.partSubMap = svrData.getPartSubMap();
		this.partSvrMap = svrData.getPartSvrMap();
		this.now = svrData.getNow();
		this.offset = svrData.getOffset();
		this.cliSub = svrData.getCliSub();
		this.svrPartMap = svrData.getSvrPartMap();
		
		this.raftData = raftData;
		this.state = raftData.getState();
		this.currTerm = raftData.getCurrTerm();
		this.votedFor = raftData.getVotedFor();
		this.log = raftData.getLog();
		this.serverSockets = raftData.getServerSockets();
		this.votes = raftData.getVotes();
		this.committedIndex = raftData.getCommittedIndex();
		this.nextIndex = raftData.getNextIndex();
		this.matchIndex = raftData.getMatchIndex();
		this.numReplicated = raftData.getNumReplicated();
		this.numReplyVote = raftData.getNumReplyVote();
		this.followerStartTime = raftData.getFollowerStartTime();
		this.cltCmdIndex = raftData.getCltCmdIndex();
	}
	
	public boolean isValidCmd(String cmd) {
		if(cmd.equalsIgnoreCase("replyappend")||cmd.equalsIgnoreCase("svrlisten")||cmd.equalsIgnoreCase("append")||cmd.equalsIgnoreCase("reqvote")||cmd.equalsIgnoreCase("addbkp")||cmd.equalsIgnoreCase("backup")||cmd.equalsIgnoreCase("move")||cmd.equalsIgnoreCase("restart")||cmd.equalsIgnoreCase("unsubscribe")||cmd.equalsIgnoreCase("remove")||cmd.equalsIgnoreCase("delete")||cmd.equalsIgnoreCase("listen")||cmd.equalsIgnoreCase("add")||cmd.equalsIgnoreCase("create")||cmd.equalsIgnoreCase("subscribe")||cmd.equalsIgnoreCase("publish")||cmd.equalsIgnoreCase("get")||cmd.equalsIgnoreCase("quit"))
		{
			return true;
		}
		return false;
	}
	
	public boolean isSvrMsg(String cmd) {
		if(cmd.equalsIgnoreCase("replyappend")||cmd.equalsIgnoreCase("svrlisten")||cmd.equalsIgnoreCase("append")||cmd.equalsIgnoreCase("reqvote")||cmd.equalsIgnoreCase("addbkp")||cmd.equalsIgnoreCase("backup")||cmd.equalsIgnoreCase("move")||cmd.equalsIgnoreCase("restart"))
		{
			return true;
		}
		return false;
	}
	
	public boolean isCltMsg(String cmd) {
		if(isValidCmd(cmd) && (!isSvrMsg(cmd)))
		{
			return true;
		}
		return false;
	}
	
	public void run(){
		String line;
		BufferedReader in = null;
		PrintWriter out = null;
		PrintWriter outServers = null;
		PrintWriter outClients = null;
		String outFileName = null;
		String clientName = null;
		
		try{
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			out = new PrintWriter(client.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("[ServerThread run] DEBUG: in or out failed");
			//System.exit(-1);
			return;
		}
		
		//while(true){
		if(true){
			try{
				line = in.readLine();
				if(line == null) {
					System.out.println("[ServerThread run] DEBUG: disconnected");
					//System.exit(-1);
					return;
				}
				//System.out.println("Client: " + line);
				
				Scanner lineScanner = new Scanner(line);
				//lineScanner = new Scanner(line);
				String cmd = "";
				String restLine = "";
				int cmdIndex = 0;
				if(!lineScanner.hasNext()) {
					System.out.println("[ServerThread run] DEBUG: no command given");
				}
				else {
					clientName = lineScanner.next();
					/*
					 int cmdIndex = lineScanner.nextInt();
					 if(cltCmdIndex.get(clientName) < cmdIndex) {//a new command from clientName
						cltCmdIndex.put(clientName, cmdIndex);//update cltCmdIndex
						log.add(new LogEntry(currTerm, clientName, cmdIndex, restLine));
					 }
					 */
					restLine = lineScanner.nextLine();
					//truncate the first character of restLine -> " "
					restLine = restLine.substring(1);
					lineScanner = new Scanner(restLine);
					cmd = lineScanner.next();
				}
				
				//handling non valid command
				if(!isValidCmd(cmd)) {
					System.out.println("[ServerThread run] DEBUG: non valid command");
					out.println("non valid command");
					return;
				}
				else if(!cmd.equalsIgnoreCase("append") && !cmd.equalsIgnoreCase("replyappend")) {//NOT print append and replyappend command
					System.out.println("[ServerThread run] DEBUG: " + clientName + " " + line);
				}
				
				//append the client command to the log when is LEADER
				//if((state = raftData.getState()) == LEADER) {
				if((state = raftData.getState()) == LEADER && !isSvrMsg(cmd) && !cmd.equalsIgnoreCase("add") && !cmd.equalsIgnoreCase("listen")) {
					currTerm = raftData.getCurrTerm();
					log.add(new LogEntry(currTerm, clientName, cmdIndex, restLine));
					//write the updated log to local file
					raftData.writeLog(localServerName);
					
					//print log
					raftData.printLog();
				}
				
				//exeCmd(cmd, lineScanner, in, out, outServers, outClients, outFileName, clientName);
				if(isSvrMsg(cmd)) {
					exeSvrCmd(cmd, lineScanner, in, out, outServers, outClients, outFileName, clientName);
				}
				/*
				 if(isCltMsg(cmd)) {
					exeCltCmd(cmd, lineScanner, in, out, outServers, outClients, outFileName, clientName);
				}
				 */
				//only apply "add" command to state machine
				if(cmd.equalsIgnoreCase("add")||cmd.equalsIgnoreCase("listen")) {
					exeCltCmd(cmd, lineScanner, out, outServers, outClients, outFileName, clientName);
				}
			}catch (IOException e) {
				System.out.println("[ServerThread run] DEBUG: Read failed");
				//System.exit(-1);
				return;
			}catch(NoSuchElementException e) {
				System.out.println("[ServerThread run] DEBUG: " + clientName + "disconnected");
				//System.exit(-1);
				return;
			}catch(NullPointerException e) {
				System.out.println("[ServerThread run] DEBUG: " + clientName + " disconnected");
				//System.exit(-1);
				return;
			}
		}
	}
	
	private void exeCltCmd(String cmd, Scanner lineScanner, PrintWriter out, PrintWriter outServers, PrintWriter outClients, String outFileName, String clientName) throws IOException {
		//if (line.equalsIgnoreCase("quit"))
		if (cmd.equalsIgnoreCase("quit")) {
			//break;
			clientSockets.get(clientName).close();//close the socket associated with the clientName needs to quit
			clientSockets.remove(clientName);//remove clientName from clientSockets
			return;
			//out.println(line);
			
		}
		
		//set up the listen channel to a client
		if(cmd.equalsIgnoreCase("listen")) {
			doListen(lineScanner, out, outClients, outFileName, clientName);
		}
		
		if(cmd.equalsIgnoreCase("add")) {
			doAdd(lineScanner, out, outServers, outFileName, clientName);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("delete")) {
			doDel(lineScanner, out, outServers, outFileName);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("create")) {
			doCreate(lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("remove")) {
			doRemove(clientName, lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("publish")) {
			doPublish(lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("subscribe")) {
			doSubscribe(clientName, lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("unsubscribe")) {
			doUnsubscribe(clientName, lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		if(cmd.equalsIgnoreCase("get")) {
			doGet(clientName, lineScanner, out);
			
			//out.close();
			//in.close();
			//client.close();
		}
		
		writeData();
		writeInfo();
	}
	
	private void exeSvrCmd(String cmd, Scanner lineScanner, BufferedReader in, PrintWriter out, PrintWriter outServers, PrintWriter outClients, String outFileName, String clientName) throws IOException {
		/*if(cmd.equalsIgnoreCase("replyvote")) {
			procReplyVote(lineScanner, clientName);
		}
		*/
		if(cmd.equalsIgnoreCase("replyappend")) {
			procReplyAppend(lineScanner, clientName);
			
			writeData();
			writeInfo();
		}
		
		if(cmd.equalsIgnoreCase("append")) {
			procAppend(in, lineScanner, clientName);
			
			out.close();
			in.close();
			client.close();
			
			writeData();
			writeInfo();
		}
		
		if(cmd.equalsIgnoreCase("reqvote")) {
			procReqVote(lineScanner, clientName);
			
			out.close();
			in.close();
			client.close();
		}
		
		//Send topics and svrPartMap to the backup server after the backup server being added to the streaming platfrom
		if(cmd.equalsIgnoreCase("addbkp")) {
			doSendBkp(out);
			
			out.close();
			in.close();
			client.close();
			
			writeData();
			writeInfo();
		}
		
		//Receive and store topics and svrPartMap from the reverse-backup server
		if(cmd.equalsIgnoreCase("backup")) {
			doRecvBkp(in, clientName);
			
			out.close();
			in.close();
			client.close();
			
			writeData();
			writeInfo();
		}
		
		//receive and store the partition from another server
		if(cmd.equalsIgnoreCase("move")) {
			doMvPart(in, lineScanner, out, clientName);
			
			out.close();
			in.close();
			client.close();
			
			writeData();
			writeInfo();
		}
		
		//set up the listen channel to a server
		if(cmd.equalsIgnoreCase("svrlisten")) {
			doSvrListen(lineScanner, out, outClients, outFileName, clientName);
		}
		
		if(cmd.equalsIgnoreCase("restart")) {
			doRestart(lineScanner, out);
			
			out.close();
			in.close();
			client.close();
			
			writeData();
			writeInfo();
		}
	}
	
	private void procAppend(BufferedReader in, Scanner lineScanner, String clientName) throws IOException {
		int append = 1;//by default the reply to appendRPC is TRUE
		int term = lineScanner.nextInt();//the current term of the leader
		currTerm = raftData.getCurrTerm();
		if(term < currTerm) {//reject the append request when the current term of the leader < the current term of this server
			append = 0;
		}
		else {//the current term of the leader >= the current term of this server
			currTerm = term;//update current term
			raftData.setCurrTerm(currTerm);
			if((state=raftData.getState()) == CANDIDATE) {//a CANDIDATE will stwitch back to a FOLLOWER when receiving appendRPC and the current term of the leader >= the current term of this candidate
				state = FOLLOWER;
				raftData.setState(state);//reset the state in raftData
				//new FollowerThread(localServerName, state, votes, numReplyVote, log, servers, followerStartTime).start();
				//start a new FollowerThread for monitoring heartbeat
				//new FollowerThread(localServerName, state, currTerm, votes, numReplyVote, log, servers, followerStartTime).start();
				//new FollowerThread(localServerName, servers, raftData).start();
				FollowerThread followerThread = new FollowerThread(localServerName, servers, raftData);
				followerThread.start();
				raftData.setFollowerThread(followerThread);
			}
			if((state=raftData.getState()) == FOLLOWER) {
				followerStartTime = new GregorianCalendar().getTimeInMillis();//reset the election timeout
				raftData.setFollowerStartTime(followerStartTime);//reset followerStartTime in raftData
				/*
				//try the wait method of the followerThread
				FollowerThread followerThread = raftData.getFollowerThread();
				//followerThread.wait(raftData.getTimeoutInterval()-150);
				try {
					//followerThread.wait(raftData.getTimeoutInterval()-150);
					followerThread.sleep(raftData.getTimeoutInterval()-150);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
			}
			int leaderPrevLogIndex = lineScanner.nextInt();
			int leaderPrevLogTerm = lineScanner.nextInt();
			int leaderCommit = lineScanner.nextInt();
			
			int prevLogTerm = 0;
			if(leaderPrevLogIndex >= log.size()) {//log has no entry at leaderPrevLogIndex yet
				append = 0;
			}
			else {//leaderPrevLogIndex != -1 && leaderPrevLogIndex < log.size()
				if(leaderPrevLogIndex == -1) {
					currTerm = raftData.getCurrTerm();
					prevLogTerm = currTerm;
				}
				else {
					prevLogTerm = log.get(leaderPrevLogIndex).getTerm();//leaderPrevLogIndex < log.size()
				}
				//the PrevLogIndex entry of the leader doesn't match that of this follower
				//if((leaderPrevLogIndex>=log.size())||(prevLogTerm != leaderPrevLogTerm)) {
				if(prevLogTerm != leaderPrevLogTerm) {
					append = 0;
				}
				else {//prevLogTerm == leaderPrevLogTerm
					if((state = raftData.getState()) != LEADER) {//the server is not leader itself /*!localServerName.equals(clientName)*/
						//remove all the entries after the last matched entry
						for(int i = log.size()-1; i > leaderPrevLogIndex && i < log.size(); i--) {
							log.remove(i);
						}
						//replicate the entries sent from the leader
						int numEntries = lineScanner.nextInt();
						/*//read the end of the line
						if(numEntries > 0)
							lineScanner.nextLine();*/
						//replicate the entries, one entry per line
						for(int i = 0; i < numEntries; i++) {
							String line = in.readLine();
							lineScanner = new Scanner(line);
							log.add(new LogEntry(lineScanner.nextInt(), lineScanner.next(), lineScanner.nextInt(), lineScanner.nextLine().substring(1)));
						}
						
						//write the updated log to local file
						raftData.writeLog(localServerName);
						
						if(numEntries>0)
							//print log
							raftData.printLog();
					}
				}
			}
			
			//update committedIndex after new entries replicated
			committedIndex = Math.min(leaderCommit, log.size() - 1);
			raftData.setCommittedIndex(committedIndex);
			
			//System.out.println("[procAppend] DEBUG: committedIndex: " + raftData.getCommittedIndex());
		
			//should apply the newly committed commands to the state machine
			int lastApplied = raftData.getLastApplied();
			//apply the newly committed entries to the state machine -> execute the client commands in the newly committed entries
			if(lastApplied < committedIndex) {
				String currCltName = "";
				try{
					PrintWriter outServers = null;
					PrintWriter outClients = null;
					String outFileName = "";
					
					for(int i = lastApplied+1; i <= committedIndex; i++) {
						currCltName = log.get(i).getCltName();
						Socket currSocket = clientSockets.get(currCltName);//possbible that currSocket is null?
						PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
						
						String line = log.get(i).getCmd();//restLine -> includes cmd head like "create"
						lineScanner = new Scanner(line);
						String cmd = lineScanner.next();//read cmd head
						
						exeCltCmd(cmd, lineScanner, currOut, outServers, outClients, outFileName, currCltName);
						raftData.setLastApplied(i);//reset lastApplied in raftData
						
						//write the updated log to local file
						raftData.writeLog(localServerName);
						//print log
						raftData.printLog();
					}
				} catch (IOException e) {
					System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot create the out stream
				}
			}
		}
		//String message = "replyappend"+" "+append;
		//reply the append request
		try{
			//Socket currSocket = serverSockets.get(clientName);
			//identify the replyAppend message sender server
			int i = 0;
			while(i < servers.size()) {
				if(servers.get(i).getName().equals(clientName)) {
					break;
				}
				i++;
			}
			Socket currSocket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
			PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for clientName
			currTerm = raftData.getCurrTerm();
			String message = localServerName+" "+"replyappend"+" "+currTerm+" "+append;
			if(append == 1) {
				int lastLogIndex = log.size() - 1;
				message += " " + lastLogIndex;
			}
			//System.out.println("DEBUG: "+message);
			//currOut.println(localServerName+" "+cmd);//send clientName the replyVote message
			currOut.println(message);//send clientName the replyVote message
		} catch (IOException e) {
			System.out.println("[procReplyAppend] DEBUG: " + clientName + " " + "in or out failed");
		}
	}
	
	private void procReplyAppend(Scanner lineScanner, String clientName) {
		//process the message only when the server is still the LEADER
		if((state = raftData.getState()) != LEADER) {
			System.out.println("[procReplyAppend] DEBUG: Not LEADER state now");
			return;
		}
		int term = lineScanner.nextInt();
		currTerm = raftData.getCurrTerm();
		if(term > currTerm) {
			currTerm = term;
			raftData.setCurrTerm(currTerm);
			votedFor = "";//not voted yet
			raftData.setVotedFor(votedFor);
			state = FOLLOWER;//switch to FOLLOWER state
			raftData.setState(state);//reset the state in raftData
			System.out.println("[procReplyAppend] DEBUG: Switched to FOLLOWER state");
			//new FollowerThread(localServerName, servers, raftData).start();
			FollowerThread followerThread = new FollowerThread(localServerName, servers, raftData);
			followerThread.start();
			raftData.setFollowerThread(followerThread);
			return;
		}
		//identify the replyAppend message sender server
		int i = 0;
		while(i < servers.size()) {
			if(servers.get(i).getName().equals(clientName)) {
				break;
			}
			i++;
		}
		int lastLogIndex = log.size()-1;
		int append = lineScanner.nextInt();
		if(append == 0) {
			nextIndex = raftData.getNextIndex();
			//be careful about the boundary case: nextIndex.get(i) == 0
			if(nextIndex.get(i) > 0) {
				nextIndex.set(i, nextIndex.get(i)-1);
			}
			/* //no need to de appendRPC here
			currTerm = raftData.getCurrTerm();
			String message = localServerName+" "+"append"+" "+currTerm;//the requestVote message to send to other servers
			int prevLogIndex = -1;
			if(nextIndex.get(i) > 0) {
				prevLogIndex = nextIndex.get(i)-1;//index of log entry immediately preceding new ones
			}
			
			//int prevLogIndex = matchIndex.get(i);
			int prevLogTerm = 0;
			//boundary case: need to replicate the log entry starting from the first one in the log
			if(prevLogIndex == -1) {
				prevLogTerm = raftData.getCurrTerm();
			}
			else {
				prevLogTerm = log.get(prevLogIndex).getTerm();//term of prevLogIndex entry
			}
			int numEntries = lastLogIndex - prevLogIndex;//number of log entries to be replicated to servers[i]
			if(numEntries > 0) {
				//no heartbeat AppendRPC
			}
			try {
				//create the socket and corresponding out stream to communicate with the server
				Socket currSocket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
				PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
				committedIndex = raftData.getCommittedIndex();
				String currMsg = message + " " + prevLogIndex + " " + prevLogTerm + " " + committedIndex + " " + numEntries;
				//print a line of message to the out stream
				outSvr.println(currMsg);
				
				//send entries to the follower, one line per entry
				int next = nextIndex.get(i);//the index of the first new log entry to be replicated to servers[i]
				for(int j = 0; j < numEntries; j++) {
					LogEntry entry = log.get(j+next);
					//currMsg += " "+entry.getTerm()+" "+entry.getCltName()+" "+entry.getCmdIndex()+" "+entry.getCmd();
					currMsg = entry.getTerm()+" "+entry.getCltName()+" "+entry.getCmdIndex()+" "+entry.getCmd();
					//print a log entry to the out stream
					outSvr.println(currMsg);
				}
				
				//close the socket and corresponding out stream
				outSvr.close();
				currSocket.close();
			} catch (UnknownHostException e) {
				System.err.println("[procReplyAppend] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			} catch (IOException e) {
				System.err.println("[procReplyAppend] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			}*/
			/*committedIndex = raftData.getCommittedIndex();
			 String currMsg = message + " " + prevLogIndex + " " + prevLogTerm + " " + committedIndex + " " + numEntries;
			 int next = nextIndex.get(i);//the index of the first new log entry to be replicated to servers[i]
			 for(int j = 0; j < numEntries; j++) {
				LogEntry entry = log.get(j+next);
				currMsg += " "+entry.getTerm()+" "+entry.getCltName()+" "+entry.getCmdIndex()+" "+entry.getCmd();
			 }
			 System.out.println(currMsg);
			 appendMsg(svrInfo, currMsg);*/
		}
		if(append == 1) {
			int lastRepLogIndex = lineScanner.nextInt();//read the index of the last entry replicated to the follower
																									//update matchIndex
			matchIndex = raftData.getMatchIndex();
			//if(matchIndex.get(i) < lastLogIndex) {
			if(matchIndex.get(i) < lastRepLogIndex) {//really successfully replicate at least one entry to the follower
																							 //matchIndex.set(i, lastLogIndex);
																							 //reset matchIndex[i]
				matchIndex.set(i, lastRepLogIndex);//replicated entries until the lastRepLogIndex to the follower
																					 //need to update nextIndex also
				nextIndex = raftData.getNextIndex();
				nextIndex.set(i, matchIndex.get(i)+1);//reset nextIndex[i]
				
				/*numReplicated = raftData.getNumReplicated();
				 numReplicated++;
				 raftData.setNumReplicated(numReplicated);*/
				
				//update committedIndex when successfully replicate entries to a majority of the servers && the last replicated entry's term is the current term
				//if((numReplicated > servers.size()/2) && (log.get(lastLogIndex).getTerm() == (currTerm = raftData.getCurrTerm()))) {
				//if(log.get(lastRepLogIndex).getTerm() == (currTerm = raftData.getCurrTerm())) {
				//update committedIndex
				committedIndex = raftData.getCommittedIndex();
				if((lastRepLogIndex > committedIndex) && (log.get(lastRepLogIndex).getTerm() == (currTerm = raftData.getCurrTerm()))) {
					matchIndex = raftData.getMatchIndex();
					int numRep = 0;
					for(int j = 0; j < matchIndex.size(); j++) {
						if(matchIndex.get(j)>=lastRepLogIndex) {
							numRep++;
						}
					}
					if(numRep > servers.size()/2) {
						//committedIndex = raftData.getCommittedIndex();
						//int preCommittedIndex = committedIndex;
						//committedIndex = Math.max(committedIndex, lastRepLogIndex);//lastLogIndex;/*log.size()-1;*/
						committedIndex = lastRepLogIndex;
						raftData.setCommittedIndex(committedIndex);
						//should apply the newly committed commands to the state machine
						int lastApplied = raftData.getLastApplied();
						//apply the newly committed entries to the state machine -> execute the client commands in the newly committed entries
						if(lastApplied < committedIndex) {
							String currCltName = "";
							try{
								PrintWriter outServers = null;
								PrintWriter outClients = null;
								String outFileName = "";
								
								for(int k = lastApplied+1; k <= committedIndex; k++) {
									currCltName = log.get(k).getCltName();
									Socket currSocket = clientSockets.get(currCltName);//possbible that currSocket is null?
									PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
									
									String line = log.get(k).getCmd();//restLine -> includes cmd head like "create"
									lineScanner = new Scanner(line);
									String cmd = lineScanner.next();//read cmd head
									
									exeCltCmd(cmd, lineScanner, currOut, outServers, outClients, outFileName, currCltName);
									raftData.setLastApplied(k);
									
									//write the updated log to local file
									raftData.writeLog(localServerName);
									//print log
									raftData.printLog();
								}
							} catch (IOException e) {
								System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot create the out stream
							}
						}
					}
				}
			}
		}
	}
	
	private void procReqVote(Scanner lineScanner, String clientName) {
		
		int candidateTerm = lineScanner.nextInt();
		int candLastLogIndex = lineScanner.nextInt();
		int candLastLogTerm = lineScanner.nextInt();
		int vote = 0;
		
		currTerm = raftData.getCurrTerm();
		if(candidateTerm > currTerm) {
			currTerm = candidateTerm;//update current term
			raftData.setCurrTerm(currTerm);//reset currTerm
			votedFor = "";//not voted yet
			raftData.setVotedFor(votedFor);
		}
		int lastLogIndex = log.size()-1;
		int lastLogTerm = 0;
		//boundary case: no log entry yet
		if(lastLogIndex == -1) {
			currTerm = raftData.getCurrTerm();
			lastLogTerm = currTerm;
		}
		else {
			lastLogTerm = log.get(lastLogIndex).getTerm();//get term in the last entry of the log
		}
		if((candLastLogTerm > lastLogTerm) || ((candLastLogTerm == lastLogTerm) && (candLastLogIndex >= lastLogIndex))) {
			if(votedFor.equals("")||clientName.equals(localServerName)) {//not voted for any other candidate except the server itself
				if(candLastLogTerm != -1 || lastLogIndex == -1)//candidate has at least one log entry OR itself has no log entry yet
				{
				vote = 1;//vote for the candidate
				votedFor = clientName;//voted for clientName
				raftData.setVotedFor(votedFor);
				}
			}
		}
		try{
			Socket currSocket = serverSockets.get(clientName);//reply via the listen channel
			PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for clientName
			currTerm = raftData.getCurrTerm();
			String message = "replyvote"+" "+currTerm+" "+vote;//not need to include the server's nmae in the message
			System.out.println("[procReqVote] DEBUG: "+message);
			//currOut.println(localServerName+" "+cmd);//send clientName the replyVote message
			currOut.println(message);//send clientName the replyVote message
		} catch (IOException e) {
			System.out.println("[procReqVote] DEBUG: "+clientName + " " + "in or out failed");
		}
	}
	
	private synchronized void doRemove(String clientName, Scanner lineScanner, PrintWriter out){
		while(lineScanner.hasNext()) {
			String topicName = lineScanner.next().substring(7);//skip "(topic=" at the beginning
			if(topicName.substring(topicName.length()-1).equals(")")){
				topicName = topicName.substring(0, topicName.length()-1);//skip ")" at the end
			}
			
			if(!topics.containsKey(topicName)) {//topicName not in topics, cannot remove this topicName
				
				out.print(topicName + " not exist, please change the topic name");
				out.print(" / ");
				//}
				System.out.println(topicName + " not exist, please change the topic name");
				//return;
			}
			else {
				int numPart = topics.get(topicName).size();
				
				numSub.remove(topicName);
				//numSub.put(topicName, 0);//current number of subscribers is 0
				for(int j = 0; j < subList.get(topicName).size(); j++) {
					String currCltName = subList.get(topicName).get(j);//get the name of the client who is a subscriber of current topic
					if(!clientName.equals(currCltName)) {//don't need to re-print the info for clientName again
						try{
							Socket currSocket = clientSockets.get(currCltName);//possbible that currSocket is null?
							PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
							System.out.println(topicName + " removed");
							currOut.println(topicName + " removed");//notify clientName topicName removed
						} catch (IOException e) {
							System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot create the out stream
						}
					}
				}
				subList.remove(topicName);
				//subList.put(topicName, new ArrayList<String>());
				now.remove(topicName);
				//now.put(topicName, new int[numPart]);//initialize with number of partitions specified by user input
				offset.remove(topicName);
				//offset.put(topicName, new int[numPart]);//initialize with number of partitions specified by user input
				//ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
				partSubMap.remove(topicName);
				//partSubMap.put(topicName, new String[numPart]);//put topicName into partSubMap
				partSvrMap.remove(topicName);
				//partSvrMap.put(topicName, new String[numPart]);//put topicName into partSvrMap
				for(int i = 0; i < numPart; i++) {
					//topic.add(new ArrayList<Record>());
					String str = topicName + " partition " + i;
					int svrNum = doSvrMap(str);//servers already added to the stream platform by a clent
					String svrName = servers.get(svrNum).getName();
					if(svrPartMap.get(svrName)!=null) {
						svrPartMap.get(svrName).remove(topicName);//possibly return null
					}
				}
				topics.remove(topicName);
				bkpTopics.remove(topicName);//remove topicName from the backup topics
				//topics.put(topicName, topic);
				
				out.print(topicName + " removed");
				out.print(" / ");
				//}
				System.out.println(topicName + " removed");
			}
		}
		out.println();//only println at the end
	}

	private synchronized void doCreate(Scanner lineScanner, PrintWriter out){
		while(lineScanner.hasNext()) {
			String topicName = lineScanner.next().substring(7);//skip "(topic=" at the beginning
			boolean noNumPart = false;
			if(topicName.substring(topicName.length()-1).equals(")")){
				topicName = topicName.substring(0, topicName.length()-1);//skip ")" at the end
				noNumPart = true;
			}
			
			if(topics.containsKey(topicName)) {//topicName is already in topics, cannot create this topicName
				
				out.print(topicName + " already exists, please change the topic name");
				out.print(" / ");
				//}
				System.out.println("[doCreate] DEBUG: " + topicName + " already exists, please change the topic name");
				//return;
			}
			else {
				int numPart = 0;
				
				if(!noNumPart) {//has number of partition in command line
					String str = lineScanner.next().substring(11);//skip the 'partitions=' at the beginning
					str = str.substring(0, str.length()-1);//skip ")" at the end
					numPart = Integer.parseInt(str);
				}
				else {
					numPart = 1;//only 1 partition
				}
				
				numSub.put(topicName, 0);//current number of subscribers is 0
				subList.put(topicName, new ArrayList<String>());
				now.put(topicName, new int[numPart]);//initialize with number of partitions specified by user input
				offset.put(topicName, new int[numPart]);//initialize with number of partitions specified by user input
				ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
				ArrayList<ArrayList<Record>> bkpTopic = new ArrayList<ArrayList<Record>>();
				partSubMap.put(topicName, new String[numPart]);//put topicName into partSubMap
				partSvrMap.put(topicName, new String[numPart]);//put topicName into partSvrMap
				for(int i = 0; i < numPart; i++) {
					topic.add(new ArrayList<Record>());
					bkpTopic.add(new ArrayList<Record>());
					String str = topicName + " partition " + i;//create the string by combining the topicName and partition number
					//map the partition to a server, hashCode is the index of that server in servers
					int svrNum = doSvrMap(str);//servers already added to the stream platform by a clent
					partSvrMap.get(topicName)[i] = servers.get(svrNum).getName();
					System.out.println("[doCreate] DEBUG: partition " + i + " of " + topicName + " is located at " + partSvrMap.get(topicName)[i]);
					if(svrPartMap.get(servers.get(svrNum).getName()) == null) {//this server is not in svrPartMap yet
						svrPartMap.put(servers.get(svrNum).getName(), new HashMap<String, HashSet<Integer>>());
					}
					if(svrPartMap.get(servers.get(svrNum).getName()).get(topicName) == null) {//topicName is not associated with this server yet
						svrPartMap.get(servers.get(svrNum).getName()).put(topicName, new HashSet<Integer>());
					}
					svrPartMap.get(servers.get(svrNum).getName()).get(topicName).add(i);//associate partition i of topicName with this server
					now.get(topicName)[i] = 0;//current size of partition i of topicName is 0 because no record published to this partition yet
					offset.get(topicName)[i] = 0;//current offset of partition i of topicName is 0 because there is no subscriber of this partition yet
				}
				topics.put(topicName, topic);
				bkpTopics.put(topicName, bkpTopic);//backup topics also needs to be updated with the new topic
				
				out.print(topicName + " with " + numPart + " partitions created");
				out.print(" / ");
				//}
				System.out.println("[doCreate] DEBUG: " + topicName + " with " + numPart + " partitions created");
			}
		}
		out.println();//only println at the end
	}
	
	private synchronized void doPublish(Scanner lineScanner, PrintWriter out) {
		//String topicName = lineScanner.next();
		String topicName = lineScanner.next().substring(7);//skip "(topic=" at the begining
		ArrayList<ArrayList<Record>> currTopic = topics.get(topicName);//get topicName from topics, return null if topicName not in topics
		ArrayList<ArrayList<Record>> currBkpTopic = bkpTopics.get(topicName);//get topicName from backup topics, return null if topicName not in topics
		if(currTopic == null) { //no topicName in the topics
			out.println(topicName + " doesn't exist");
			return;//exit the method
		}
		int partNum = 0;
		boolean noPartNum = true;
		String str = lineScanner.next();
		if(!str.substring(0, 3).equals("key")){//the second argument not begins with "key=", so has partition number
			str = str.substring(10);//skip the 'partition=' at the beginning
			partNum = Integer.parseInt(str);
			if(partNum >= topics.get(topicName).size()) {
				System.out.println("DEBUG: partition index [" + partNum + "] should be less than the number of partitions of " + topicName);
				out.println("partition index [" + partNum + "] should be less than the number of partitions of " + topicName);
				return;
			}
			noPartNum = false;
			str = lineScanner.next();//continue to read the key
		}
		String key = str.substring(4);//skip "key=" at the beginning
		str = lineScanner.next().substring(6);//skip "value=" at the beginning
		str = str.substring(0, str.length()-1);//ship ")" at the end
		int value = Integer.parseInt(str);
		if(noPartNum) {
			System.out.print("DEBUG: put (" + key + ", " + value + ") to " + topicName + " on");
			out.print("put (" + key + ", " + value + ") to " + topicName + " on");
			for(int i = 0; i < currTopic.size(); i++) {//iterate for each partition of the topic
				//check the local server name
				//is the server where the partition should locate at
				String mapSvrName = partSvrMap.get(topicName)[i];
				String backupSvrName = backupSvr(partSvrMap.get(topicName)[i]);
				if(localServerName.equals(mapSvrName)) {
					
					currTopic.get(i).add(new Record(key, value));//add the new record to the partitions of topicName
					now.get(topicName)[i] = currTopic.get(i).size();//update now with the size of current partition of topicName
				}
				//is the backup server but NOT the mapped server (NOT only one server in the stream platform)
				if(localServerName.equals(backupSvrName)&&(!mapSvrName.equals(backupSvrName))) {//backupSvr is the helper method to retrieve the name of the backup server
					
					//currTopic.get(i).add(new Record(key, value));//add the new record to the partitions of topicName
					currBkpTopic.get(i).add(new Record(key, value));//add the new record to the partitions of topicName on backup topics
					now.get(topicName)[i] = currBkpTopic.get(i).size();//update now with the size of current partition of topicName on backup topics
				}
				System.out.print(" partition " + i + " on " + partSvrMap.get(topicName)[i]);
				System.out.print(" / ");
				out.print(" partition " + i + " on " + partSvrMap.get(topicName)[i]);
				out.print(" / ");
			}
			System.out.println();
			out.println();
		}
		else {
			String mapSvrName = partSvrMap.get(topicName)[partNum];
			String backupSvrName = backupSvr(partSvrMap.get(topicName)[partNum]);
			if(localServerName.equals(mapSvrName)) {
				
				currTopic.get(partNum).add(new Record(key, value));//add the new record to the partitions of topicName
				now.get(topicName)[partNum] = currTopic.get(partNum).size();//update now with the size of current partition of topicName
			}
			if(localServerName.equals(backupSvrName)&&(!mapSvrName.equals(backupSvrName))) {//backupSvr is the helper method to retrieve the name of the backup server
				
				//currTopic.get(partNum).add(new Record(key, value));//add the new record to the partitions of topicName
				currBkpTopic.get(partNum).add(new Record(key, value));//add the new record to the partitions of topicName on backup topics
				now.get(topicName)[partNum] = currBkpTopic.get(partNum).size();//update now with the size of current partition of topicName on backup topics
			}
			out.println("put (" + key + ", " + value + ") to " + topicName + " and partition " + partNum + " on " + partSvrMap.get(topicName)[partNum]);
			
			//}
			System.out.println("put (" + key + ", " + value + ") to " + topicName + " and partition " + partNum + " on " + partSvrMap.get(topicName)[partNum]);
		}
	}
	
	private synchronized void printTopics() {
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> entry: topics.entrySet()) {
			System.out.println("current topic: " + entry.getKey());
			for(int i = 0; i < entry.getValue().size(); i++) {
				System.out.println("partition " + i + ":");
				for(int j = 0; j < entry.getValue().get(i).size(); j++) {
					System.out.println(entry.getValue().get(i).get(j).getKey() + " " + entry.getValue().get(i).get(j).getValue());
				}
			}
		}
	}
	
	private synchronized void writeData() {
		PrintWriter outTopics = null;
		String outFileName = null;
		try {
			//String localDir = "/tmp/92476/stream";
			//String localDir = "/Users/gladet/csc502/stream";
			String localDir = "../stream";
			outFileName = localDir+"/"+localServerName+"_data";
			
			outTopics = new PrintWriter(outFileName);
		}catch (FileNotFoundException exception) {
			System.out.println("ERROR: output file [" + outFileName + "] does not exist");
		}
		
		//write topics
		outTopics.println(topics.size());//number of topics
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {
			outTopics.println(" " + e.getKey());//current topic name
			outTopics.println(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {//iterate over parts
				outTopics.println(e.getValue().get(i).size());//number of records of the current part
				for(int j = 0; j < e.getValue().get(i).size(); j++) {//iterate over records
					outTopics.println(e.getValue().get(i).get(j).getKey() + " " + e.getValue().get(i).get(j).getValue());
				}
			}
		}
		outTopics.println();
		//write bkpTopics
		outTopics.println(bkpTopics.size());//number of bkpTopics
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: bkpTopics.entrySet()) {
			outTopics.println(" " + e.getKey());//current topic name
			outTopics.println(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {//iterate over parts
				outTopics.println(e.getValue().get(i).size());//number of records of the current part
				for(int j = 0; j < e.getValue().get(i).size(); j++) {//iterate over records
					outTopics.println(e.getValue().get(i).get(j).getKey() + " " + e.getValue().get(i).get(j).getValue());
				}
			}
		}
		
		outTopics.close();//close the out stream when no more info to write to the local file
		
		//chmod 777
		try {
			File file = new File(outFileName);
			Runtime.getRuntime().exec("chmod 777 " + outFileName);
		} catch(IOException e) {
			e.printStackTrace();
		}
		//***
		
	}
	
	private synchronized void writeInfo() {
		PrintWriter outInfo = null;
		String outFileName = null;
		try {
			//String localDir = "/tmp/92476/stream";
			//String localDir = "/Users/gladet/csc502/stream";
			String localDir = "../stream";
			outFileName = localDir+"/"+localServerName+"_info";
			
			outInfo = new PrintWriter(outFileName);
		}catch (FileNotFoundException exception) {
			System.out.println("ERROR: output file [" + outFileName + "] does not exist");
		}
		
		//wirte [now] to the info file
		for(Map.Entry<String, int[]> e: now.entrySet()) {
			outInfo.println(" " + e.getKey());//current topic name
			outInfo.println(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outInfo.println(" " + e.getValue()[i]);
			}
		}
		
		//wirte [offset] to the info file
		for(Map.Entry<String, int[]> e: offset.entrySet()) {
			outInfo.println(" " + e.getKey());
			outInfo.println(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outInfo.println(" " + e.getValue()[i]);
			}
		}
		
		//wirte [number of subscribers] to the info file
		for(Map.Entry<String, Integer> e: numSub.entrySet()) {
			outInfo.println(" " + e.getKey());
			outInfo.println(" " + e.getValue());
		}
		
		//wirte [list of subscribers] to the info file
		for(Map.Entry<String, ArrayList<String>> e: subList.entrySet()) {
			outInfo.println(" " + e.getKey());
			outInfo.println(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {
				outInfo.println(" " + e.getValue().get(i));
			}
		}
		
		//wirte [partition-subscriber mapping] to the info file
		for(Map.Entry<String, String[]> e: partSubMap.entrySet()) {
			outInfo.println(" " + e.getKey());
			outInfo.println(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outInfo.println(" " + e.getValue()[i]);
			}
		}
		
		//wirte [partition-server mapping] to the info file
		for(Map.Entry<String, String[]> e: partSvrMap.entrySet()) {
			outInfo.println(" " + e.getKey());
			outInfo.println(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outInfo.println(" " + e.getValue()[i]);
			}
		}
		
		//wirte [partitions associated with the server] to the info file
		outInfo.println(" " + svrPartMap.size());//number of servers
		for(Map.Entry<String, HashMap<String, HashSet<Integer>>> e: svrPartMap.entrySet()) {
			outInfo.println(" " + e.getKey());//server name
			outInfo.println(" " + e.getValue().size());//number of topics
			for(Map.Entry<String, HashSet<Integer>> entry: e.getValue().entrySet()) {
				outInfo.println(" " + entry.getKey());//topic name
				outInfo.println(" " + entry.getValue().size());//number of partitions
				for(Integer i: entry.getValue()) {
					outInfo.println(" " + i);//index of the partition
				}
			}
		}
		
		outInfo.close();//close the out stream when no more info to write to the local file
		
		//chmod 777
		try {
			File file = new File(outFileName);
			Runtime.getRuntime().exec("chmod 777 " + outFileName);
		} catch(IOException e) {
			e.printStackTrace();
		}
		//***
		
	}

	private void doGet(String clientName, Scanner lineScanner, PrintWriter out) {
		String topicName = lineScanner.next().substring(7);//skip "(topic=" at the beginning
		if(!lineScanner.hasNext()) {
			System.out.println("missing the partition number");
			out.println("missing the partition number");
			return;
		}
		String str = lineScanner.next();
		str = str.substring(10);//skip the 'partition=' at the beginning
		str = str.substring(0, str.length()-1);//ship ")" at the end
		int partNum = Integer.parseInt(str);
		if(topics.get(topicName)==null) {
			System.out.println("DEBUG: " + topicName + " not exist");
			out.println(topicName + " not exist");
			return;
		}
		if(partNum >= topics.get(topicName).size()) {
			System.out.println("DEBUG: partition index [" + partNum + "] should be less than the number of partitions of " + topicName);
			out.println("partition index [" + partNum + "] should be less than the number of partitions of " + topicName);
			return;
		}
		if(partSubMap.get(topicName) == null) {
			System.out.println("DEBUG: " + topicName + " not created yet");
			out.println(topicName + " not created yet");
			return;
		}
		if(!clientName.equals(partSubMap.get(topicName)[partNum])) {//no error handling for topicName not yet added in partSubMap
			System.out.println("DEBUG: " + clientName + " doesn't subscribe partition " + partNum + " of " + topicName);
			out.println("DEBUG: " + clientName + " doesn't subscribe partition " + partNum + " of " + topicName);
		}
		else {//clientName subscribed this partition of topicName
			String mapSvrName = partSvrMap.get(topicName)[partNum];
			String backupSvrName = backupSvr(mapSvrName);
			if(localServerName.equals(mapSvrName)||localServerName.equals(backupSvrName)) {//this partition is located at local server -> either mapped server or backup server
				System.out.println("DEBUG: partition " + partNum + " of " + topicName + " is located at " + localServerName);
				if(topics.get(topicName).get(partNum).size() == 0 && bkpTopics.get(topicName).get(partNum).size() == 0) {//no error handling if topicName is not in topics -> this partition of topicName has no record yet neither on topics nor backup topics
					System.out.println("DEBUG: partition " + partNum + " of " + topicName + " has no record yet");
					out.println("partition " + partNum + " of " + topicName + " has no record yet");
				}
				else {
					int currOffset = offset.get(topicName)[partNum];// get the current offset of this partition
					int currNow = now.get(topicName)[partNum];// get the current now of this partition
					if(currOffset >= currNow) {
						System.out.println("DEBUG: current offset out of boundary, reset to 0");
						currOffset %= currNow;
					}
					//check the server is the mapped one or backup server
					String svrName = "";
					if(localServerName.equals(mapSvrName)) {//local server is the mapped server
						svrName = mapSvrName;
						System.out.println("DEBUG: get (" + topics.get(topicName).get(partNum).get(currOffset).getKey() + ", " + topics.get(topicName).get(partNum).get(currOffset).getValue() + ") from " + topicName + " and partition " + partNum + " on " + svrName);
						out.println("get (" + topics.get(topicName).get(partNum).get(currOffset).getKey() + ", " + topics.get(topicName).get(partNum).get(currOffset).getValue() + ") from " + topicName + " and partition " + partNum + " on " + svrName);
					}
					else {//local server is the backup server
						svrName = backupSvrName;
						System.out.println("DEBUG: get (" + bkpTopics.get(topicName).get(partNum).get(currOffset).getKey() + ", " + bkpTopics.get(topicName).get(partNum).get(currOffset).getValue() + ") from " + topicName + " and partition " + partNum + " on " + svrName);
						out.println("get (" + bkpTopics.get(topicName).get(partNum).get(currOffset).getKey() + ", " + bkpTopics.get(topicName).get(partNum).get(currOffset).getValue() + ") from " + topicName + " and partition " + partNum + " on " + svrName);
					}
					offset.get(topicName)[partNum] = (currOffset+1);//update offset of the current accessed partition of topicName
				}
			}
			else {
				System.out.println("DEBUG: partition " + partNum + " of " + topicName + " is NOT located at " + localServerName);
				out.println("partition " + partNum + " of " + topicName + " is NOT located at " + localServerName);
			}
		}
	}
	
	private void doSubscribe(String clientName, Scanner lineScanner, PrintWriter out) {
		while(lineScanner.hasNext()) {
			String topicName = lineScanner.next().substring(7);//skip "(topic=" at the begining
			topicName = topicName.substring(0, topicName.length()-1);//skip ")" at the end
			if(subList.get(topicName) == null) {//topicName not created yet
				System.out.println("DEBUG: " + topicName + " not created yet");
				out.print(topicName + " not created yet");
				out.print(" / ");
				//return;
			}
			else {
				//check if clientName already subscribed topicName
				if(subList.get(topicName).contains(clientName)) {
					System.out.println("DEBUG: " + clientName + " already subscribed " + topicName);
					out.print(clientName + " already subscribed " + topicName);
					out.print(" / ");
				}
				else {
					int numTopicSub = numSub.get(topicName);//record the current number of subscribers of topicName
					int numTopicPart = topics.get(topicName).size();//record the number of partitions of topicName
					if(numTopicSub == numTopicPart) {//number of subscribers alreaday equals to number of partitions
						System.out.println("DEBUG: number of subscribers alreaday equals to number of partitions, cannot subscribe " + topicName);
						out.print("number of subscribers alreaday equals to number of partitions, cannot subscribe " + topicName);
						out.print(" / ");
					}
					else {//number of subscribers less than number of partitions
						numSub.put(topicName, numTopicSub+1);//increase the number of subscribers by 1 and replace the older value
						//numSub.get(topicName)++;//no sure if it works
						if(subList.get(topicName) == null) {//topicName not in subList yet
							subList.put(topicName, new ArrayList<String>());
						}
						subList.get(topicName).add(clientName);//add the current clientName to the list of subscribers
																									 //re-allocate the partitions to subscribers
						if(partSubMap.get(topicName) == null) {//topicName not in partSubMap yet
							partSubMap.put(topicName, new String[numTopicPart]);
						}
						int i = 0;//record the current index in partSubMap.get(topicName)
						while(i < numTopicPart) {
							for(int j = 0; j < numSub.get(topicName) && i < numTopicPart; j++) {
								partSubMap.get(topicName)[i] = subList.get(topicName).get(j);
								System.out.println("partition " + i + " of " + topicName + " is subscribed by " + subList.get(topicName).get(j));
								i++;
							}
						}
						System.out.print(clientName + " subscribed " + topicName + " and can get partiton");//print out the partition number of topicName subscribed by clientName
						out.print(clientName + " subscribed " + topicName + " and can get partiton");//send clientName the partition number of topicName subscribed by clientName
						for(i = 0; i < numTopicPart; i++) {
							if(clientName.equals(partSubMap.get(topicName)[i])) {
								System.out.print(" ");
								System.out.print(i);
								out.print(" ");
								out.print(i);
							}
						}
						out.print(" / ");
						System.out.println();
						//out.println();
						for(int j = 0; j < subList.get(topicName).size(); j++) {
							String currCltName = subList.get(topicName).get(j);//get the name of the client who is a subscriber of current topic
							if(!clientName.equals(currCltName)) {//don't need to re-print the info for clientName again
								try{
									Socket currSocket = clientSockets.get(currCltName);//possbible that currSocket is null?
									PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
									System.out.print(currCltName + " subscribed " + topicName + " and can get partiton");//print out the partition number of topicName subscribed by clientName
									currOut.print(currCltName + " subscribed " + topicName + " and can get partiton");//send clientName the partition number of topicName subscribed by clientName
									for(i = 0; i < numTopicPart; i++) {
										if(currCltName.equals(partSubMap.get(topicName)[i])) {
											System.out.print(" ");
											System.out.print(i);
											//send the message to the currCltName
											currOut.print(" ");
											currOut.print(i);
										}
									}
									//currOut.print(" / ");
									currOut.println();//remember to generate new line //don't close currOut here
									System.out.println();
								} catch (IOException e) {
									System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot create the out stream
									//System.exit(-1);
									//return;
								}
							}
						}
					}
				}
			}
			
		}
		out.println();//only println at the end
	}
	
	private void doUnsubscribe(String clientName, Scanner lineScanner, PrintWriter out) {
		while(lineScanner.hasNext()) {
			String topicName = lineScanner.next().substring(7);//skip "(topic=" at the begining
			topicName = topicName.substring(0, topicName.length()-1);//skip ")" at the end
			if(subList.get(topicName) == null) {//topicName not created yet
				System.out.println("DEBUG: " + topicName + " not created yet");
				out.print(topicName + " not created yet");
				out.print(" / ");
				//return;
			}
			else {
				//check if clientName already subscribed topicName
				if(!subList.get(topicName).contains(clientName)) {
					System.out.println("DEBUG: " + clientName + " not subscribe " + topicName);
					out.print(clientName + " not subscribe " + topicName);
					out.print(" / ");
				}
				else {
					int numTopicSub = numSub.get(topicName);//record the current number of subscribers of topicName
					int numTopicPart = topics.get(topicName).size();//record the number of partitions of topicName
					numSub.put(topicName, numTopicSub-1);//decrease the number of subscribers by 1 and replace the older value
					if(subList.get(topicName) == null) {//topicName not in subList yet
						subList.put(topicName, new ArrayList<String>());
					}
					for(int i = 0; i < subList.get(topicName).size(); i++) {
						if(clientName.equals(subList.get(topicName).get(i))) {
							subList.get(topicName).remove(i);//remove clientName from subList.get(topicName)
							break;
						}
					}
					//subList.get(topicName).add(clientName);//add the current clientName to the list of subscribers
					//re-allocate the partitions to subscribers
					if(partSubMap.get(topicName) == null) {//topicName not in partSubMap yet
						partSubMap.put(topicName, new String[numTopicPart]);
					}
					int i = 0;//record the current index in partSubMap.get(topicName)
					while(i < numTopicPart) {
						for(int j = 0; j < numSub.get(topicName) && i < numTopicPart; j++) {
							partSubMap.get(topicName)[i] = subList.get(topicName).get(j);
							System.out.println("partition " + i + " of " + topicName + " is subscribed by " + subList.get(topicName).get(j));
							i++;
						}
					}
					System.out.print(clientName + " unsubscribed " + topicName);
					out.print(clientName + " unsubscribed " + topicName);
					out.print(" / ");
					System.out.println();
					//out.println();
					//notify other clients subscribed topicName about the change
					for(int j = 0; j < subList.get(topicName).size(); j++) {
						String currCltName = subList.get(topicName).get(j);//get the name of the client who is a subscriber of current topic
						if(!clientName.equals(currCltName)) {//don't need to re-print the info for clientName again
							try{
								Socket currSocket = clientSockets.get(currCltName);//possbible that currSocket is null?
								PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
								System.out.print(currCltName + " subscribed " + topicName + " and can get partiton");//print out the partition number of topicName subscribed by clientName
								currOut.print(currCltName + " subscribed " + topicName + " and can get partiton");//send clientName the partition number of topicName subscribed by clientName
								for(i = 0; i < numTopicPart; i++) {
									if(currCltName.equals(partSubMap.get(topicName)[i])) {
										System.out.print(" ");
										System.out.print(i);
										//send the message to the currCltName
										currOut.print(" ");
										currOut.print(i);
									}
								}
								//currOut.print(" / ");
								currOut.println();//remember to generate new line //don't close currOut here
								System.out.println();
							} catch (IOException e) {
								System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot create the out stream
							}
						}
					}
					if(numTopicSub == numTopicPart) {//number of subscribers alreaday equals to number of partitions
					}
					else {//number of subscribers less than number of partitions
					}
				}
			}
			
		}
		out.println();//only println at the end
	}
	
	private void doListen(Scanner lineScanner, PrintWriter out, PrintWriter outClients, String outFileName, String clientName) {
		//System.out.println("The address of the endpoint the client is bound to " + client.getLocalSocketAddress());
		//get and store the client info, no need to reply to the client in this case
		try {
			clientName = lineScanner.next();
			String clientIP = lineScanner.next();
			int clientPort = lineScanner.nextInt();
			clients.add(new ClientInfo(clientName, clientIP, clientPort));
			clientSockets.put(clientName, client);
			out.println(localServerName + " will send message when necessary");
			System.out.println("[doListen] DEBUG: connected with " + clientName + " " + clientIP + " " + clientPort);
			
			//initialize the clientsInfo file reader
			try {
				//String localDir = "/tmp/92476/stream";
				//String localDir = "/Users/gladet/csc502/stream";
				String localDir = "../stream";
				
				outFileName = localDir + "/clientsInfo";
				
				outClients = new PrintWriter(outFileName);
			}catch (FileNotFoundException exception) {
				System.out.println("ERROR: output file [" + outFileName + "] does not exist");
			}
			
			outClients.println(clients.size());//write the number of clients into local file
			for(int i = 0; i < clients.size(); i++) {//2 clients
				outClients.println(clients.get(i).getName() + " " + clients.get(i).getIP() + " " + clients.get(i).getPort());//write the clients' info to the local file
			}
			outClients.close();//close the out stream when no more info to write to the local file
			
			//chmod 777
			try {
				File file = new File(outFileName);
				Runtime.getRuntime().exec("chmod 777 " + outFileName);
			} catch(IOException e) {
				e.printStackTrace();
			}
			//***
		}/*catch (IOException e) {
			System.out.println("Read failed");
			//System.exit(-1);
			return;
			}*/catch(NullPointerException e) {
				System.out.println(clientName + "disconnected");
				//System.exit(-1);
				return;
			}
	}
	
	private void doSvrListen(Scanner lineScanner, PrintWriter out, PrintWriter outClients, String outFileName, String serverName) {
		//System.out.println("The address of the endpoint the client is bound to " + client.getLocalSocketAddress());
		//get and store the client info, no need to reply to the client in this case
		try {
			serverName = lineScanner.next();
			String clientIP = lineScanner.next();
			int clientPort = lineScanner.nextInt();
			//clients.add(new ClientInfo(serverName, clientIP, clientPort));
			serverSockets.put(serverName, client);//add the listen socket to the list for later use
			out.println(localServerName + " will send message when necessary");
			System.out.println("[doSvrListen] DEBUG: connected with " + serverName + " " + clientIP + " " + clientPort);
			
		}	catch(NullPointerException e) {
				System.out.println(serverName + " disconnected");
				//System.exit(-1);
				//return;
			}
	}
	
	//the restarted/newly added server is the backup server -> send the topics and svrPartMap to the restarted/newly added server for backup
	private void doSendBkp(PrintWriter out) {
		//send the topics
		out.print(topics.size());//number of topics
		//System.out.println(topics.size());
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {
			out.print(" " + e.getKey());//current topic name
			out.print(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {//iterate over parts
				out.print(" " + e.getValue().get(i).size());//number of records of the current part
				for(int j = 0; j < e.getValue().get(i).size(); j++) {//iterate over records
					out.print(" " + e.getValue().get(i).get(j).getKey() + " " + e.getValue().get(i).get(j).getValue());
				}
			}
		}
		//send the info of parts located on local server
		if(svrPartMap.get(localServerName) == null) {
			svrPartMap.put(localServerName, new HashMap<String, HashSet<Integer>>());
		}
		HashMap<String, HashSet<Integer>> svrParts = svrPartMap.get(localServerName);//the partitions currently stored on localServerName
		out.print(" " + localServerName);//local server name
		out.print(" " + svrParts.size());//number of topics
		for(Map.Entry<String, HashSet<Integer>> entry: svrParts.entrySet()) {//iterate over topics
			out.print(" " + entry.getKey());//topic name
			out.print(" " + entry.getValue().size());//number of partitions
			for(Integer i: entry.getValue()) {//iterate ove parts
				out.print(" " + i);//index of the partition
			}
		}
		out.println();
	}
	
	//is the backup server of the restarted server, send data/info to the restarted server for recovery
	private void doRestartUpdate(PrintWriter out) {
		//send the backup topics to the restarted server
		out.print(bkpTopics.size());//number of bkpTopics
		System.out.print(bkpTopics.size());
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: bkpTopics.entrySet()) {
			out.print(" " + e.getKey());//current topic name
			out.print(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {//iterate over parts
				out.print(" " + e.getValue().get(i).size());//number of records of the current part
				for(int j = 0; j < e.getValue().get(i).size(); j++) {//iterate over records
					out.print(" " + e.getValue().get(i).get(j).getKey() + " " + e.getValue().get(i).get(j).getValue());
				}
			}
		}
		
		//wirte [now] to the info file
		for(Map.Entry<String, int[]> e: now.entrySet()) {
			out.print(" " + e.getKey());//current topic name
			out.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				out.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [offset] to the info file
		for(Map.Entry<String, int[]> e: offset.entrySet()) {
			out.print(" " + e.getKey());
			out.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				out.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [number of subscribers] to the info file
		for(Map.Entry<String, Integer> e: numSub.entrySet()) {
			out.print(" " + e.getKey());
			out.print(" " + e.getValue());
		}
		
		//wirte [list of subscribers] to the info file
		for(Map.Entry<String, ArrayList<String>> e: subList.entrySet()) {
			out.print(" " + e.getKey());
			out.print(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {
				out.print(" " + e.getValue().get(i));
			}
		}
		
		//wirte [partition-subscriber mapping] to the info file
		for(Map.Entry<String, String[]> e: partSubMap.entrySet()) {
			out.print(" " + e.getKey());
			out.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				out.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [partition-server mapping] to the info file
		for(Map.Entry<String, String[]> e: partSvrMap.entrySet()) {
			out.print(" " + e.getKey());
			out.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				out.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [partitions associated with the server] to the info file
		out.print(" " + svrPartMap.size());//number of servers
		System.out.print(" " + svrPartMap.size());//number of servers
		for(Map.Entry<String, HashMap<String, HashSet<Integer>>> e: svrPartMap.entrySet()) {
			out.print(" " + e.getKey());//server name
			out.print(" " + e.getValue().size());//number of topics
			for(Map.Entry<String, HashSet<Integer>> entry: e.getValue().entrySet()) {
				out.print(" " + entry.getKey());//topic name
				out.print(" " + entry.getValue().size());//number of partitions
				for(Integer i: entry.getValue()) {
					out.print(" " + i);//index of the partition
				}
			}
		}
		
		out.println();
		System.out.println();
	}
	
	//get the restart message from a restarted server
	private void doRestart(Scanner lineScanner, PrintWriter out) {
		String serverName = lineScanner.next();
		//String serverIP = lineScanner.next();
		int serverPort = lineScanner.nextInt();
		//int i = lineScanner.nextInt();
		int index = lineScanner.nextInt();
		for(int i = 0; i < servers.size(); i++) {
			if(serverName.equals(servers.get(i).getName())&&(serverPort!=servers.get(i).getPort())) {
				//servers.get(i) = new serversInfo(serverName, servers.get(i).getIP(), serverPort);
				servers.get(i).setPort(serverPort);
				
				//write the restarted server's new port number to serversInfo
				String outFileName = null;
				PrintWriter outServers = null;
				
				//initialize the serversInfo file reader
				try {
					//localDir = "/tmp/92476/stream";
					//localDir = "/Users/gladet/csc502/stream";
					String localDir = "../stream";
					outFileName = localDir+"/serversInfo";
					
					//outFileName = "serversInfo"; //the local file to store the servers info, using relative path
					outServers = new PrintWriter(outFileName);
				}catch (FileNotFoundException exception) {
					System.out.println("[server main] DEBUG: ERROR: output file [" + outFileName + "] does not exist");
				}
				outServers.println(servers.size());//write the number of servers into local file
				for(int j = 0; j < servers.size(); j++) {//2 servers
					outServers.println(servers.get(j).getName() + " " + servers.get(j).getIP() + " " + servers.get(j).getPort());//write the servers' info to the local file
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
				
				//listen to the restarted server again
				new ListenThread(localServerName, servers, servers.get(i), raftData).start();
				out.println(localServerName + " got restart message from " + serverName);//ack message
				
				String backupSvrName = backupSvr(serverName);
				//localServerName is the backup server of the restarted server, send data/info to the restarted server for recovery
				if(localServerName.equals(backupSvrName)) {
					//doRestartUpdate(out);//update the restarted server with the data and info of the stream platform
					
					//boardcast to all the clients the restarted server's info
					for(Map.Entry<String, Socket> entry: clientSockets.entrySet()) {
						String currCltName = entry.getKey();
						System.out.println("current client: " + currCltName);
						Socket currSocket = entry.getValue();
						try{
							PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
							System.out.println("DEBUG: restart" + " " + serverName + " " + serverPort + " " + i);//print out the partition number of topicName subscribed by clientName
							currOut.println("restart" + " " + serverName + " " + serverPort + " " + i);//send clientName the partition number of topicName subscribed by clientName
						} catch (IOException e) {
							System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot
						}
					}
				}
				String rvBkpSvr = backupSvr(localServerName);
				if(serverName.equals(rvBkpSvr)) {
					//restarted server is the backup server of localServerName -> send topics and svrPartMap to restarted server for backup
					//doSendBkp(out);
				}
				//out.println(localServerName + " got restart message from " + serverName);//ack message
				break;
			}
		}
		
	}
	
	private void doAdd(Scanner lineScanner, PrintWriter out, PrintWriter outServers, String outFileName, String clientName) {
		//servers = null;
		String addCmd = "add ";
		boolean newAdd = false;//indicate this server is newly added to the streaming platform or not
													 //no need to reply to the client in this case
		while(lineScanner.hasNext()) {
			String serverName = lineScanner.next().substring(6);//ignore the '(name='
			//check if the server itself is newly added to the streaming platform
			if(localServerName.equals(serverName)) {
				newAdd = true;
			}
			
			String serverIP = lineScanner.next().substring(3);//ignore the 'ip='
			String portStr = lineScanner.next().substring(5);//ignore the 'port='
			int serverPort = Integer.parseInt(portStr.substring(0, portStr.length()-1));//ignore the ')'
			ServerInfo svrInfo = new ServerInfo(serverName, serverIP, serverPort);
			servers.add(svrInfo);//add the ServerInfo into the servers
			
			//if is the LEADER, update nextIndex and matchIndex
			if((state = raftData.getState()) == LEADER) {
				int lastLogIndex = log.size()-1;
				
				nextIndex = raftData.getNextIndex();
				nextIndex.add(lastLogIndex+1);
				matchIndex = raftData.getMatchIndex();
				matchIndex.add(-1);
			}
			
			//start the ListenThread corresponding to a server
			//new ListenThread(localServerName, state, currTerm, votes, numReplyVote, numReplicated, log, nextIndex, matchIndex, committedIndex, servers, svrInfo).start();
			new ListenThread(localServerName, servers, svrInfo, raftData).start();
			System.out.println("[doAdd] DEBUG: " + serverName + " " + serverIP + " " + serverPort);
			
			addCmd += "(name=" + serverName + " " + "ip=" + serverIP + " " + "port=" + serverPort + ") ";
		}
		
		//start the FollowerThread -> only when the server is newly added
		//new FollowerThread(localServerName, svrData.getState(), svrData.getCurrTerm(), svrData.getVotes(), svrData.getNumReplyVote(), svrData.getLog(), svrData.getServers(), svrData.getFollowerStartTime()).start();
		//new FollowerThread(localServerName, state, currTerm, votes, numReplyVote, log, servers, followerStartTime).start();
		if(newAdd) {
			//new FollowerThread(localServerName, servers, raftData).start();
			FollowerThread followerThread = new FollowerThread(localServerName, servers, raftData);
			followerThread.start();
			raftData.setFollowerThread(followerThread);
		}
		//new FollowerThread(localServerName, servers, raftData).start();
		
		//initialize the serversInfo file reader
		try {
			//String localDir = "/tmp/92476/stream";
			//String localDir = "/Users/gladet/csc502/stream";
			String localDir = "../stream";
			outFileName = localDir+"/serversInfo";
			
			//outFileName = "serversInfo"; //the local file to store the servers info, using relative path
			outServers = new PrintWriter(outFileName);
		}catch (FileNotFoundException exception) {
			System.out.println("[doAdd] ERROR: output file [" + outFileName + "] does not exist");
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
		
		out.println(localServerName + " added to the stream platform");
		
		if(localServerName.equals(servers.get(0).getName())&&(!newAdd)) {//only the first server in servers forward the add command to the clients, make sure this server is not newly added to the stream platform
			for(Map.Entry<String, Socket> entry: clientSockets.entrySet()) {
				String currCltName = entry.getKey();
				System.out.println("[doAdd] DEBUG: current client: " + currCltName);
				if(!currCltName.equals(clientName)) {//don't need to forward the add command to the original message sender client
					Socket currSocket = entry.getValue();
					try{
						PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
						System.out.println("[doAdd] DEBUG: " + addCmd);//print out the partition number of topicName subscribed by clientName
						currOut.println(addCmd);//send clientName the partition number of topicName subscribed by clientName
					} catch (IOException e) {
						System.out.println("[doAdd] DEBUG: " + currCltName + " " + "in or out failed");//print currCltName in the case cannot
					}
				}
			}
		}
		
		//redistribute the partitions
		//doPartReloc();
		
		/* //NOT backup the reverse-backup server after deploying consensus mdoule
		if(newAdd&&(servers.size()>1)) {
			// check if localServerName is the backup server of servers.get(i)->if YES->ask servers.get(i) to send topics and svrPartMap for backup
			for(int i = 0; i < servers.size(); i++) {
				// check if localServerName is the backup server of servers.get(i)
				String rvBkpSvr = backupSvr(servers.get(i).getName());
				if(localServerName.equals(rvBkpSvr)) {
					try {
						//create the socket and corresponding streams to communicate with the server
						Socket currSocket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
						PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
						Scanner inSvr = new Scanner(currSocket.getInputStream());
						addBkp(outSvr, inSvr, servers.get(i).getName());
					} catch (UnknownHostException e) {
						System.err.println("[doAdd] DEBUG: Cannot connect to " + servers.get(i).getName());
						//System.exit(1);
					} catch (IOException e) {
						System.err.println("[doAdd] DEBUG: Cannot connect to " + servers.get(i).getName());
						//System.exit(1);
					}
				}
			}
		
			//generate the svrPartMap
			//genSvrPartMap();
		}
		*/
		
		//doBackup();//send the topics to the backup server fror backup
	}
	
	//generate the new SvrPartMap
	private void genSvrPartMap() {
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {
			String currTopic = e.getKey();//current topic name
			int numPart = e.getValue().size();//number of partitions of the current topic
			for(int i = 0; i < numPart; i++) {//iterate over parts
				String str = currTopic + " partition " + i;//create the string by combining the topicName and partition number
				int svrNum = doSvrMap(str);//servers already added to the stream platform by a clent
				//partSvrMap.get(topicName)[i] = servers.get(svrNum).getName();
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
	
	//receive the topics and svrPartMap from reverse-backup server and store the data: topics->bkpTopics after being added to the streaming platform
	private void addBkp(PrintWriter outSvr, Scanner inSvr, String svrName) {
		//System.out.println("[addBkp] DEBUG: recover the backup data for "+localServerName);
		outSvr.println(localServerName+" "+"addbkp");
		
		//recover the backup topics from the reverse-backup server!
		Map<String, ArrayList<ArrayList<Record>>> bkpTopics = svrData.getBkpTopics();
		String line = inSvr.nextLine();
		System.out.println("[addBkp] DEBUG: add->backup for "+svrName+": "+line);//print out the data&info message for debug purpose
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
	
	//send the topics to the backup server fror backup
	private void doBackup() {
		String backupSvrName = backupSvr(localServerName);
		if(backupSvrName==null) {//localServerName deleted from servers already
			return;
		}
		for(int i = 0; i < servers.size(); i++) {//iterate over the servers
			if(backupSvrName.equals(servers.get(i).getName())&&(!backupSvrName.equals(localServerName))) {//is the backup server->send the topics for backup->should NOT be localServerName itself!->only 1 server on platform
				try {
					Socket socket = new Socket(servers.get(i).getIP(), servers.get(i).getPort());
					PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
					
					System.out.println("[doBackup] DEBUG: send the topics to the backup server: " + servers.get(i).getName());
					out.println(localServerName + " " + "backup");//send the backup cmd first
					//send the topics
					out.print(topics.size());//number of topics
					//System.out.println(topics.size());//number of topics
					for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {//iterate over topics
						out.print(" " + e.getKey());//current topic name
						out.print(" " + e.getValue().size());//number of partitions of the current topic
						for(int j = 0; j < e.getValue().size(); j++) {//iterate over parts
							out.print(" " + e.getValue().get(j).size());//number of records of the current part
							for(int k = 0; k < e.getValue().get(j).size(); k++) {//iterate over records
								out.print(" " + e.getValue().get(j).get(k).getKey() + " " + e.getValue().get(j).get(k).getValue());
							}
						}
					}
					//send the info of parts located on local server
					//check if svrPartMap contains currSvr
					if(svrPartMap.get(localServerName) == null) {
						svrPartMap.put(localServerName, new HashMap<String, HashSet<Integer>>());
					}
					HashMap<String, HashSet<Integer>> svrParts = svrPartMap.get(localServerName);//the partitions currently stored on localServerName
					out.print(" " + localServerName);//local server name
					out.print(" " + svrParts.size());//number of topics
					for(Map.Entry<String, HashSet<Integer>> entry: svrParts.entrySet()) {//iterate over topics
						out.print(" " + entry.getKey());//topic name
						out.print(" " + entry.getValue().size());//number of partitions
						for(Integer index: entry.getValue()) {//iterate ove parts
							out.print(" " + index);//index of the partition
						}
					}
					out.println();
				} catch (UnknownHostException e) {
					System.err.println("[doBackup] DEBUG: Cannot connect to " + servers.get(i).getName());
					//System.exit(1);
				} catch (IOException e) {
					System.err.println("[doBackup] DEBUG: 	Cannot connect to " + servers.get(i).getName());
					//System.exit(1);
				}
			}
		}
	}
	
	//recv the topics and svrPartMap from the reverse-backup server
	private void doRecvBkp(BufferedReader in, String clientName){
		try {
			//receive the topics from the reverse-backup server->store in bkpTopics->reset svrData.bkpTopics
			Map<String, ArrayList<ArrayList<Record>>> bkpTopics = svrData.getBkpTopics();
			//String line = in.nextLine();
			String line = in.readLine();
			System.out.println("[doRecvBkp] DEBUG: backup topics from "+clientName+": "+line);//print out the data&info message for debug purpose
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
			svrData.setBkpTopics(bkpTopics);//reset the backup topics
			
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
		} catch (UnknownHostException e) {
			System.err.println("ERROR: cannot read line");
			//System.exit(1);
		} catch (IOException e) {
			System.err.println("ERROR: cannot read line");
		}
	}
	
	//send the data/info to the server where the partition relocated if the server NOT initialized yet->a new added server
	private void doRelocUpdate(PrintWriter outSvr) {
		outSvr.print(topics.size());//number of topics
		for(Map.Entry<String, ArrayList<ArrayList<Record>>> e: topics.entrySet()) {
			outSvr.print(" " + e.getKey());//current topic name
			outSvr.print(" " + e.getValue().size());//number of partitions of the current topic
		}
		
		//wirte [now] to the info file
		for(Map.Entry<String, int[]> e: now.entrySet()) {
			outSvr.print(" " + e.getKey());//current topic name
			outSvr.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outSvr.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [offset] to the info file
		for(Map.Entry<String, int[]> e: offset.entrySet()) {
			outSvr.print(" " + e.getKey());
			outSvr.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outSvr.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [number of subscribers] to the info file
		for(Map.Entry<String, Integer> e: numSub.entrySet()) {
			outSvr.print(" " + e.getKey());
			outSvr.print(" " + e.getValue());
		}
		
		//wirte [list of subscribers] to the info file
		for(Map.Entry<String, ArrayList<String>> e: subList.entrySet()) {
			outSvr.print(" " + e.getKey());
			outSvr.print(" " + e.getValue().size());//number of partitions of the current topic
			for(int i = 0; i < e.getValue().size(); i++) {
				outSvr.print(" " + e.getValue().get(i));
			}
		}
		
		//wirte [partition-subscriber mapping] to the info file
		for(Map.Entry<String, String[]> e: partSubMap.entrySet()) {
			outSvr.print(" " + e.getKey());
			outSvr.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outSvr.print(" " + e.getValue()[i]);
			}
		}
		
		//wirte [partition-server mapping] to the info file
		for(Map.Entry<String, String[]> e: partSvrMap.entrySet()) {
			outSvr.print(" " + e.getKey());
			outSvr.print(" " + e.getValue().length);//number of partitions of the current topic
			for(int i = 0; i < e.getValue().length; i++) {
				outSvr.print(" " + e.getValue()[i]);
			}
		}
	}
	
	//relocate the partitions when add/delete servers
	private void doPartReloc() {
		HashMap<String, HashSet<Integer>> svrParts = svrPartMap.get(localServerName);//the partitions currently stored on localServerName
		if(svrParts!=null) {//server already initialized
			HashMap<String, HashSet<Integer>> newSvrParts = new HashMap<String, HashSet<Integer>>();
			for(Map.Entry<String, HashSet<Integer>> entry: svrParts.entrySet()) {//iterate each and every topic
				String currTopic = entry.getKey();
				newSvrParts.put(currTopic, new HashSet<Integer>());
				HashSet<Integer> parts = entry.getValue();//the partitions of currTopic currently stored on localServerName
				Iterator<Integer> iter = parts.iterator();
				while(iter.hasNext()){
				//for(Integer index: parts) {//iterate each and every partition
					int index = iter.next();
					String str = currTopic + " partition " + index;//create the string by combining the topicName and partition number
					int svrNum = doSvrMap(str);//servers already added to the stream platform by a clent
					String mapSvrName = servers.get(svrNum).getName();
					if(!localServerName.equals(mapSvrName)) {//mapSvrName is NOT localServerName, need to move the partition
						iter.remove();
						partSvrMap.get(currTopic)[index] = mapSvrName;//update the partSvrMap
						ArrayList<Record> part = topics.get(currTopic).get(index);
						try {
							//create the socket and corresponding streams to communicate with the server
							Socket currSocket = new Socket(servers.get(svrNum).getIP(), servers.get(svrNum).getPort());
							PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
							Scanner inSvr = new Scanner(currSocket.getInputStream());
							System.out.println("[doPartReloc] DEBUG: " + localServerName + " move partition " + index + " of " + currTopic + " to " + servers.get(svrNum).getName());
							outSvr.println(localServerName + " " + "move (topic=" + currTopic + " partition=" + index + ") " + part.size());
							System.out.println("[doPartReloc] DEBUG: " + localServerName + " " + "move (topic=" + currTopic + " partition=" + index + ") " + part.size());
							//outSvr.println(localServerName + " " + "move (topic=" + currTopic + " partition=" + index + ") ");
							//System.out.println(localServerName + " " + "move (topic=" + currTopic + " partition=" + index + ") ");
							
							if(inSvr.nextLine().equals("false")) {//mapSvrName not initialize yet
								doRelocUpdate(outSvr);
							}
							
							//move [part] info.
							for(int i = 0; i < part.size(); i++) {
								outSvr.print(" " + part.get(i).getKey() + " " + part.get(i).getValue());
								System.out.print(" " + part.get(i).getKey() + " " + part.get(i).getValue());
							}
							//move [now] info.
							outSvr.print(" " + now.get(currTopic)[index]);
							System.out.print(" " + now.get(currTopic)[index]);
							//move [offset] info.
							outSvr.print(" " + offset.get(currTopic)[index]);
							System.out.print(" " + offset.get(currTopic)[index]);
							//move [partSubMap] info.
							outSvr.print(" " + partSubMap.get(currTopic)[index]);
							System.out.print(" " + partSubMap.get(currTopic)[index]);
							
							outSvr.println();
							System.out.println();
							//outSvr.println(localServerName + " " + "restart" + " " + localServerName + " " + serverSocket.getLocalPort() + " " + index);//send the restart message to the server
							String ackMsg = inSvr.nextLine();
							System.out.println("[doPartReloc] DEBUG: from " + servers.get(svrNum).getName() + ": " + ackMsg);
							//close the socket and corresponding streams
							inSvr.close();
							outSvr.close();
							currSocket.close();
							
							//remove this partition from localServerName
							topics.get(currTopic).set(index, new ArrayList<Record>());
							//svrPartMap.get(localServerName).get(currTopic).remove(index);
						} catch (UnknownHostException e) {
							System.err.println("[doPartReloc] DEBUG: Cannot connect to " + servers.get(svrNum).getName());
							//System.exit(1);
						} catch (IOException e) {
							System.err.println("[doPartReloc] DEBUG: Cannot connect to " + servers.get(svrNum).getName());
							//System.exit(1);
						}
					}
					else {
						newSvrParts.get(currTopic).add(index);
					}
				}
			}
			
			doBackup();//send the topics to the backup server fror backup
		}
	}
	
	//receive and store the partition from another server
	private void doMvPart(BufferedReader in, Scanner lineScanner, PrintWriter out, String clientName) throws IOException {
		String topicName = lineScanner.next().substring(7);//skip "(topic=" at the beginning
		if(!lineScanner.hasNext()) {
			System.out.println("missing the partition number");
			out.println("missing the partition number");
			return;
		}
		String str = lineScanner.next();
		str = str.substring(10);//skip the 'partition=' at the beginning
		str = str.substring(0, str.length()-1);//ship ")" at the end
		int partNum = Integer.parseInt(str);
		int partSize = lineScanner.nextInt();
		//int numPart = lineScanner.nextInt();
		if(topics.size() == 0) {
			//no topic added to topics yet->do initialization here->data/info received might be INCOMPLETE or INACCURATE!
			out.println("false");
			
			String line = in.readLine();
			System.out.println("DEBUG: initializing data/info from "+clientName+": "+line);
			lineScanner = new Scanner(line);
			
			int numTopics = lineScanner.nextInt();
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numPart = lineScanner.nextInt();
				ArrayList<ArrayList<Record>> topic = new ArrayList<ArrayList<Record>>();
				for(int j = 0; j < numPart; j++) {
					topic.add(new ArrayList<Record>());
				}
				topics.put(currTopic, topic);
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numPart = lineScanner.nextInt();
				now.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					now.get(currTopic)[j] = lineScanner.nextInt();
				}
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numPart = lineScanner.nextInt();
				offset.put(currTopic, new int[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					offset.get(currTopic)[j] = lineScanner.nextInt();
				}
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numS = lineScanner.nextInt();
				numSub.put(currTopic, numS);
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numSub = lineScanner.nextInt();
				subList.put(currTopic, new ArrayList<String>());
				for(int j = 0; j < numSub; j++) {
					subList.get(currTopic).add(lineScanner.next());
				}
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numPart = lineScanner.nextInt();
				partSubMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					partSubMap.get(currTopic)[j] = lineScanner.next();
				}
			}
			
			for(int i = 0; i < numTopics; i++) {
				String currTopic = lineScanner.next();
				int numPart = lineScanner.nextInt();
				partSvrMap.put(currTopic, new String[numPart]);//initialize with number of partitions specified by user input
				for(int j = 0; j < numPart; j++) {
					partSvrMap.get(currTopic)[j] = lineScanner.next();
				}
			}
		}
		else {
			out.println("true");
			String line = in.readLine();
			lineScanner = new Scanner(line);
		}
		//move the partition
		ArrayList<Record> part = new ArrayList<Record>();
		for(int i = 0; i < partSize; i++) {
			String key = lineScanner.next();
			int value = lineScanner.nextInt();
			part.add(new Record(key, value));
		}
		//update topics
		topics.get(topicName).set(partNum, part);
		//update partSvrMap
		partSvrMap.get(topicName)[partNum] = localServerName;
		//update svrPartMap
		if(svrPartMap.get(localServerName) == null) {//this server is not in svrPartMap yet
			svrPartMap.put(localServerName, new HashMap<String, HashSet<Integer>>());
		}
		if(svrPartMap.get(localServerName).get(topicName) == null) {//topicName is not associated with this server yet
			svrPartMap.get(localServerName).put(topicName, new HashSet<Integer>());
		}
		svrPartMap.get(localServerName).get(topicName).add(partNum);//associate partition partNum of topicName with this server
		
		now.get(topicName)[partNum] = lineScanner.nextInt();//update [now] info.
		offset.get(topicName)[partNum] = lineScanner.nextInt();//update [offset] info.
		partSubMap.get(topicName)[partNum] = lineScanner.next();//update [partSubMap] info.
		
		System.out.println("DEBUG: partition[" + partNum + "] of " + topicName + " moved from " + clientName + " to " + localServerName);
		out.println("partition[" + partNum + "]  of " + topicName + " moved from " + clientName + " to " + localServerName);
		
		doBackup();//send the topics and svrPartMap to the backup server fror backup
	}
	
	private void doDel(Scanner lineScanner, PrintWriter out, PrintWriter outServers, String outFileName) {
		boolean deleted = false;
		//servers = null;
		String delCmd = "";
		//boolean isDel = false;//indicate this server is newly added to the streaming platform or not
		//no need to reply to the client in this case
		while(lineScanner.hasNext()) {
			String serverName = lineScanner.next().substring(6);//ignore the '(name='
			String serverIP = lineScanner.next().substring(3);//ignore the 'ip='
			String portStr = lineScanner.next().substring(5);//ignore the 'port='
			int serverPort = Integer.parseInt(portStr.substring(0, portStr.length()-1));//ignore the ')'
			
			if(localServerName.equals(serverName)) {//this server is to be deleted, forward the delete message to the connected clients
				deleted = true;
				delCmd = "delete ";
				delCmd += "(name=" + serverName + " " + "ip=" + serverIP + " " + "port=" + serverPort + ") ";//generate the delete command to be sent to clients
				
				for(Map.Entry<String, Socket> entry: clientSockets.entrySet()) {//forward the delete message to all the clients connected with this server
					String currCltName = entry.getKey();//get the client name from the hashmap
					System.out.println("current client: " + currCltName);//display the client name
					
					Socket currSocket = entry.getValue();//get the socket associate with the client name
					try{
						PrintWriter currOut = new PrintWriter(currSocket.getOutputStream(), true);//create the out stream for currCltName
						System.out.println("DEBUG: forward the delete messge to " + currCltName);//
						System.out.println("DEBUG: " + delCmd);
						currOut.println(delCmd);//send the delete command to the client
					} catch (IOException e) {
						System.out.println(currCltName + " " + "in or out failed");//print currCltName in the case cannot
					}
				}
				out.println(localServerName + " delete command processed");//reply the client sending the delete command
				//System.exit(-1);//exit the program//NOT QUIT HERE!
			}
			
			//delete the server's info from servers
			for(int i = 0; i < servers.size(); i++) {
				if(servers.get(i).getName().equals(serverName)) {//found this server in servers
					servers.remove(i);//remove this server's info from servers
					break;//no need to further the loop
				}
			}
			System.out.println("deleted: " + serverName + " " + serverIP + " " + serverPort);//display the info of the server to be deleted
		}
		
		//relocate the parts after deleting the servers
		doPartReloc();
		
		//initialize the serversInfo file reader
		try {
			//String localDir = "/tmp/92476/stream";
			//String localDir = "/Users/gladet/csc502/stream";
			String localDir = "../stream";
			outFileName = localDir+"/serversInfo";
			
			//outFileName = "serversInfo"; //the local file to store the servers info, using relative path
			outServers = new PrintWriter(outFileName);
			
			outServers.println(servers.size());//write the number of servers into local file
			for(int i = 0; i < servers.size(); i++) {//2 servers
				outServers.println(servers.get(i).getName() + " " + servers.get(i).getIP() + " " + servers.get(i).getPort());//write the servers' info to the local file
			}
			
			outServers.close();//close the out stream when no more info to write to the local file
			
			//remove the data/info files
			if(deleted) {
				//Runtime.getRuntime().exec("rm " + outFileName);//remove the file
				outFileName = localDir+"/"+localServerName+"_data";
				Runtime.getRuntime().exec("rm " + outFileName);//remove the file
				outFileName = localDir+"/"+localServerName+"_info";
				Runtime.getRuntime().exec("rm " + outFileName);//remove the file
				System.exit(-1);//exit the program when server deleted
			}
		}catch (FileNotFoundException exception) {
			System.out.println("ERROR: output file [" + outFileName + "] does not exist");
		}catch(IOException e) {
			e.printStackTrace();
		}
		
		//chmod 777
		try {
			File file = new File(outFileName);
			Runtime.getRuntime().exec("chmod 777 " + outFileName);
			
		} catch(IOException e) {
			e.printStackTrace();
		}
		//***
		
		//out.println(localServerName + " added to the stream platform");
		out.println(localServerName + " delete command processed");//reply the client sending the delete command

	}
	
	private String backupSvr(String svrName) {
		for(int i = 0; i < servers.size(); i++) {
			if(servers.get(i).getName().equals(svrName)) {//find the index of the server in servers
				//int index = (i+1)%servers.size();//select the following server in servers as the backup server
				int index = (i-1+servers.size())%servers.size();//select the previous server in servers as the backup server
				return servers.get(index).getName();
			}
		}
		return null;
	}
	
	private int doSvrMap(String str) {
		return Math.abs(str.hashCode())%servers.size();
	}
}
