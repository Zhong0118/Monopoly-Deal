package oldmana.md.client.state.client;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import oldmana.md.client.card.CardButton;
import oldmana.md.client.card.CardProperty;
import oldmana.md.client.card.collection.PropertySet;
import oldmana.md.client.gui.component.MDButton;
import oldmana.md.client.gui.component.MDCreateSet;
import oldmana.md.client.gui.component.MDSelection;
import oldmana.md.client.gui.component.collection.MDHand;
import oldmana.md.client.gui.component.collection.MDPropertySet;
import oldmana.md.client.gui.util.GraphicsUtils;
import oldmana.md.net.packet.client.action.PacketActionUseCardButton;

public class ActionStateClientPlayProperty extends ActionStateClient
{
	private CardProperty property;
	private CardButton button;
	
	private MDSelection propSelect;
	private MDButton cancel;
	private List<PropertySet> setSelects = new ArrayList<PropertySet>();
	private MDCreateSet createSet;
	
	public ActionStateClientPlayProperty(CardProperty property, CardButton button)
	{
		this.property = property;
		this.button = button;
	}
	
	@Override
	public void setup()
	{
		Point propLoc = ((MDHand) getClient().getThePlayer().getHand().getUI()).getScreenLocationOf(property);
		propSelect = new MDSelection(Color.BLUE);
		propSelect.setLocation(propLoc);
		propSelect.setSize(GraphicsUtils.getCardWidth(2), GraphicsUtils.getCardHeight(2));
		getClient().addTableComponent(propSelect, 90);
		
		cancel = new MDButton("Cancel");
		cancel.setSize((int) (propSelect.getWidth() * 0.8), (int) (propSelect.getHeight() * 0.2));
		cancel.setLocation((int) (propSelect.getWidth() * 0.1) + propSelect.getX(), (int) (propSelect.getHeight() * 0.4) + propSelect.getY());
		cancel.setListener(() -> removeState());
		getClient().addTableComponent(cancel, 91);
		
		for (PropertySet set : getClient().getThePlayer().getPropertySets(true))
		{
			if (!set.isMonopoly() && set.isCompatibleWith(property))
			{
				MDPropertySet setUI = (MDPropertySet) set.getUI();
				setUI.enableSelection(() ->
				{
					getClient().sendPacket(new PacketActionUseCardButton(property.getID(), button.getPosition().getID(), set.getID()));
					getClient().setAwaitingResponse(true);
					
					removeState();
				});
				setSelects.add(set);
			}
		}
		
		createSet = new MDCreateSet(getClient().getThePlayer());
		createSet.setSize(GraphicsUtils.getCardWidth(), GraphicsUtils.getCardHeight());
		getClient().getTableScreen().add(createSet, new Integer(90));
		createSet.addClickListener(() ->
		{
			getClient().sendPacket(new PacketActionUseCardButton(property.getID(), button.getPosition().getID(), -1));
			getClient().setAwaitingResponse(true);
			
			removeState();
		});
	}
	
	@Override
	public void cleanup()
	{
		getClient().removeTableComponent(propSelect);
		getClient().removeTableComponent(cancel);
		if (createSet != null)
		{
			getClient().removeTableComponent(createSet);
		}
		for (PropertySet set : setSelects)
		{
			((MDPropertySet) set.getUI()).disableSelection();
		}
		getClient().getTableScreen().repaint();
	}
}
