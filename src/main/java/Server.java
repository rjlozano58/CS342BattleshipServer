// Rogelio Lozano, Pradyun Shrestha, Zakareah Hafeez
// CS 342 - Software Design - Prof. McCarthy
// Project 4: Battleship

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;

import javafx.util.Callback;
import javafx.util.Pair;
import sun.java2d.loops.DrawGlyphListAA;

// Create a pair
//Pair<String, String> pair = new Pair<>("Rogelio", "John");
public class Server{

	int count = 1;
	ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	ArrayList<Pair<String,String>> currentGames = new ArrayList<Pair<String,String>>();
	TheServer server;
	private Consumer<Serializable> callback;


	Server(Consumer<Serializable> call){

		callback = call;
		server = new TheServer();
		server.start();
	}


	public class TheServer extends Thread{



		public void run() {

			try(ServerSocket mysocket = new ServerSocket(5555);){
				System.out.println("Server is waiting for a client!");

				while(true) {

					ClientThread c = new ClientThread(mysocket.accept(), count); // blocking call, so doesnt create a new ClientTHread until a new client connects
					clients.add(c);
					c.start();

					count++;

				}
			}
			catch(Exception e) {
				callback.accept("Server socket did not launch");
			}
		}
	}


	class ClientThread extends Thread{


		Socket connection;
		int count;
		ObjectInputStream in;
		ObjectOutputStream out;

		String username = "Unknown";

		ClientThread(Socket s, int count){
			this.connection = s;
			this.count = count;
		}

		// Send a broadcast message to all clients on the server, sent in a Message Object
		public synchronized void updateClients(String message) {

			for(int i = 0; i < clients.size(); i++) {
				Message broadcastMessage = new Message("All", clients.get(i).username, message);
				ClientThread t = clients.get(i);
				try {
					t.out.writeObject(broadcastMessage);
				}
				catch(Exception e) {}
			}
		}

		// Sends 1-1 messages from a sender to the recipient. Sent as a Message object
		public void sendIndividualMessage(Message message){
			for(int i = 0; i < clients.size(); i++) {
				if (clients.get(i).username.equals(message.getRecipient())){
					ClientThread t = clients.get(i);
					try {
						t.out.writeObject(message);
					}
					catch(Exception e) {}
				}
			}
		}
		public synchronized void sendAttack(GameMessage attack){
			for(int i = 0; i < clients.size(); i++) {
				if (clients.get(i).username.equals(attack.getRecipient())){
					ClientThread t = clients.get(i);
					try {
						t.out.writeObject(attack);
						callback.accept( attack.getContent() + " at X: " + attack.getX() + " Y: " + attack.getY() + " was sent to " + attack.getRecipient() + " from " + attack.getSender());
					}
					catch(Exception e) {}
				}
			}
		}

		// Sends the clients an array of the current clients on the server
		public synchronized void sendClientArray(){
			// Assume 'username' is the username of the client to exclude
			for (int i = 0; i < clients.size(); i++) {
				ClientThread t = clients.get(i);
				ArrayList<String> clientsOnServer = new ArrayList<>();
				clientsOnServer.add("Computer");
				for (int j = 0; j < clients.size(); j++) {
					ClientThread client = clients.get(j);
					if (!client.username.equals(t.username)) {  // Compare usernames, not objects
						clientsOnServer.add(client.username);
						System.out.println("Adding: " + client.username);
					}
				}
				try {
					t.out.writeObject(clientsOnServer);
					System.out.println("Sent client list to: " + t.username);
				} catch (Exception e) {
					System.err.println("Error sending list of clients on server to " + t.username + ": " + e.getMessage());
				}
			}
		}

		public synchronized void handlePair(Pair<String,String> game){
			if (currentGames.isEmpty()){
				currentGames.add(game);
				callback.accept(game.getKey() + " is waiting for " + game.getValue() + " to press ready...");
			}else {
				for (Pair<String, String> currentGame : currentGames) {
					if (currentGame.getKey().equals(game.getKey()) || currentGame.getKey().equals(game.getValue()) || currentGame.getValue().equals(game.getKey()) || currentGame.getValue().equals(game.getValue())) {
						// Traverse through all client, find client with value and with key
						for (ClientThread client : clients) {
							if (client.username.equals(currentGame.getKey()) || client.username.equals(currentGame.getValue())) {
								GameMessage startMessage = new GameMessage("Server", client.username, "Start", 0, 0);
								try {
									client.out.writeObject(startMessage);
									callback.accept( startMessage.getRecipient() + " pressed Ready, Game will now Start!");
								} catch (Exception e) {
									System.err.println("Error sending game start message: " + e.getMessage());
								}
							}
						}
					} else {
						currentGames.add(game);
						callback.accept(game.getKey() + " is waiting for " + game.getValue() + " to press ready...");
					}
				}
			}


		}

		public synchronized void removeGame(String username) {
			Iterator<Pair<String, String>> iterator = currentGames.iterator();
			while (iterator.hasNext()) {
				Pair<String, String> game = iterator.next();
				if (game.getKey().equals(username) || game.getValue().equals(username)) {
					iterator.remove();
				}
			}
		}

		public void printCurrentGames() {
			System.out.println("Current Games:");
			for (Pair<String, String> game : currentGames) {
				System.out.println("Game between: " + game.getKey() + " and " + game.getValue());
			}
			if (currentGames.isEmpty()) {
				System.out.println("No current games.");
			}
		}


		public void run(){
			try {
				out = new ObjectOutputStream(connection.getOutputStream());
				in = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);

				// Get the username immediately after establishing the connection
				try {
					sendClientArray();
					Message clientUsername = (Message) in.readObject();
					username = clientUsername.getSender();
					System.out.println("New client has connected: " + username);
					callback.accept("New client has connected: " + username);
					sendClientArray();
				} catch (Exception e) {
					callback.accept("Could not receive username from client: " + count);
				}

				// Notify all clients about the new connection
				updateClients( username + " is online");

				// Handle further messages
				while(true) {
					try {
						Object receivedObject = in.readObject();

						if (receivedObject instanceof GameMessage){
							GameMessage cordMessage = (GameMessage) receivedObject;
							sendAttack(cordMessage);
							System.out.println("Received Attack/Result from " + cordMessage.getSender() + " to " + cordMessage.getRecipient() +  " : X: " + cordMessage.getX() + " Y: " + cordMessage.getY());
						}else if (receivedObject instanceof Pair){
							Pair<String,String> playersPair = (Pair<String,String>) receivedObject;
							callback.accept("Server has receive a request to play game between " + playersPair.getKey() + " and " + playersPair.getValue());
							handlePair(playersPair);
							printCurrentGames();
						}


					} catch(Exception e) {
						callback.accept(username + " has left the server.");
						updateClients("Client " + username + " has left the server!");
						clients.remove(this);
						removeGame(username);
						sendClientArray();
						break;
					}
				}
			} catch(Exception e) {
				System.out.println("Streams not open");
			}
		}//end of run


	}//end of client thread
}





	
	

	
