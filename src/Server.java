import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

	public static ServerSocket server;
	static int serverPort = 8888;
	static LinkedList<Socket> clients = new LinkedList<Socket>();

	static HashSet<Integer> judgedCards = new HashSet<Integer>(81);
	static LinkedList<Integer> cards = new LinkedList<Integer>();
	

	static final int numberCards = 15;
	public static int[] currentCards = new int[numberCards];
	static int[] allViews = new int[Server.numberCards];
	
	public static ExecutorService pool = Executors.newSingleThreadExecutor();

	public static void main(String[] args)
	{
		initCards();
		try {
			server = new ServerSocket(serverPort);
			while (true)
			{
				Socket client = server.accept();
				System.out.println(client);
				clients.add(client);
				new LoopThread(client, 100).start();
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
		StringBuilder s = new StringBuilder();
		s.append('V');
		for (int i = 0 ; i < views.length ; i++)
		{
			s.append(' ');
			s.append(views[i]);
			s.append(' ');
			int card = cards.poll();
			currentCards[views[i]] = card;
			s.append(card);
		}
		String msg = s.toString();
		System.out.println(msg);
		for (Socket client : clients)
		{
			try {
				PrintWriter output = new PrintWriter(client.getOutputStream(),	true);
				output.println(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
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
			cards.add(temp[i][0]);
		}
		for (int i = 0 ; i < numberCards ; i++)
		{
			currentCards[i] = cards.poll();
			allViews[i] = i;
		}
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
		return true;
	}
}

class LoopThread extends Thread{

	long time = 0;
	Socket client = null;

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
				try {
					Thread.sleep(time);
					input = new BufferedReader(new InputStreamReader(client.getInputStream()));
					output = new PrintWriter(client.getOutputStream(),	true);
					while (input.ready())
					{
						char task = (char)input.read();
						input.read();
						switch (task)
						{
						case 'R':
							restartGame(output);
							break;
						case 'S':
							String msg = input.readLine();
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
		output.println('E');
		System.out.println('E');
		client.close();
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
	
	void restartGame(PrintWriter output)
	{
		Server.initCards();
		Server.updateView(Server.allViews);
	}
}