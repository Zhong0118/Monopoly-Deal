package oldmana.md.server.card.action;

import oldmana.md.server.card.CardAction;

public class CardActionDoubleTheRent extends CardAction
{
	
	public CardActionDoubleTheRent()
	{
		super(1, "Double The Rent");
		setDisplayName("DOUBLE", "THE RENT!");
		setFontSize(7);
		setDisplayOffsetY(2);
		setRevocable(false);
		setMarksPreviousUnrevocable(true);
		setDescription("Can be played with a Rent card to double the charge against players. Counts as a turn.");
	}
	
	@Override
	public CardType getType()
	{
		return CardType.DOUBLE_THE_RENT;
	}
}
