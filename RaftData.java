import java.util.ArrayList;
import java.lang.String;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;

//import java.util.Scanner;
import java.io.*;
import java.net.*;

//import java.util.NoSuchElementException;
//import java.lang.NullPointerException;

public class RaftData {
	//instance variables
	//will be used by the ServerThread objects
	private Map<String, Socket> serverSockets;//store the listen socket of each server
	private int state;//the state of the server, can be FOLLOWER, CANDIDATE, LEADER
	private int currTerm;//latest term server has seen
	private int votes;
	private ArrayList<LogEntry> log;//replicated log to record all the commands from all the clients in chronological order
	private ArrayList<Integer> nextIndex;
	private ArrayList<Integer> matchIndex;
	private int committedIndex;
	private int numReplicated;//number of followers to which the leader has successfully replicated the log entries
	private int numReplyVote;//number of replies of reqVote
	private long followerStartTime;
	private long candStartTime;
	private Map<String, Integer> cltCmdIndex;
	private String votedFor;
	private int lastApplied;
	private FollowerThread followerThread;
	private long timeoutInterval;
	
	private static final int FOLLOWER = 0;
	
	//constructor
	public RaftData() {
		serverSockets = new HashMap<String, Socket>();
		state = FOLLOWER;//state is initiated as FOLLOWER by default
		currTerm = 0;
		votes = 0;
		log = new ArrayList<LogEntry>();
		nextIndex = new ArrayList<Integer>();
		matchIndex = new ArrayList<Integer>();
		committedIndex = -1;//0;
		numReplicated = 0;
		numReplyVote = 0;
		followerStartTime = 0;
		candStartTime = 0;
		cltCmdIndex = new HashMap<String, Integer>();
		votedFor = "";
		lastApplied = -1;
		followerThread = null;
		timeoutInterval = 0;
	}
	
	public void writeLog(String localServerName) {
		PrintWriter outLog = null;
		String outFileName = null;
		try {
			//String localDir = "/tmp/92476/stream";
			//String localDir = "/Users/gladet/csc502/stream";
			String localDir = "../stream";
			outFileName = localDir+"/"+localServerName+"_log";
			
			outLog = new PrintWriter(outFileName);
		}catch (FileNotFoundException exception) {
			System.out.println("ERROR: output file [" + outFileName + "] does not exist");
		}
		
		//write log
		outLog.println(log.size());//number of log entries
		for(int i = 0; i < log.size(); i++) {
			LogEntry logEntry = log.get(i);//get the current log entry
			int currTerm = logEntry.getTerm();
			String clientName = logEntry.getCltName();
			int cmdIndex = logEntry.getCmdIndex();
			String cmd = logEntry.getCmd();
			outLog.println(currTerm+" "+clientName+" "+cmdIndex+" "+cmd);
		}
		
		//int lastApplied = raftData.getLastApplied();
		outLog.println(lastApplied);
		//int committedIndex = raftData.getCommittedIndex();
		outLog.println(committedIndex);
		
		outLog.close();//close the out stream when no more log entry to write to the local file
		
		//chmod 777
		try {
			File file = new File(outFileName);
			Runtime.getRuntime().exec("chmod 777 " + outFileName);
		} catch(IOException e) {
			e.printStackTrace();
		}
		//***
	}
	
	//printLog method
	public void printLog() {
		System.out.println(log.size());
		for(int i = 0; i < log.size(); i++) {
			log.get(i).printLogEntry();
		}
		System.out.println(lastApplied);
		System.out.println(committedIndex);
	}
	
	//set methods
	public int getState() {
		return state;
	}
	
	public int getCurrTerm() {
		return currTerm;
	}
	
	public int getVotes() {
		return votes;
	}
	
	public ArrayList<Integer> getNextIndex() {
		return nextIndex;
	}
	
	public ArrayList<Integer> getMatchIndex() {
		return matchIndex;
	}
	
	public int getCommittedIndex() {
		return committedIndex;
	}
	
	public int getNumReplicated() {
		return numReplicated;
	}
	
	public int getNumReplyVote() {
		return numReplyVote;
	}
	
	public long getFollowerStartTime() {
		return followerStartTime;
	}
	
	public long getCandStartTime() {
		return candStartTime;
	}
	
	public ArrayList<LogEntry> getLog() {
		return log;
	}
	
	public Map<String, Socket> getServerSockets() {
		return serverSockets;
	}
	
	public Map<String, Integer> getCltCmdIndex() {
		return cltCmdIndex;
	}
	
	public String getVotedFor() {
		return votedFor;
	}
	
	public int getLastApplied() {
		return lastApplied;
	}
	
	public FollowerThread getFollowerThread() {
		return this.followerThread;
	}
	
	public long getTimeoutInterval() {
		return this.timeoutInterval;
	}
	
	//set methods
	public void setState(int state) {
		this.state = state;
	}
	
	public void setCurrTerm(int currTerm) {
		this.currTerm = currTerm;
	}
	
	public void setVotes(int votes) {
		this.votes = votes;
	}
	
	public void setNextIndex(ArrayList<Integer> nextIndex) {
		this.nextIndex = nextIndex;
	}
	
	public void setMatchIndex(ArrayList<Integer> matchIndex) {
		this.matchIndex = matchIndex;
	}
	
	public void setCommittedIndex(int committedIndex) {
		this.committedIndex = committedIndex;
	}
	
	public void setNumReplicated(int numReplicated) {
		this.numReplicated = numReplicated;
	}
	
	public void setNumReplyVote(int numReplyVote) {
		this.numReplyVote = numReplyVote;
	}
	
	public void setFollowerStartTime(long followerStartTime) {
		this.followerStartTime = followerStartTime;
	}
	
	public void setCandStartTime(long candStartTime) {
		this.candStartTime = candStartTime;
	}
	
	public void setLog(ArrayList<LogEntry> log) {
		this.log = log;
	}
	
	public void setServerSockets(Map<String, Socket> serverSockets) {
		this.serverSockets = serverSockets;
	}
	
	public void setCltCmdIndex(Map<String, Integer> cltCmdIndex) {
		this.cltCmdIndex = cltCmdIndex;
	}
	
	public void setVotedFor(String votedFor) {
		this.votedFor = votedFor;
	}
	
	public void setLastApplied(int lastApplied) {
		this.lastApplied = lastApplied;
	}
	
	public void setFollowerThread(FollowerThread followerThread) {
		this.followerThread = followerThread;
	}
	
	public void setTimeoutInterval(long timeoutInterval) {
		this.timeoutInterval = timeoutInterval;
	}
}