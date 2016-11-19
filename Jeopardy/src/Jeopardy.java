//Went through and commented as well as possible. It will still take a
//little playing around if you don't understand how the input/output
//streams work. Just remember this is a Runnable and it is working
//as a processor thread. So everything is looping and waiting for
//an event. The event for this game is a mouse click. If we are
//allowing the mouse click to happen (yourTurn = true) then it
//will continue to cycle until it sees the mouse click to trigger
//the defined event. If you set both players to yourTurn = true and
//create a boolean value to enter a question phase it will listen
//for a mouse click on both player's windows. You will have to
//create something to indicate the other player has buzzed in.
//then use the tick() or another new method called in the run() 
//method to deactivate the board (both players set yourTurn to
//false) and pop-up the answer choice box on the person that
//buzzed first.

//Suggestion for the pop-up dialog for the answer:
//http://docs.oracle.com/javase/tutorial/uiswing/components/dialog.html

//Items that still need to be added for the game to function:

//1. Add a Question Phase using a method that will display a question and
//set both players turn to true (boolean value of yourTurn). 
//This will allow both players to click the mouse on the game board.
//I've created a boolean variable called questionPhase. You can
//add an if statement that will allow for clicking on the buzzer
//if it is in the questionPhase and pop-up a window with the 4
//options for the answer.

//2. The score needs to be added to the person that answers the question.
//The board square is inactive once a square has the value of 0. So when
//you click on a square it sets that referenced array value to 0. You will
//need to add an integer variable to store the current question score before
//that array location is set to 0. The person that gets the question right
//would obviously need to have the question score added to their score.

//3. Rounds should be counted down after the question phase.

