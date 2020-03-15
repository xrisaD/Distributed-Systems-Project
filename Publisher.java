import javax.management.RuntimeErrorException;
import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Scanner;


public class Publisher extends Node implements Serializable {

	private String ip;
	private int port;

	private Hashtable<ArtistName, ArrayList<Value>> artistToValue;

	public void getBrokerList() { }

	public Broker hashTopic(ArtistName artist) { return null; }

	public void push(ArtistName artist,Value value) { }

	public void notifyFailure(Broker broker) { }
	
	//constructor
	public Publisher(String ip, int port){
		this.ip = ip;
		this.port = port;
		//Read file with artists and music file info
		//initialize HashTable
	}
	//Adds artists with no songs
	public Publisher(String ip, int port, String fileName , ArrayList<String> artists){
		this(ip , port);
		for(String artist : artists)
		{
			artistToValue.put(new ArtistName(artist) , null);
		}

	}
	//Getters setters
	public Hashtable<ArtistName, ArrayList<Value>> getArtistToValue(){
		return artistToValue;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Server starts for Brokers
	 */
	public void startServer() {
		ServerSocket providerSocket = null;
		Socket connection = null;
		try {
			providerSocket = new ServerSocket(this.port, 10);
			while (true) {
				System.out.println("Publisher listening on port " + getPort());
				connection = providerSocket.accept();
				//We start a thread

				//this thread will do the communication


			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				providerSocket.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}
	}

	/**
	 * 	Connects to the broker on @param ip and @param port and sends the publisher object through the socket
	 */
	public void notifyBroker(String ip , int port){
	    System.out.printf("Publisher(%s,%d) notifying Broker(%s,%d)\n" , getIp(),getPort() , ip , port);
		Socket socket = null;
		ObjectOutputStream out = null;
		try{
			//Connecting to the broker
			System.out.printf("[PUBLISHER %d] Connecting to broker on port %d , ip %s%n" , getPort() , port , ip);
			socket = new Socket(ip,port);
			System.out.printf("[PUBLISHER %d] Connected to broker on port %d , ip %s%n" ,getPort() , port , ip);
			out = new ObjectOutputStream(socket.getOutputStream());
			//Creating notify message
			String message = String.format("notify %s %d" , getIp() , getPort());
			for(ArtistName name : artistToValue.keySet()){
				message += name.getArtistName();
			}
			out.writeObject(message);
		}
		catch(Exception e){
			e.printStackTrace();
			System.out.println(e.getMessage());
			System.out.printf("[PUBLISHER %d] Failure on notifybroker Broker(ip = %s port = %d  %n)" , getPort() , ip , port);
		}
		finally{
			try {
				//socket.close();
				out.close();
			}
			catch(Exception e){
				System.out.println("Error while closing streams");
				throw new RuntimeException(e);
			}
		}

	}
	public static void main(String[] args){
		try{
			Publisher p = new Publisher(args[0],Integer.parseInt(args[1]));
			//p.startServer();


			File myObj = new File(args[2]);
			Scanner myReader = new Scanner(myObj);
			while (myReader.hasNextLine()) {
				//Parsing a broker
				String data = myReader.nextLine();
				String[] arrOfStr = data.split("\\s");
				String ip = arrOfStr[0];
				int port = Integer.parseInt(arrOfStr[1]);
				int hashValue = Integer.parseInt(arrOfStr[2]);

				p.notifyBroker(ip , port);
			}

		}catch (Exception e) {
			System.out.println("Usage: java Publisher ip port");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public class PublisherHandler extends Thread{
		Socket socket;
		public PublisherHandler(Socket socket){
			this.socket = socket;
		}
		@Override
		public void run(){ //Protocol
			ObjectInputStream in = null;
			ObjectOutputStream out = null;
			try{
				//Take Broker's request
				//Broker's request is a ArtistName

				ArtistName aName = null;
				aName= (ArtistName) in.readObject();

				//Response to Broker' request for an Artist



			}catch (ClassNotFoundException c) {
				System.out.println("Class not found");
				c.printStackTrace();
				return;
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}

		}
	}
}
