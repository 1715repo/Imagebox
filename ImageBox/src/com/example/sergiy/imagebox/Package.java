package com.example.sergiy.imagebox;
/*
 *	POD used by Imagebox client and server 
 *  
 *  In all cases has the instruction int set (0 == Sync, 1 == Add, 2 == Delete)
 *	If the instruction is Delete, the file name is set to the file to be deleted
 *	If the instruction is Add, the file name is set, along with the file buffer, which contains the actual file itself
 *
 */


public class Package implements java.io.Serializable //the object used to communicate between server/client 
{
	public int instruction; //Multipurpose variable used by client/server in switch/cases
	public byte[] file; //Bytes of a file
	public String name; //Name of file. 
	
	Package(int i, byte[] f, String n) //Sync, Add file state
	{
		instruction = i; 
		file = f;		
		name = n;
	}
	
	Package(int i, String n) //Remove file state
	{
		instruction = i;
		name = n;
	}
	
	Package(int i) 			//Status or response state indicating success or failure of instruction
	{
		instruction = i;
	}
}
