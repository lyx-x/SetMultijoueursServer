import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

	public static ServerSocket server;
	static int serverPort = 8888;
	public static LinkedList<LoopThread> clients = new LinkedList<LoopThread>();

	static HashSet<Integer> judgedCards = new HashSet<Integer>(81);
	static LinkedList<Integer> cards = new LinkedList<Integer>();
	
	static int numberCards = 12;
	public static int[] currentCards = new int[15];
	static int[] allViews = new int[15];
	static boolean haveSetCard = false;
	
	public static ExecutorService pool = Executors.newSingleThreadExecutor();
	
	public static ExecutorService check = Executors.newSingleThreadExecutor();

	public static void main(String[] args)
	{
		initCards();
		initViews();
		haveSetCard = true;
		try {
			server = new ServerSocket(serverPort);
			new FrozenThread().start();
			new ActiveThread().start();
			while (true)
			{
				Socket client = server.accept();
				System.out.println(client);
				LoopThread lp = new LoopThread(client,100);
				clients.add(lp);
				lp.start();
			}
		} 
		catch (Exception e) {
			System.out.println(e);
		}
		finally
		{
			try
			{
				server.close();
			}
			catch (Exception e)
			{
				System.out.println(e);
			}
		}
	}

	public static boolean judgeSubmission(int[] msg) {
		boolean ans = false;
		Future<Boolean> tmp = pool.submit(new Judge(msg));
		try {
			ans = tmp.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return ans;
	}
	
	public static void updateView(int[] views)
	{
		boolean[] l = {false, false, false};
		int count = 0;
		for(int i = 0 ; i < views.length; i++){
			if (views[i] >= 12){
				l[views[i] - 12] = true;
				count++;
			}
		}
		StringBuilder s = new StringBuilder();
		s.append('V');
		if (numberCards == 12 || count == 3){
			for (int i = 0 ; i < views.length ; i++)
			{
				s.append(' ');
				s.append(views[i]);
				s.append(' ');
				int card = 0;
				try
				{
					card = cards.poll();
				}
				catch (Exception e)
				{
					System.out.println("Another round");
					initCards();
					card = cards.poll();
				}
				currentCards[views[i]] = card;
				s.append(card);
				if(count == 3)
					numberCards = 15;
				
			}
		}else{		
			for(int i = 0 ; i < views.length ; i++){
				s.append(' ');
				s.append(views[i]);
				s.append(' ');
				if (views[i] >= 12){
					s.append(-1);
				}else{
					int k = 0;
					while(k < 3 && l[k]){
						k++;
					}
					if(k<3){
						s.append(currentCards[12 + k]);
						currentCards[views[i]] = currentCards[12 + k];
						l[k]=true;
					}else{
						System.out.println("set 15 cards wrong");
					}
					s.append(' ');
					s.append(12 + k);
					s.append(' ');
					s.append(-1);
				}
			}
			numberCards = 12;
		}
		String msg = s.toString();
		for (LoopThread lp : clients)
		{
			Socket client = lp.client;
			lp.frozen = false;
			System.out.println(client.toString() + msg);
			try {
				PrintWriter output = new PrintWriter(client.getOutputStream(),	true);
				output.println(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(numberCards == 12)
			checkSet();
	}
	
	public static void initCards(){
		int[][] temp = new int[81][2];
		Random rand = new Random();
		for(int i = 0 ; i < 81 ; i++){
			temp[i][0] = i;
			temp[i][1] = rand.nextInt(100);
		}
		for(int i = 0 ; i < 81 ; i++){
			for(int j = i + 1 ; j < 81 ; j++){
				if (temp[j][1] > temp[i][1]){
					int k = temp[i][0];
					temp[i][0] = temp[j][0];
					temp[j][0] = k;
					k = temp[i][1];
					temp[i][1] = temp[j][1];
					temp[j][1] = k;
				}
			}
		}
		for(int i = 0 ; i < 81 ; i++){
			boolean f = true;
			if(haveSetCard){
				for(int j = 0; j < numberCards; i++){
					if(temp[i][0] == currentCards[j]){
						f = false;
					}
				}
			}
			if (f)
				cards.add(temp[i][0]);
		}
	}
	
	public static void initViews(){
		for (int i = 0 ; i < numberCards ; i++)
		{
			currentCards[i] = cards.poll();
			allViews[i] = i;
		}
		checkSet();
	}
	
	public static void checkSet(){
		boolean Possible = false;
		Future<Boolean> tmp = check.submit(new Check());
		try {
			Possible = tmp.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		if (!Possible)
		{
			System.out.println("No set");
			int[] views = {12,13,14};
			updateView(views);
		}
	}

	public static void restartGame(){
		initCards();
		initViews();
		updateView(Server.allViews);
	}

	public static boolean isSet(int a, int b, int c) {
		int[][] set = new int[3][4];
		for (int i = 0 ; i < 4 ; i++)
		{
			set[0][i] = a % 3;
			a /= 3;
			set[1][i] = b % 3;
			b /= 3;
			set[2][i] = c % 3;
			c /= 3;
		}
		for (int i = 0 ; i < 4 ; i++)
		{
			int x, y, z;
            x = set[0][i];
            y = set[1][i];
            z = set[2][i];
            if (!((x == y && y == z && z == x) || (x != y && y != z && z != x)))
            	return false;
		}
		return true;
	}
	

}


class Judge implements Callable<Boolean>{

	int[] set;

	public Judge (int[] s)
	{
		set = new int[3];
		for (int i = 0 ; i < 3 ; i++)
			set[i] = s[i];
	}

	@Override
	public Boolean call() throws Exception {
		for (int i : set)
		{
			if (Server.judgedCards.contains(i))
				return false;
		}
		for (int i : set)
			Server.judgedCards.add(i);
		return true;
	}
}

class Check implements Callable<Boolean>{

	@Override
	public Boolean call() throws Exception {
		for (int i = 0 ; i < Server.numberCards - 2 ; i++)
			for (int j = i + 1 ; j < Server.numberCards - 1 ; j++)
				for (int k = j + 1 ; k < Server.numberCards ; k++)
					if (Server.isSet(Server.currentCards[i] , Server.currentCards[j] , Server.currentCards[k]))
					{
						System.out.format("Set: %d %d %d\n", i , j , k);
						return true;
					}
		return false;
	}
}

class FrozenThread extends Thread{
	public void run()
	{
		while (true){
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			boolean f = true;
			for(LoopThread lp : Server.clients){
				if (!lp.frozen)
					f = false;
			}
			if(f){
				for (LoopThread lp : Server.clients)
				{
					Socket client = lp.client;
					System.out.println(client.toString() + "M");
					try {
						PrintWriter output = new PrintWriter(client.getOutputStream(),	true);
						output.println("M");
						lp.frozen = false;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
		}
	}
}

class ActiveThread extends Thread{
	public void run(){
		while(true){
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for(LoopThread lp : Server.clients){
				if (!lp.active){
					lp.alive = false;
				}else{
					lp.active = false;
				}
			}
		}
	}
}

class LoopThread extends Thread{

	long time = 0;
	Socket client = null;
	boolean frozen = false;
	boolean active = false;
	boolean alive = true;
	
	public LoopThread(Socket s, long t)
	{
		this.time = t;
		this.client = s;
	}

	public void run()
	{
		BufferedReader input = null;
		PrintWriter output = null;
		try {
			output = new PrintWriter(client.getOutputStream(),	true);
			initGame(output);
			while (true) {
				if(!alive){
					endMessage(output);
					input.close();
					output.close();
					Server.clients.remove(this);
					client.close();
					return;
				}
				try {
					Thread.sleep(time);
					input = new BufferedReader(new InputStreamReader(client.getInputStream()));
					output = new PrintWriter(client.getOutputStream(),	true);
					while (input.ready())
					{
						active=true;
						char task = (char)input.read();
						input.read();
						switch (task)
						{
						case 'F':
							this.frozen = true;
							break;
						case 'R':
							Server.restartGame();
							break;
						case 'S':
							String msg = input.readLine();
							System.out.println("Reveive: " + msg);
							int[] set = new int[3];
							int[] views = new int[3];
							String[] s = msg.split(" ");
							for (int i = 0 ; i < 3 ; i++)
							{
								set[i] = Integer.parseInt(s[2 * i + 1]);
								views[i] = Integer.parseInt(s[2 * i]);
							}
							boolean valid = Server.judgeSubmission(set);
							if (valid)
							{
								updateScore(output, set);
								Server.updateView(views);
							}
							break;
						case 'E':
							endMessage(output);
							try {
								input.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							output.close();
							Server.clients.remove(client);
							System.out.println(Server.clients.size());
							client.close();
							return;
						}
					}
				} catch (Exception e) {
					System.out.println(e);
					e.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			System.out.println(e);
			e.printStackTrace();
		}
		finally
		{
			try {
				input.close();
				System.out.println("Connection shutdown");
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			output.close();
		}
	}

	void endMessage(PrintWriter output) throws IOException
	{
		output.println("E");
		System.out.println('E');
	}

	void updateScore(PrintWriter output, int[] set)
	{
		StringBuilder s = new StringBuilder();
		s.append('S');
		for (int i = 0 ; i < 3 ; i++)
		{
			s.append(' ');
			s.append(set[i]);
		}
		output.println(s.toString());
		System.out.println(s.toString());
	}

	void initGame(PrintWriter output)
	{
		StringBuilder s = new StringBuilder();
		s.append('V');
		for (int i = 0 ; i < Server.numberCards ; i++)
		{
			s.append(' ');
			s.append(i);
			s.append(' ');
			s.append(Server.currentCards[i]);
		}
		String msg = s.toString();
		try {
			output = new PrintWriter(client.getOutputStream(),	true);
			output.println(msg);
			System.out.println(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}