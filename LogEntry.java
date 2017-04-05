//data structure to represent
public class LogEntry {
	//instance variables
	int currTerm;
	String clientName;
	int cmdIndex;
	String cmd;
	
	//constructor
	public LogEntry(int currTerm, String clientName, int cmdIndex, String cmd) {
		this.currTerm = currTerm;
		this.clientName = clientName;
		this.cmdIndex = cmdIndex;
		this.cmd = cmd;
	}
	
	//print method
	public void printLogEntry() {
		System.out.println(currTerm+" "+clientName+" "+cmdIndex+" "+cmd);
	}
	
	//instance methods
	int getTerm() {
		return currTerm;
	}
	
	String getCltName() {
		return clientName;
	}
	
	int getCmdIndex() {
		return cmdIndex;
	}
	
	String getCmd() {
		return cmd;
	}
}
