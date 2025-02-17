package oldmana.md.client.state;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import oldmana.md.client.Player;
import oldmana.md.client.card.Card;
import oldmana.md.client.gui.action.ActionScreenRent;
import oldmana.md.client.gui.component.MDButton;
import oldmana.md.common.playerui.ButtonColorScheme;
import oldmana.md.common.state.TargetState;
import oldmana.md.net.packet.client.action.PacketActionPay;

public class ActionStateRent extends ActionState
{
	private Map<Player, Integer> charges;
	
	private ActionScreenRent rentScreen;
	
	public ActionStateRent(Player renter, Map<Player, Integer> charges)
	{
		super(renter, new ArrayList<Player>(charges.keySet()));
		this.charges = charges;
	}
	
	public ActionScreenRent getRentScreen()
	{
		return rentScreen;
	}
	
	@Override
	public void setTargetState(Player player, TargetState state)
	{
		super.setTargetState(player, state);
		Player thePlayer = getClient().getThePlayer();
		if (thePlayer == player && isTarget(player))
		{
			if (state == TargetState.REFUSED)
			{
				MDButton button = getClient().getTableScreen().getMultiButton();
				button.setEnabled(false);
			}
			else if (state == TargetState.ACCEPTED)
			{
				cleanup();
			}
			else if (state == TargetState.TARGETED)
			{
				MDButton button = getClient().getTableScreen().getMultiButton();
				button.setEnabled(true);
			}
		}
	}
	
	@Override
	public void onPreTargetRemoved(Player player)
	{
		if (player == getClient().getThePlayer())
		{
			cleanup();
		}
		else
		{
			super.onPreTargetRemoved(player);
		}
	}
	
	@Override
	public void setup()
	{
		Player player = getClient().getThePlayer();
		if (isTarget(player) && !isAccepted(player))
		{
			rentScreen = new ActionScreenRent(getClient().getThePlayer(), this);
			rentScreen.setVisible(false);
			getClient().getTableScreen().setActionScreen(rentScreen);
			
			MDButton button = getClient().getTableScreen().getMultiButton();
			button.setEnabled(true);
			button.setText("View Charge");
			button.setColor(ButtonColorScheme.ALERT);
			button.repaint();
			button.setListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent event)
				{
					if (button.isEnabled())
					{
						rentScreen.setVisible(true);
					}
				}
			});
		}
	}
	
	@Override
	public void cleanup()
	{
		super.cleanup();
		getClient().getTableScreen().removeActionScreen();
	}
	
	@Override
	public void updateUI()
	{
		rentScreen = new ActionScreenRent(getClient().getThePlayer(), this);
		rentScreen.setVisible(false);
		getClient().getTableScreen().setActionScreen(rentScreen);
	}
	
	public void payRent(List<Card> cards)
	{
		int[] ids = new int[cards.size()];
		for (int i = 0 ; i < ids.length ; i++)
		{
			ids[i] = cards.get(i).getID();
		}
		getClient().sendPacket(new PacketActionPay(ids));
		cleanup();
	}
	
	public int getPlayerRent(Player player)
	{
		return charges.get(player);
	}
}
