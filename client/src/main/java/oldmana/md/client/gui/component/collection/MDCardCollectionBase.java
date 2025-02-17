package oldmana.md.client.gui.component.collection;

import java.awt.Component;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import oldmana.md.client.card.Card;
import oldmana.md.client.card.collection.CardCollection;
import oldmana.md.client.gui.component.MDComponent;

public abstract class MDCardCollectionBase extends MDComponent
{
	private CardCollection collection;
	private double scale;
	
	private CollectionMod mod;
	private int modIndex;
	private double moveProgress;
	
	public MDCardCollectionBase(CardCollection collection, double scale)
	{
		this.collection = collection;
		this.scale = scale;
	}
	
	public CardCollection getCollection()
	{
		return collection;
	}
	
	public void setCollection(CardCollection collection)
	{
		this.collection = collection;
	}
	
	public void reset()
	{
		setCollection(null);
		setModification(null);
		modIndex = -1;
		moveProgress = 0;
	}
	
	public int getCardCount()
	{
		return collection.getCardCount();
	}
	
	public double getCardScale()
	{
		return scale;
	}
	
	public boolean isCardIncoming()
	{
		return mod == CollectionMod.ADDITION;
	}
	
	public boolean isCardBeingRemoved()
	{
		return mod == CollectionMod.REMOVAL;
	}
	
	public void cardArrived()
	{
		update();
	}
	
	public void startAddition(int index)
	{
		setModification(CollectionMod.ADDITION);
		modIndex = index;
	}
	
	public void startRemoval(int index)
	{
		setModification(CollectionMod.REMOVAL);
		modIndex = index;
	}
	
	public void modificationFinished()
	{
		setModification(null);
		modIndex = -1;
		moveProgress = 0;
	}
	
	public int getModIndex()
	{
		return modIndex;
	}
	
	public void setCardMoveProgress(double progress)
	{
		moveProgress = progress;
	}
	
	public double getCardMoveProgress()
	{
		return moveProgress;
	}
	
	public double getVisibleShiftProgress()
	{
		if (mod == CollectionMod.REMOVAL)
		{
			return Math.min(1, getCardMoveProgress() * 1.5);
		}
		else
		{
			return Math.max(0, (getCardMoveProgress() * 1.5) - 0.5);
		}
	}
	
	public CollectionMod getModification()
	{
		return mod;
	}
	
	public boolean isBeingModified()
	{
		return mod != null;
	}
	
	public void setModification(CollectionMod mod)
	{
		this.mod = mod;
	}
	
	public Point getLocationOf(int cardIndex)
	{
		return getLocationOf(cardIndex, getCardCount());
	}
	
	public Point getScreenLocationOf(int cardIndex)
	{
		return getScreenLocationOf(cardIndex, getCardCount());
	}
	
	public Point getScreenLocationOf(int cardIndex, int cardCount)
	{
		return SwingUtilities.convertPoint(this, getLocationOf(cardIndex, cardCount), getClient().getWindow().getTableScreen());
	}
	
	public Point getLocationOfRelative(int cardIndex, Component component)
	{
		return getLocationOfRelative(cardIndex, getCardCount(), component);
	}
	
	public Point getLocationOfRelative(int cardIndex, int cardCount, Component component)
	{
		return SwingUtilities.convertPoint(this, getLocationOf(cardIndex, cardCount), component);
	}
	
	public abstract void update();
	
	public abstract Point getLocationOf(int cardIndex, int cardCount);
	
	
	public enum CollectionMod
	{
		ADDITION, REMOVAL
	}
}
