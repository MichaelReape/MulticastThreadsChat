import java.util.Scanner;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MulticastThreadsChat {

	private String username;
	private InetAddress address;
	private NetworkInterface netif;
	private MulticastSocket socket;
	private InetSocketAddress groupaddr;
	private byte[] outBuffer;
	private byte[] inBuffer;
	private byte[] statBuffer;
	private DatagramPacket outPacket;
	private DatagramPacket inPacket;
	private DatagramPacket statPacket;
	private Scanner sc;
	
	private ReadWriteLock rwlock = new ReentrantReadWriteLock();
	private final ArrayList <String> chatHistory = new ArrayList<String> ();
	private final Map <String, Long> lastSeen = new HashMap<>();
	
	private volatile String mode;	
	private volatile boolean isRunning = true;
	
	private static final int PORT = 8888;
	private static final int TICK_RATE = 10000;
	private static final long ONLINE_TIMEOUT = 20000;
	private static final int REFRESH_INTERVAL = 10000;

	public static void main(String[] args) {
		MulticastThreadsChat mtc = new MulticastThreadsChat();
		mtc.login();	
		
		//start the listener thread
		Thread recThread = new Thread(new Runnable() {
	           public void run() {
	               mtc.receiver();
	            }
	        });
		   recThread.start();
		   
		   //start the send thread
		   Thread sendThread = new Thread(new Runnable() {
			  public void run() {
	        	   mtc.sender();
			  }
	       });
		   sendThread.start();
		   
		   //start the thread to send the online ticks
		   Thread stThread = new Thread(new Runnable() {
	           public void run() {
	               mtc.status();
	            }
	        });
		   stThread.start();
		   
		   //start the thread to check the online members
		   Thread onThread = new Thread(new Runnable() {
			   public void run() {
				   mtc.online();
			   }
		   });
		   onThread.start();
	}
	
	//login method
	private void login() {
		System.out.println("Enter username: ");
		sc = new Scanner(System.in);
		String temp;
		
		//ensures user enters non-empty username
		do {
			temp = sc.nextLine();
			if(temp.isEmpty()) {
				System.out.println("Please enter a valid username");
			}
		}while(temp.isEmpty());
		
		username = temp;
		System.out.println(username + " logged in!");
		
		//mode used for switching between chatting and dynamic online friends list in console
		mode = "chat";
		
		//join the multicast 
		try {
			address = InetAddress.getByName("224.2.2.3");
			socket = new MulticastSocket(PORT);
			netif = NetworkInterface.getByInetAddress(address);
			groupaddr = new InetSocketAddress(address, PORT);
			socket.joinGroup(groupaddr, netif);
		}catch( IOException ioe ) {
			System.out.println(ioe);
		}
	}
	
	//listener method 
	private void receiver() {
		//infinite loop to receive messages
		while(isRunning) {
			//initialise the buffer for receiving bytes and the datagram packet
			inBuffer = new byte[256];
			inPacket = new DatagramPacket(inBuffer, inBuffer.length);
			try {
				//receive the packets
				socket.receive(inPacket);	
				String text = new String(inBuffer, 0, inPacket.getLength());
				//separate the user messages from the online ticks to keep track of status
				if(text.startsWith("(tick)")){
					//grab the username encoded in the tick message
					String tick = text.substring(7);
					//add the time stamp and user to map
					rwlock.writeLock().lock();
					lastSeen.put(tick, System.currentTimeMillis());
					rwlock.writeLock().unlock();
					continue;
				}
				else if(!text.startsWith(username) && mode.equals("chat")) {
					//prints the message from users to the console 
					System.out.println(text);
				}
				//adds messages to chat history list
				//locks for writing to array list
				rwlock.writeLock().lock();
				chatHistory.add(text);
				rwlock.writeLock().unlock();
			}catch(IOException ioe) {
				System.out.println(ioe);
			}
		}
	}
	
	//method to send messages
	private void sender() {
		while(isRunning){
			String input = sc.nextLine();
			//some functionality for the user, can navigate modes using /command 
			// /quit to exit application
			if(input.equals("/quit")) {
				try {
				socket.leaveGroup(groupaddr, netif);
				socket.close();
				//put this in the infinite loops to stop exceptions when socket closed 
				isRunning = false;
				System.exit(0);
				}catch(IOException ioe) {
					System.out.print(ioe);
				}
			}else if(input.equals("/online")) {
				//displays dynamic list of online users 
				clearConsole();
				printOnline();
				mode = "online";
				continue;
			}else if(input.equals("/history")) {
				//displays static list of chat history
				clearConsole();
				System.out.println("Chat History");
				System.out.println("=====================");
				printHistory();
				System.out.println("=====================");
			}else if(input.equals("/chat")){
				//the default chat mode, user can navigate back to after checking online users with /chat
				mode = "chat";
				clearConsole();
				printHistory();
				continue;
			}else if(!input.isEmpty() && mode.equals("chat")) {
				//actually sending a message 
				try {
					//prepend username to message and send 
					String message = username + " : " + input;
					outBuffer = message.getBytes();
					outPacket = new DatagramPacket(outBuffer, outBuffer.length, address, PORT);
					socket.send(outPacket);	
				}catch(IOException ioe) {
					System.out.println(ioe);
				}
			}else {	
				continue;
			}
		}
	}
	
	//method to send tick letting other users know you are available 
	private void status() {
		while(isRunning) {
			try {
				String tick = "(tick) " + username;
				statBuffer = tick.getBytes();
				statPacket = new DatagramPacket(statBuffer, statBuffer.length, address, PORT);
				socket.send(statPacket);
				//sends at a pre defined rate
				Thread.sleep(TICK_RATE);
			}catch(Exception e) {
					System.out.println(e);
			}
		} 
	}
	
	//method to display the dynamic online user list
	private void online() {
		while(isRunning) {
			if(mode.equals("online")) {
				clearConsole();
				printOnline();
			}
			try {	
				//refreshes the list to the console at a defined interval
				Thread.sleep(REFRESH_INTERVAL);
			}catch(Exception e) {
				System.out.println(e);
			}
		}
	}
	
	//prints the chat history 
	private void printHistory() {
		rwlock.readLock().lock();
		for(int i = 0; i < chatHistory.size(); i++) {
			System.out.println(chatHistory.get(i));
		}
		rwlock.readLock().unlock();
	}
	
	//clears the text displayed in the console 
	private void clearConsole() {
		System.out.print("\033[H\033[2J");
		System.out.flush();
	}
	
	//prints the users (keys) in the map if their last known time is less than the timeout value
	private void printOnline() {
		System.out.println("=========Available Users=========");
		long currentTime = System.currentTimeMillis();
		rwlock.readLock().lock();
		for(Map.Entry<String, Long> entry : lastSeen.entrySet()) {
			String user = entry.getKey();
			long timeStamp = entry.getValue();
			if(currentTime - timeStamp <= ONLINE_TIMEOUT) {
				System.out.println(user);
			}
		}
		rwlock.readLock().unlock();
	}

}
