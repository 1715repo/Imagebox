/**
 * Description
 * 		Simple experimental File Transfer protocol 
 * 		Server contains a list of files(master list) that can be accessed by clients 
 * 		Each client contains a copy of the master list, and can instruct the server to add/remove files from the master list
 * 		Changes in the master list will reflect changes in the client list upon reconnection
 * 
 * Protocol
 * 		1. Client establishes connection
 * 		2. Client sends an instruction and its dependencies to the server via Package class 
 * 		3. Server attempts to execute instruction, sends response Package with success/failure flag set along with dependencies 
 * 		
 * 		Syncing
 * 		1.	Client sends its file list to the server
 * 		2.  Server takes note of all (if any) differences and sends back an array of Packages, with each Package containing an instruction + dependencies for the client to process
 * 
 * Server.java listens on port 4567 for incoming client connections. 
 * Handles each connection on a separate thread
 * Package.java is the POD standard in this model
 *
 */

package com.example.sergiy.imagebox;

import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Server implements Runnable
{
	private Socket client;
	private static Map<String, String> files; //associates each file name with an owner/client (ip address). deleting files from the server requires ownership
	private InstructionExec[] instructions;
	private ObjectInputStream pkgIn;
	private ObjectOutputStream pkgOut;
	
	public Server(Socket c) //constructor in creating a new thread, client connection passed from the main thread
	{
		client = c;
	}
	
	public void init() //Creates stream objects for each unique Server-Client pair
	{
		try
		{
			pkgIn = new ObjectInputStream(client.getInputStream());
			pkgOut = new ObjectOutputStream(client.getOutputStream());
			
			//each Package that can be received by server contains an instruction flag
			//each instruction has a corresponding class which implements the InstructionExec interface
			instructions = new InstructionExec[3];
			instructions[0] = new Sync();
			instructions[1] = new Add();
			instructions[2] = new Delete();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		try
		{
			ServerSocket server = new ServerSocket(4567);
			files = new HashMap<String, String>();
			
			files.put("dust.jpg", "127.0.0.1");
			
			while(true) //continuously listen for incoming connections
			{
				try
				{
					Socket clientSocket = server.accept();
					System.out.println("accepting");
					new Thread(new Server(clientSocket)).start(); //instantiates Server class, passing the established client connection, and starting a new thread
					
				}
				catch(Exception e)
				{
					e.printStackTrace();
					server.close();
				}
				
			}
		
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void run() //thread entry point
	{
		System.out.println("Started new thread");
		
		init();
		boolean running = true;
		while(running)
		{
			try
			{
				Package p = (Package)pkgIn.readObject();
				System.out.println("Recieved pkg");
				//executes instruction received from Client. Specific int code that maps to instruction object array
				//all array objects contain the execute method
				instructions[p.instruction].execute(p); 
				
			}
			catch(Exception e)
			{
				//e.printStackTrace();
				running = false;
			}	
		}
		
		try
		{
			client.close();
			System.out.println("Closed thread");
			return;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
//Instruction interface and class defenitions----------------------------------------------------------------------------------------------------------------------------------

	public interface InstructionExec
	{
		void execute(Package p); //to be overwritten by child classes
	}
	
	
	public class Sync implements InstructionExec
	{
		
		/*
	 	Runs the file list provided by client against the Server's master file list
	 	Server sends missing files to client
	 	If the client list has files that are no longer on the master list, server sends instructions to delete those files (can be ignored client-side, obviously)
		*/
		
		public void execute(Package p)
		{
			ArrayList<Package> clientBuff = new ArrayList<Package>();
			String[] fileList;
			synchronized(files)
			{
				fileList = files.keySet().toArray(new String[files.size()]);
			}
			
			String clientListRaw = p.name;
//			System.out.println(clientListRaw);
			String[] clientList = clientListRaw.split("/");
			
			if(clientList.length < 1)
				clientList = new String[] {clientListRaw};
				
			for(String s : clientList)
				System.out.println(s);
			
			try
			{
				//Check to see if client list is up to date
				if(Arrays.equals(fileList, clientList))
				{
					clientBuff.add(new Package(0));
					pkgOut.writeObject(clientBuff.toArray());
				}
				
				else
				{
					//Check to see if client is missing files
					for(String s : fileList)
						if(!(Arrays.asList(clientList).contains(s)))
						{
							Path file = Paths.get(s);
							if(Files.exists(file))
								clientBuff.add(new Package(1, Files.readAllBytes(file), s));
						}	
					
					//Check to see if client has deleted files
					for(String s : clientList)
						if(!(Arrays.asList(fileList).contains(s)))
							clientBuff.add(new Package(2, s));
					
					pkgOut.writeObject(clientBuff.toArray(new Package[clientBuff.size()]));
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public  class Delete implements InstructionExec
	{
		public void execute(Package p)
		{
			String fName = p.name;
			String cIp = client.getInetAddress().toString();
			Package response;
			
			synchronized(files)
			{
				try
				{
					//check if file exists
					if(!files.containsKey(fName))
						response = new Package(0);
					
					//check if file belongs to client
					if(!files.get(fName).equals(cIp))
						response = new Package(1);
					
					//remove file
					else
					{
						Path file = Paths.get(fName);
						Files.delete(file);
						files.remove(fName);
						response = new Package(2);
						System.out.println("Removed file");
					}
					
					pkgOut.writeObject(response);
					
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	public class Add implements InstructionExec
	{
		public void execute(Package p)
		{
			//Add file to server
			try
			{
				FileOutputStream fo = new FileOutputStream(p.name);
				fo.write(p.file);
				fo.close();
				
				synchronized(files) //updates master list with file name and owner
				{
					files.put(p.name, client.getInetAddress().toString());
				}
				
				System.out.println("Added file");
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
}
