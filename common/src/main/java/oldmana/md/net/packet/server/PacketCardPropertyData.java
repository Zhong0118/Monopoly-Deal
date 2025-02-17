package oldmana.md.net.packet.server;

import oldmana.general.mjnetworkingapi.packet.Packet;

public class PacketCardPropertyData extends Packet
{
	public int id;
	public String name;
	public byte value;
	public byte[] colors;
	public boolean base; // Whether or not the card can be rented with on its own
	public boolean stealable;
	
	public short description;
	
	public PacketCardPropertyData() {}
	
	public PacketCardPropertyData(int id, String name, int value, byte[] colors, boolean base, boolean stealable, int description)
	{
		this.id = id;
		this.name = name;
		this.value = (byte) value;
		this.colors = colors;
		this.base = base;
		this.stealable = stealable;
		
		this.description = (short) description;
	}
}
