package oldmana.md.server.card.collection;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import oldmana.general.mjnetworkingapi.packet.Packet;
import oldmana.md.net.packet.server.PacketUnknownCardCollectionData;
import oldmana.md.net.packet.server.PacketCardCollectionData.CardCollectionType;
import oldmana.md.server.Player;
import oldmana.md.server.card.Card;
import oldmana.md.server.card.collection.deck.DeckStack;
import oldmana.md.server.event.DeckReshuffledEvent;

public class Deck extends CardCollection
{
	private DeckStack stack;
	
	private Random rand = new Random();
	
	public Deck(DeckStack stack)
	{
		super(null);
		setDeckStack(stack);
	}
	
	public void setDeckStack(DeckStack stack)
	{
		setDeckStack(stack, true);
	}
	
	public void setDeckStack(DeckStack stack, boolean shuffle)
	{
		if (this.stack != null)
		{
			for (Card card : this.stack.getCards())
			{
				card.transfer(getServer().getVoidCollection(), -1, 0.05);
			}
		}
		this.stack = stack;
		for (Card card : stack.getCards())
		{
			card.transfer(this, -1, 0.05);
		}
		if (shuffle)
		{
			shuffle();
		}
		getServer().getGameRules().setRules(stack.getDeckRules());
	}
	
	public DeckStack getDeckStack()
	{
		return stack;
	}
	
	public void shuffle()
	{
		Collections.shuffle(getCards());
	}
	
	public Card drawCard(Player player)
	{
		return drawCard(player, 1);
	}
	
	public Card drawCard(Player player, double time)
	{
		if (getCardCount() > 0)
		{
			Card card = getCards().get(0);
			transferCard(card, player.getHand(), 0, time);
			return card;
		}
		else
		{
			List<Card> cards = getServer().getDiscardPile().getCards(true);
			Collections.reverse(cards);
			for (Card card : cards)
			{
				card.transfer(this, 0, 0.25);
			}
			shuffle();
			getServer().getEventManager().callEvent(new DeckReshuffledEvent(this));
			if (getCardCount() == 0)
			{
				System.out.println("Deck and discard pile are out of cards! " + player.getName() + " cannot draw a card.");
				return null;
			}
			return drawCard(player, time);
		}
	}
	
	public void drawCards(Player player, int amount)
	{
		drawCards(player, amount, 1);
	}
	
	public void drawCards(Player player, int amount, double time)
	{
		for (int i = 0 ; i < amount ; i++)
		{
			drawCard(player, time);
		}
	}
	
	public void insertCardRandomly(Card card)
	{
		insertCardRandomly(card, 1);
	}
	
	public void insertCardRandomly(Card card, double time)
	{
		card.transfer(this, rand.nextInt(getCardCount()), time);
	}
	
	@Override
	public boolean isVisibleTo(Player player)
	{
		return false;
	}
	
	@Override
	public Packet getCollectionDataPacket()
	{
		return new PacketUnknownCardCollectionData(getID(), -1, getCardCount(), CardCollectionType.DECK);
	}
}
