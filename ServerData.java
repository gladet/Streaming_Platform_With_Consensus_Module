import java.util.ArrayList;
import java.lang.String;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;

//import java.util.Scanner;
//import java.io.*;
import java.net.*;

//import java.util.NoSuchElementException;
//import java.lang.NullPointerException;

public class ServerData {
	//instance variables
	//will be used by the ServerThread objects
	private Map<String, ArrayList<ArrayList<Record>>> topics;
	private Map<String, ArrayList<ArrayList<Record>>> bkpTopics;//backup topics
	private ArrayList<ServerInfo> servers;
	private ArrayList<ClientInfo> clients;
	private Map<String, Socket> clientSockets;//store the listen socket of each client
	//String localServerName;
	
	private Map<String, Integer> numSub;//record the number of subscribers of each topic
	private Map<String, ArrayList<String>> subList;//record the subscribers of each topic
	private Map<String, String[]> partSubMap;//record the subscriber of each partition of each topic
	private Map<String, String[]> partSvrMap;//record the server where each partition of each topic locates
	private Map<String, int[]> now;//record the size of each partition of each topic
	private Map<String, int[]> offset;//record the current offset of each partition of each topic
	
	private HashMap<String, HashMap<String, HashSet<Integer>>> cliSub;//record the partition number of each topic that the client subscribes
	private HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap;//record the partition number of each topic that the client subscribes
	
	//constructor
	public ServerData() {
		servers = new ArrayList<ServerInfo>(); //store the servers info and write into the local file
		clients = new ArrayList<ClientInfo>(); //store the clients info and werite into the local file
		clientSockets = new HashMap<String, Socket>(); //store the "listen" socket corresponding to each client
		//int portNumber = 0;//for automatic port number allocation
		
		topics = new HashMap<String, ArrayList<ArrayList<Record>>>();//store the data of topics and write into the local data file
		bkpTopics = new HashMap<String, ArrayList<ArrayList<Record>>>();//store the data of backup topics and write into the local data file
		numSub = new HashMap<String, Integer>();//record the number of subscribers of each topic
		subList = new HashMap<String, ArrayList<String>>();//record the subscribers of each topic
		partSubMap = new HashMap<String, String[]>();//record the subscriber of each partition of each topic
		partSvrMap = new HashMap<String, String[]>();//record the server where each partition of each topic locates
		now = new HashMap<String, int[]>();//record the size of each partition of each topic
		offset = new HashMap<String, int[]>();//record the current offset of each partition of each topic
		
		cliSub = new HashMap<String, HashMap<String, HashSet<Integer>>>();//record the partition number of each topic that the client subscribes
		svrPartMap = new HashMap<String, HashMap<String, HashSet<Integer>>>();//record the partition number of each topic that the client subscribes
	}
	
	public Map<String, ArrayList<ArrayList<Record>>> getTopics() {
		return topics;
	}
	
	public Map<String, ArrayList<ArrayList<Record>>> getBkpTopics() {
		return bkpTopics;
	}
	
	public ArrayList<ServerInfo> getServers() {
		return servers;
	}
	
	public ArrayList<ClientInfo> getClients() {
		return clients;
	}
	
	public Map<String, Socket> getClientSockets() {
		return clientSockets;
	}
	
	public Map<String, Integer> getNumSub() {
		return numSub;
	}
	
	public Map<String, ArrayList<String>> getSubList() {
		return subList;
	}
	
	public Map<String, String[]> getPartSubMap() {
		return partSubMap;
	}
	
	public Map<String, String[]> getPartSvrMap() {
		return partSvrMap;
	}
	
	public Map<String, int[]> getNow() {
		return now;
	}
	
	public Map<String, int[]> getOffset() {
		return offset;
	}
	
	public HashMap<String, HashMap<String, HashSet<Integer>>> getCliSub() {
		return cliSub;
	}
	
	public HashMap<String, HashMap<String, HashSet<Integer>>> getSvrPartMap() {
		return svrPartMap;
	}
	
	public void setTopics(Map<String, ArrayList<ArrayList<Record>>> topics) {
		this.topics = topics;
	}
	
	public void setBkpTopics(Map<String, ArrayList<ArrayList<Record>>> bkpTopics) {
		this.bkpTopics = bkpTopics;
	}
	
	public void setNumSub(Map<String, Integer> numSub) {
		this.numSub = numSub;
	}
	
	public void setSubList(Map<String, ArrayList<String>> subList) {
		this.subList = subList;
	}
	
	public void setPartSubMap(Map<String, String[]> partSubMap) {
		this.partSubMap = partSubMap;
	}
	
	public void setPartSvrMap(Map<String, String[]> partSvrMap) {
		this.partSvrMap = partSvrMap;
	}
	
	public void setNow(Map<String, int[]> now) {
		this.now = now;
	}
	
	public void setOffset(Map<String, int[]> offset) {
		this.offset = offset;
	}
	
	public void setCliSub(HashMap<String, HashMap<String, HashSet<Integer>>> cliSub) {
		this.cliSub = cliSub;
	}
	
	public void setSvrPartMap(HashMap<String, HashMap<String, HashSet<Integer>>> svrPartMap) {
		this.svrPartMap = svrPartMap;
	}
}