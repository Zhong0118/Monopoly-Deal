package oldmana.md.server.state;

import oldmana.general.mjnetworkingapi.packet.Packet;
import oldmana.md.net.packet.server.actionstate.PacketActionStateBasic;
import oldmana.md.net.packet.server.actionstate.PacketActionStateBasic.BasicActionState;
import oldmana.md.server.Player;

public class ActionStatePlayerTargeted extends ActionState
{
	public ActionStatePlayerTargeted(Player actionOwner, Player actionTarget)
	{
		super(actionOwner, actionTarget);
	}
	
	@Override
	public Packet constructPacket()
	{
		return new PacketActionStateBasic(getActionOwner().getID(), BasicActionState.PLAYER_TARGETED, getTargetPlayer().getID());
	}
}
