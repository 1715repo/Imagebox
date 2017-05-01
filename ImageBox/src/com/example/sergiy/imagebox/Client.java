package com.example.sergiy.imagebox;

import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/*Simple client test-case
 * 
 * Tests Add and Sync protocols 
 * 
 */

public class Client 
{
	private String serverIP = "127.0.0.1"; //default
	
	private Socket connection;
	private ObjectInputStream packageIn; 
	private ObjectOutputStream packageOut; 
	private ArrayList<String> files; //copy of master list
	
	Client()
	{
		init();
	}
	
	public static void main(String[] args)
	{
		Client client = new Client();
		
	}
	
	private void init() //attempts to connect to server, add a file, and sync up with the master list
	{
		try
		{
			connection = new Socket(serverIP, 4567);
			packageOut = new ObjectOutputStream(connection.getOutputStream());
			packageIn = new ObjectInputStream(connection.getInputStream());
			files = new ArrayList<String>();
			
			files.add("0");
			Sync();
		}
		catch(Exception e)
		{
			//e.printStackTrace();
			System.out.println("No sever found listening on port/addr");
		}
	}
	
	public void Sync() //reads response Package array buffer from server ( see Sync in Server.java )
	{
		Package[] response;
		try
		{
			String s = stringCat(files.toArray(new String[0]));
			System.out.println(s);
			packageOut.writeObject(new Package(0, s));
			response = (Package[]) packageIn.readObject();
			
			for(Package p : response)
				switch(p.instruction)
				{
				case 1:
					Add(p);
					break;
				
				case 2:
					Remove(p);
					break;
				}
			
			System.out.println("Finished syncing");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void Add(Package p)
	{
		try
		{
			FileOutputStream fo = new FileOutputStream(p.name);
			fo.write(p.file);
			fo.close();
		
			files.add(p.name);
			System.out.println("Added " + p.name);
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void Remove(Package p)
	{
		files.remove(p.name);
		System.out.println("Removed " + p.name);
	}
	
	public void AddToServer(String file)
	{
		try
		{
			Path path = Paths.get(file);
			Package fp = new Package(1 , Files.readAllBytes(path), file);
			packageOut.writeObject(fp);
			Add(fp);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void RemoveFromServer(String s)
	{
		try
		{
			Package fp = new Package(2, s);
			packageOut.writeObject(fp);
			Package response = (Package)packageIn.readObject();
			
			switch(response.instruction)
			{
			case 0:
				//file not found
				break;
				
			case 1:
				//file doesn't belong to client
				break;
			
			case 2:
				Path path = Paths.get(s);
				Files.delete(path);
				Remove(fp);
			}
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public String stringCat(String[] array)
	{
		String out = "";
		
		for(String s : array)
		{
			out += s;
			out += "/";
		}
		
		return out.substring(0, out.length() - 1);
	}
}