//4. A timer needs to be added when the question displays. It should count
//down from 10 seconds. When the timer reaches 0 before someone buzzes in
//it shouldn't count that as a round and it will allow the same person
//that selected the previous question to ask another question.
//This was purposely added last because it is not a requirement
//but will make the game a bit nicer.

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Jeopardy implements Runnable {

	//Network information variables to open socket
	private String ip = "localhost";
	private int port = 123;
	
	private Scanner scanner = new Scanner(System.in);
	
	//This is setting the container frame size.
	//Used 800 by 600. The added 6 to the width is for the outline of the window
	//the 27 on the height is for the top bar and frame.
	private JFrame frame;
	private final int WIDTH = 806;
	private final int HEIGHT = 627;
	
	//Thread is used to feed items into process thread
	//Items are processed in order of priority
	private Thread thread;

	//Painter is used to "paint" graphics on the screen
	private Painter painter;
	
	//Socket used for the TCP network socket
	private Socket socket;
	
	//This is the datastream for input and output
	//it will allow the server and client to talk to each other
	private DataOutputStream dos;
	private DataInputStream dis;

	//Socket used for TCP listen socket
	private ServerSocket serverSocket;

	//Define the variables for the images being used
	private BufferedImage board;
	private BufferedImage blank;

	//The point system and positions for the game board.
	//Because the first row is the categories we don't
	//want them to be available for clicking. So we set
	//those squares to 0 points to keep them inactive.
	private int[] spaces = { 0, 0, 0, 0, 0,
			100, 100, 100, 100, 100,
			200, 200, 200, 200, 200,
			300, 300, 300, 300, 300,
			400, 400, 400, 400, 400,
			500, 500, 500, 500, 500};

	//Set to true to activate the board
	private boolean yourTurn = false;
	
	//
	private boolean server = true;
	
	//value to make sure the connection is accepted
	//between client and server.
	private boolean accepted = false;
	
	//Set to true if unable to communicate with partner.
	private boolean unableToCommunicateWithOpponent = false;
	
	private boolean won = false;
	private boolean enemyWon = false;
	private boolean tie = false;

	//gridWidth and gridHeight used to set the width and height of a square.
	private int gridWidth = 154;
	private int gridHeight = 78;
	
	//Count tcp-ip errors
	private int errors = 0;
	
	//Set rounds to desired round number
	private int rounds = 5;
	
	//Point system
	private int yourPoints = 200;
	private int enemyPoints = 100;

	//Fonts to be used on the game. Can add more if needed using
	//a similar format.
	private Font font = new Font("Verdana", Font.BOLD, 32);
	private Font smallerFont = new Font("Verdana", Font.BOLD, 20);
	private Font largerFont = new Font("Verdana", Font.BOLD, 50);

	//Defined strings.
	private String waitingString = "Waiting for another player";
	private String unableToCommunicateWithOpponentString = "Unable to communicate with opponent.";
	private String wonString = "You won!";
	private String enemyWonString = "Other Player won!";
	private String tieString = "Game ended in a tie.";
	
	//Categories, these must be short enough to work on a single line.
	//Return chars aren't allowed in the drawString method.
	private String[] categories = { "\"S\" words", "Condiments", "Cover Me", "Food", "Flowers" };

	public Jeopardy() {
		System.out.println("Please input the IP: ");
		ip = scanner.nextLine();
		System.out.println("Please input the port: ");
		port = scanner.nextInt();
		while (port < 1 || port > 65535) {
			System.out.println("The port you entered was invalid, please input another port: ");
			port = scanner.nextInt();
		}

		loadImages();

		//Initialize the window and preferred size.
		painter = new Painter();
		painter.setPreferredSize(new Dimension(WIDTH, HEIGHT));

		//If we can't connect to the server at the given address
		//we will become the server
		if (!connect()) initializeServer();

		//Create a JFrame with properties for our Jeopardy game
		frame = new JFrame();
		frame.setTitle("Jeopardy");
		frame.setContentPane(painter);
		frame.setSize(WIDTH, HEIGHT);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setVisible(true);

		//Start our process thread
		thread = new Thread(this, "Jeopardy");
		thread.start();
	}

	//This is the main loop for the program.
	//Everything revolves around the repaint and
	//tick methods.
	public void run() {
		while (true) {
			tick();
			painter.repaint();

			if (!server && !accepted) {
				listenForServerRequest();
			}

		}
	}

	private void render(Graphics g) {
		
		//Draw the game board
		g.drawImage(board, 0, 0, null);
		
		//If communication with the person has a problem
		//print the unable to communicate string
		if (unableToCommunicateWithOpponent) {
			g.setColor(Color.RED);
			g.setFont(smallerFont);
			Graphics2D g2 = (Graphics2D) g;
			int stringWidth = g2.getFontMetrics().stringWidth(unableToCommunicateWithOpponentString);
			g.drawString(unableToCommunicateWithOpponentString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
			return;
		}

		//If the connection is properly established
		if (accepted) {
			
			//Print the categories on the board
			for (int i = 0; i < 5; i++){
				Graphics2D g2 = (Graphics2D) g;
				g.setColor(Color.WHITE);
				g.setFont(smallerFont);
				int stringWidth = g2.getFontMetrics().stringWidth(categories[i]);
				g.drawString(categories[i], 80 + (i % 5) * gridWidth + 5 * (i % 5) - (stringWidth/2), (int) 45 + (i / 5) * gridHeight + 5 * (int) (i / 5));
			}
			
			//When the board array has a 0 in the index we place a blue
			//square over the score. We only do this for items under the
			//category row.
			for (int i = 5; i < spaces.length; i++) {
				if (spaces[i] < 100) {
					g.drawImage(blank, 15 + (i % 5) * gridWidth + 5 * (i % 5), (int) 15 + (i / 5) * gridHeight + 5 * (int) (i / 5), null);
				}
			}
			
			//Print the lower score red.
			if(yourPoints < enemyPoints){
				g.setColor(Color.RED);
				g.setFont(font);
				g.drawString(Integer.toString(yourPoints), 275, 570 );
				
				g.setColor(Color.WHITE);
				g.drawString(Integer.toString(enemyPoints), 450, 570 );
			}
			else if(yourPoints > enemyPoints){
				g.setColor(Color.WHITE);
				g.setFont(font);
				g.drawString(Integer.toString(yourPoints), 275, 570 );
				
				g.setColor(Color.RED);
				g.drawString(Integer.toString(enemyPoints), 450, 570 );				
			}
			else {
				g.setColor(Color.WHITE);
				g.setFont(font);
				g.drawString(Integer.toString(yourPoints), 275, 570 );
				
				g.setColor(Color.WHITE);
				g.drawString(Integer.toString(enemyPoints), 450, 570 );				
			}
			
			//Check if there is a winner.
			if (won || enemyWon) {
				Graphics2D g2 = (Graphics2D) g;
				g.setColor(Color.RED);
				g.setFont(largerFont);
				if (won) {
					int stringWidth = g2.getFontMetrics().stringWidth(wonString);
					g.drawString(wonString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
				} 
				else if (enemyWon) {
					int stringWidth = g2.getFontMetrics().stringWidth(enemyWonString);
					g.drawString(enemyWonString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
				}
			}
			if (tie) {
				Graphics2D g2 = (Graphics2D) g;
				g.setColor(Color.BLACK);
				g.setFont(largerFont);
				int stringWidth = g2.getFontMetrics().stringWidth(tieString);
				g.drawString(tieString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
			}
		} 
		
		//If the connection isn't accepted yet we print
		//that we are waiting for a partner.
		else {
			g.setColor(Color.RED);
			g.setFont(font);
			Graphics2D g2 = (Graphics2D) g;
			int stringWidth = g2.getFontMetrics().stringWidth(waitingString);
			g.drawString(waitingString, WIDTH / 2 - stringWidth / 2, HEIGHT / 2);
		}

	}

	private void tick() {
		
		//Check to see if we can't communicate with the partner
		if (errors >= 10) unableToCommunicateWithOpponent = true;

		//
		if (!yourTurn && !unableToCommunicateWithOpponent) {
			try {
				
				//dis is our input stream. We read the integer that
				//is fed from the other player for which square on
				//the board is set to 0.
				//the readInt() method will throw an IOException if
				//there is an error with communication so we use a
				//try/catch block
				int space = dis.readInt();
				spaces[space] = 0;
				
				//Check to see if there is a winner
				checkForWinner();
				
				//Can change this depending on who will have a turn next
				yourTurn = true;
				
			} catch (IOException e) {
				//Catch the exception and add to the errors
				e.printStackTrace();
				errors++;
			}
		}
	}

	//Can use this after rounds count to 0
	//To check for winner
	private void checkForWinner() {
		if(rounds == 0){
			if(yourPoints < enemyPoints){
				enemyWon = true;
			}
			else if(yourPoints > enemyPoints){		
				won = true;
			}
			else{
				tie = true;
			}	
		}
	}

	//Wait for a client then open data streams with
	//the client.
	private void listenForServerRequest() {
		Socket socket = null;
		try {
			socket = serverSocket.accept();
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
			System.out.println("Accepted Client's Join Request.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//Check to see if we can connect to a server using the ip/port.
	//If we can, we open the proper data streams with the server.
	//If we can't connect to someone return false. 
	private boolean connect() {
		try {
			socket = new Socket(ip, port);
			dos = new DataOutputStream(socket.getOutputStream());
			dis = new DataInputStream(socket.getInputStream());
			accepted = true;
		} catch (IOException e) {
			System.out.println("Unable to connect to the address: " + ip + ":" + port + " | Starting a server");
			return false;
		}
		System.out.println("Successfully connected to the server.");
		return true;
	}

	//Setup the server and make it the server's turn to begin with.
	private void initializeServer() {
		try {
			serverSocket = new ServerSocket(port, 8, InetAddress.getByName(ip));
		} catch (Exception e) {
			e.printStackTrace();
		}
		yourTurn = true;
		server = false;
	}

	//Load the images for later use.
	//Board is for the game board, blank is for the
	//square that covers the selected point box.
	private void loadImages() {
		try {
			board = ImageIO.read(getClass().getResourceAsStream("/board.png"));
			blank = ImageIO.read(getClass().getResourceAsStream("/blank.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//Add a suppress warnings to unused because we never
	//actually use the created Jeopardy variable.
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		Jeopardy jeopardy = new Jeopardy();
	}

	//Create a mouse listener
	private class Painter extends JPanel implements MouseListener {
		private static final long serialVersionUID = 1L;

		public Painter() {
			setFocusable(true);
			requestFocus();
			setBackground(Color.BLUE);
			addMouseListener(this);
		}

		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			render(g);
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (accepted) {
				//To make a question phase you would add an if statement here
				//Currently it will only listen to mouse clicked events if it's your
				//turn.
				if (yourTurn && !unableToCommunicateWithOpponent && !won && !enemyWon) {
					
					//We pull the X and Y values from the pixel breakdown
					//of the game board. Because we have our set square sizes
					//We can use those values to find out where the mouse click
					//happened.
					int x = e.getX() / (gridWidth + 5);
					int y = e.getY() / (gridHeight + 5);
					
					//Multiply the y value by 5 because were using a 1D array.
					//For example this will make row 3 column 3 = 3 + (3 * 5).
					//The index for 3, 3 would be 18 which is correct.
					y *= 5;
					int position = x + y;
					
					//Buzzer is at position 30 or 0,6
					//You can create another if statement that will check for a 
					//questionPhase being true and find out who clicks first.
					
					
					if(position < 30){
						if (spaces[position] > 0) {
							spaces[position] = 0;
							
							//Currently sets the turn to false to swap to the
							//other player after picking an item from the board
							yourTurn = false;
							repaint();
							
							Toolkit.getDefaultToolkit().sync();
	
							try {
								//Send the position chosen to the
								//other player to sync the game board.
								dos.writeInt(position);
								dos.flush();
							} catch (IOException e1) {
								errors++;
								e1.printStackTrace();
							}
	
						}
					}
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {

		}

		@Override
		public void mouseReleased(MouseEvent e) {

		}

		@Override
		public void mouseEntered(MouseEvent e) {

		}

		@Override
		public void mouseExited(MouseEvent e) {

		}

	}

}
