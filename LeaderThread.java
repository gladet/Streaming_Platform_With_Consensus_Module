import java.util.ArrayList;
import java.net.*;
import java.io.*;
import java.lang.Thread;

//listen to the message from other servers, the message could be appendRPC or voteRPC
public class LeaderThread extends Thread {
	//instance variables
	private String serverName;
	private ArrayList<ServerInfo> servers;
	private RaftData raftData;
	
	private int state;//the state of the server, can be FOLLOWER, CANDIDATE, LEADER
	private int currTerm;//latest term server has seen
	private int votes;
	//private long timeoutInterval;//the randomly generated timeout interval in the range of 150-300 ms
	private ArrayList<LogEntry> log;//replicated log to record all the commands from all the clients in chronological orderprivate long startTime;
	private int committedIndex;
	private ArrayList<Integer> nextIndex;
	private ArrayList<Integer> matchIndex;
	
	private static final int FOLLOWER = 0;
	private static final int LEADER = 2;
	
	//constructor
	//public LeaderThread(String serverName, int state, int currTerm, int votes, ArrayList<LogEntry> log, ArrayList<Integer> nextIndex, ArrayList<Integer> matchIndex, int committedIndex, ArrayList<ServerInfo> servers) {
	public LeaderThread(String serverName, ArrayList<ServerInfo> servers, RaftData raftData) {
		this.serverName = serverName;
		this.servers = servers;
		this.raftData = raftData;
		
		this.state = raftData.getState();
		//this.startTime = startTime;
		//this.timeoutInterval = timeoutInterval;
		this.currTerm = raftData.getCurrTerm();
		this.votes = raftData.getVotes();
		this.log = raftData.getLog();
		this.nextIndex = raftData.getNextIndex();
		this.matchIndex = raftData.getMatchIndex();
		this.committedIndex = raftData.getCommittedIndex();
		
		/*
		 this.numReplicated = raftData.getNumReplicated();
		this.numReplyVote = raftData.getNumReplyVote();
		this.serverSockets = raftData.getServerSockets();
		 this.followerStartTime = raftData.getFollowerStartTime();
		 this.cltCmdIndex = raftData.getCltCmdIndex();*/
	}
	
	private void doAppendRPC() {
		currTerm = raftData.getCurrTerm();
		String message = serverName+" "+"append"+" "+currTerm;//the requestVote message to send to other servers
		//System.out.println(message);
		//nextIndex = new ArrayList<Integer>();
		int lastLogIndex = log.size()-1;
		//send the AppendEntries message to each and every server including itself
		for(int i = 0; i < servers.size(); i++) {
			//ArrayList<LogEntry> entries = new ArrayList<LogEntry>();
			int prevLogIndex = nextIndex.get(i)-1;//index of log entry immediately preceding new ones
			int prevLogTerm = 0;
			//boundary case: no log entry yet
			if(prevLogIndex == -1) {
				currTerm = raftData.getCurrTerm();
				prevLogTerm = currTerm;
			} else {
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
				committedIndex = raftData.getCommittedIndex();//also include committedIndex in the appendRPC
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
				System.err.println("[doAppendRPC] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			} catch (IOException e) {
				System.err.println("[doAppendRPC] DEBUG: Cannot connect to " + servers.get(i).getName());
				//System.exit(1);
			}
			/*committedIndex = raftData.getCommittedIndex();
			String currMsg = message + " " + prevLogIndex + " " + prevLogTerm + " " + committedIndex + " " + numEntries;
			int next = nextIndex.get(i);//the index of the first new log entry to be replicated to servers[i]
			for(int j = 0; j < numEntries; j++) {
				LogEntry entry = log.get(j+next);
				currMsg += " "+entry.getTerm()+" "+entry.getCltName()+" "+entry.getCmdIndex()+" "+entry.getCmd();
			}
			if(numEntries > 0)
				System.out.println(currMsg);//debug message
			//not send heartbeat message
			if(numEntries > 0)
			appendMsg(servers.get(i), currMsg);*/
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
	
	//run method
	public void run() {
		//initializtion
		nextIndex = new ArrayList<Integer>();//index of the next log entry to send to that server
		matchIndex = new ArrayList<Integer>();//index of highest log entry known to be replicated on server
		int lastLogIndex = log.size()-1;
		//initialize nextIndex and matchIndex
		for(int i = 0; i < servers.size(); i++) {
			nextIndex.add(lastLogIndex+1);
			//matchIndex.add(lastLogIndex);
			//matchIndex.add(0);
			matchIndex.add(-1);
		}
		raftData.setNextIndex(nextIndex);//reset nextIndex in raftData
		raftData.setMatchIndex(matchIndex);//reset matchIndex in raftData
		while((state=raftData.getState()) == LEADER) {
			//String message = localSvrName+" "+"append"+" "+currTerm+" "+lastLogIndex+" "+lastLogTerm;//the requestVote message to send to other servers
			doAppendRPC();
			try {
				Thread.sleep(3000);//50000//1500//50//4500//20//3000
			} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			}
		}
		System.out.println("[LeaderThread run] DEBUG: Current state: "+(state=raftData.getState()));
	}
}
