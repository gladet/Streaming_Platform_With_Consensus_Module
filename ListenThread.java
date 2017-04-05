import java.util.ArrayList;
import java.io.*;
import java.net.*;
import java.util.Scanner;

//listen to the message from other servers, the message could be appendRPC or voteRPC
public class ListenThread extends Thread {
	//instance variables
	private String serverName;
	private ArrayList<ServerInfo> servers;
	private ServerInfo svrInfo;
	private RaftData raftData;
	
	private int state;//the state of the server, can be FOLLOWER, CANDIDATE, LEADER
	private int currTerm;//latest term server has seen
	private String votedFor;
	private int votes;
	private int numReplyVote;
	private int numReplicated;
	//private long timeoutInterval;//the randomly generated timeout interval in the range of 150-300 ms
	private ArrayList<LogEntry> log;//replicated log to record all the commands from all the clients in chronological orderprivate long startTime;
	private int committedIndex;
	private ArrayList<Integer> nextIndex;
	private ArrayList<Integer> matchIndex;
	
	private static final int FOLLOWER = 0;
	private static final int CANDIDATE = 1;
	private static final int LEADER = 2;
	
	//constructor
	//public ListenThread(String serverName, int state, int currTerm, int votes, int numReplyVote, int numReplicated, ArrayList<LogEntry> log, ArrayList<Integer> nextIndex, ArrayList<Integer> matchIndex, int committedIndex, ArrayList<ServerInfo> servers, ServerInfo svrInfo) {
	public ListenThread(String serverName, ArrayList<ServerInfo> servers, ServerInfo svrInfo, RaftData raftData) {
		this.serverName = serverName;
		this.servers = servers;
		this.svrInfo = svrInfo;
		this.raftData = raftData;
		
		this.state = raftData.getState();
		this.currTerm = raftData.getCurrTerm();
		this.votedFor = raftData.getVotedFor();
		this.votes = raftData.getVotes();
		this.numReplicated = raftData.getNumReplicated();
		this.log = raftData.getLog();
		this.nextIndex = raftData.getNextIndex();
		this.matchIndex = raftData.getMatchIndex();
		this.committedIndex = raftData.getCommittedIndex();
		this.numReplyVote = raftData.getNumReplyVote();
		/*
		this.serverSockets = raftData.getServerSockets();
		this.followerStartTime = raftData.getFollowerStartTime();
		this.cltCmdIndex = raftData.getCltCmdIndex();*/
	}
	
	private void procReplyVote(Scanner lineScanner) {
		//process the message only when the server is still the CANDIDATE
		if((state = raftData.getState()) != CANDIDATE) {
			System.out.println("[procReplyVote] DEBUG: Not CANDIDATE state now");
			return;
		}
		int term = lineScanner.nextInt();
		currTerm = raftData.getCurrTerm();
		if(term > currTerm) {
			currTerm = term;
			raftData.setCurrTerm(currTerm);//reset currTerm
			votedFor = "";//not voted yet
			raftData.setVotedFor(votedFor);
			state = FOLLOWER;//switch to FOLLOWER state
			raftData.setState(state);//reset the state in raftData
			System.out.println("[procReplyVote] DEBUG: Switched to FOLLOWER state");
			new FollowerThread(serverName, servers, raftData).start();
			return;
		}
		int vote = lineScanner.nextInt();
		if(vote == 1) {//received vote from a follower
			votes = raftData.getVotes();
			votes++;//increment votes
			raftData.setVotes(votes);//reset votes
			if(votes > servers.size()/2) {//received votes from a majority of the servers, switch to LEADER state
				state = LEADER;
				raftData.setState(state);
				System.out.println("[procReplyVote] DEBUG: Switched to LEADER state");
				//new LeaderThread(serverName, state, currTerm, votes, log, nextIndex, matchIndex, committedIndex, servers).start();
				new LeaderThread(serverName, servers, raftData).start();
			}
		}
		/*
		numReplyVote = raftData.getNumReplyVote();
		numReplyVote++;
		raftData.setNumReplyVote(numReplyVote);
		if(numReplyVote == servers.size() && votes <= servers.size()/2) {
			reqVoteRPC();
		}*/
	}
	
