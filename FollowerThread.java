import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.net.*;
import java.io.*;
import java.util.Random;

//monitor the follower status, will change the state to candidate if not receive appendRPC (including heartbeat) from the leader within timeout interval
public class FollowerThread extends Thread {
	//instance variables
	private String localSvrName;
	private ArrayList<ServerInfo> servers;
	private RaftData raftData;
	
	private int state;//the state of the server, can be FOLLOWER, CANDIDATE, LEADER
	private int currTerm;//latest term server has seen
	private int votes;
	private int numReplyVote;
	//private long timeoutInterval;//the randomly generated timeout interval in the range of 150-300 ms
	private ArrayList<LogEntry> log;//replicated log to record all the commands from all the clients in chronological orderprivate
	long followerStartTime;
	
	private static final int FOLLOWER = 0;
	private static final int CANDIDATE = 1;
	
	//constructor
	/*long followerStartTime, long timeoutInterval, */
	//public FollowerThread(String localSvrName, int state, int currTerm, int votes, int numReplyVote, ArrayList<LogEntry> log, ArrayList<ServerInfo> servers, long followerStartTime) {
	public FollowerThread(String localSvrName, ArrayList<ServerInfo> servers, RaftData raftData) {
		this.localSvrName = localSvrName;
		this.servers = servers;
		this.raftData = raftData;
		
		this.state = raftData.getState();
		this.followerStartTime = raftData.getFollowerStartTime();
		//this.timeoutInterval = timeoutInterval;
		this.votes = raftData.getVotes();
		this.numReplyVote = raftData.getNumReplyVote();
		this.log = raftData.getLog();
		/*
		this.currTerm = raftData.getCurrTerm();
		this.numReplicated = raftData.getNumReplicated();
		this.nextIndex = raftData.getNextIndex();
		this.matchIndex = raftData.getMatchIndex();
		this.comittedIndex = raftData.getCommittedIndex();
		this.serverSockets = raftData.getServerSockets();
		 this.cltCmdIndex = raftData.getCltCmdIndex();*/
	}
	
	public void run() {
		if((state=raftData.getState()) != FOLLOWER) {
			System.out.println("[FollowerThread run] DEBUG: the server's state is NOT FOLLOWER");
			return;
		}
		boolean timeout = false;
		Random randomInterval = new Random();
		long timeoutInterval = 7000;//15000;//150000;//150;//4500;//
		timeoutInterval += randomInterval.nextInt(7000);//7000;//150
		raftData.setTimeoutInterval(timeoutInterval);
		//followerStartTime = new GregorianCalendar().getTimeInMillis();
		raftData.setFollowerStartTime(followerStartTime = new GregorianCalendar().getTimeInMillis());
		while(!timeout) {
			followerStartTime = raftData.getFollowerStartTime();//update followerStartTime with raftData's followerStartTime
			long interval = (long) (timeoutInterval/10);
			long currTime = new GregorianCalendar().getTimeInMillis();//get the current system time
			if(currTime - followerStartTime < interval) {
				//Thread.sleep(timeoutInterval-150);
				try {
					Thread.sleep(timeoutInterval-interval-120);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			followerStartTime = raftData.getFollowerStartTime();//update followerStartTime with raftData's followerStartTime
			//currTime = System.currentTimeMillis();//get the current system time
			currTime = new GregorianCalendar().getTimeInMillis();//get the current system time
			if(currTime - followerStartTime > timeoutInterval) {
				state = CANDIDATE;//switch to the candidate state
				System.out.println("[FollowerThread run] DEBUG: Switched to CANDIDATE state");
				raftData.setState(state);//reset the state in raftData
				new CandThread(localSvrName, servers, raftData).start();//create the candidate thread
				timeout = true;
			}
		}
	}
	
	private void reqVoteRPC() {
		int lastLogIndex = log.size()-1;
		int lastLogTerm = 0;
		if(lastLogIndex == -1) {
			currTerm = raftData.getCurrTerm();
			lastLogTerm = currTerm;
		}
		else {
			//lastLogTerm = log.get(log.size()-1).getTerm();
			lastLogTerm = log.get(lastLogIndex).getTerm();
		}
		currTerm = raftData.getCurrTerm();
		String message = localSvrName+" "+"reqvote"+" "+currTerm+" "+lastLogIndex+" "+lastLogTerm;//the requestVote message to send to other servers
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
}