	private void procReplyAppend(Scanner lineScanner) {
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
			new FollowerThread(serverName, servers, raftData).start();
			return;
		}
		//identify the replyAppend message sender server
		int i = 0;
		while(i < servers.size()) {
			if(servers.get(i).getName().equals(svrInfo.getName())) {
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
			
			currTerm = raftData.getCurrTerm();
			String message = serverName+" "+"append"+" "+currTerm;//the requestVote message to send to other servers
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
			}
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
					}
				}
			}
		}
	}
	
	private void appendMsg(ServerInfo server, String message) {
		try {
			//create the socket and corresponding out stream to communicate with the server
			Socket currSocket = new Socket(server.getIP(), server.getPort());
			PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
			outSvr.println(message);
			
			//close the socket and corresponding out stream
			outSvr.close();
			currSocket.close();
		} catch (UnknownHostException e) {
			System.err.println("[appendMsg] DEBUG: Cannot connect to " + server.getName());
			//System.exit(1);
		} catch (IOException e) {
			System.err.println("[appendMsg] DEBUG: Cannot connect to " + server.getName());
			//System.exit(1);
		}
	}
	
	private void reqVoteRPC() {
		int lastLogIndex = log.size();
		int lastLogTerm = log.get(log.size()-1).getTerm();
		currTerm = raftData.getCurrTerm();
		String message = serverName+" "+"reqvote"+" "+currTerm+" "+lastLogIndex+" "+lastLogTerm;//the requestVote message to send to other servers
		System.out.println("DEBUG: " + message);
		//send the requestVote message to each and every server including itself
		for(int i = 0; i < servers.size(); i++) {
			reqVoteMsg(servers.get(i), message);
		}
	}
	
	private void reqVoteMsg(ServerInfo server, String message) {
		try {
			//create the socket and corresponding out stream to communicate with the server
			Socket currSocket = new Socket(server.getIP(), server.getPort());
			PrintWriter outSvr = new PrintWriter(currSocket.getOutputStream(), true);
			outSvr.println(message);
			
			//close the socket and corresponding out stream
			outSvr.close();
			currSocket.close();
		} catch (UnknownHostException e) {
			System.err.println("Cannot connect to " + server.getName());
			//System.exit(1);
		} catch (IOException e) {
			System.err.println("Cannot connect to " + server.getName());
			//System.exit(1);
		}
	}
	
	//run method
	public void run() {
		String fromServer;
		
		try {
			//connect to the server
			Socket socket = new Socket(svrInfo.getIP(), svrInfo.getPort());
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			//System.out.println("[ListenThread run] DEBUG" + serverName + "> " + "The address of the endpoint the client socket [" + svrInfo.getName() + "] is bound to " + socket.getLocalSocketAddress());//the address info. of the local sockets
			//System.out.println("[ListenThread run] DEBUG" + serverName + "> " + "DEBUG: " + serverName + " " + socket.getInetAddress().getHostAddress() + " " + socket.getLocalPort());
			out.println(serverName + " " + "svrlisten" + " " + serverName + " " + socket.getInetAddress().getHostAddress() + " " + socket.getLocalPort()); //send the client info to the servers
			
			while(true) {
				fromServer = in.readLine();
				if(fromServer != null) {
					//System.out.println("Client: " + line);
					//System.out.println(serverName + "> " + svrInfo.getName() + ": " + fromServer);
					Scanner lineScanner = new Scanner(fromServer);
					//lineScanner = new Scanner(line);
					String cmd = "";
					cmd = lineScanner.next();
					if(cmd.equalsIgnoreCase("replyvote")) {
						procReplyVote(lineScanner);
					}
					if(cmd.equalsIgnoreCase("replyappend")) {
						procReplyAppend(lineScanner);
					}
				}
			}
		} catch (UnknownHostException e) {
			System.err.println("cannot connect to: " + svrInfo.getName());
			System.exit(1);
		} catch (IOException e) {
			System.err.println("cannot connect to: " + svrInfo.getName());
			System.exit(1);
		}
	}
}
