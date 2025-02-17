package oldmana.md.client.gui.component.collection;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.SwingUtilities;

import oldmana.md.client.card.Card;
import oldmana.md.client.card.collection.CardCollection;
import oldmana.md.client.card.collection.Hand;
import oldmana.md.client.gui.component.MDOverlayHand;
import oldmana.md.client.gui.util.GraphicsUtils;

public class MDHand extends MDCardCollection
{
	private Card hovered;
	private MDOverlayHand overlay;
	private MDHandListener listener;
	
	public MDHand(Hand hand)
	{
		super(hand, 2);
		listener = new MDHandListener();
		addMouseListener(listener);
		addMouseMotionListener(listener);
		Toolkit.getDefaultToolkit().addAWTEventListener(event ->
		{
			if (overlay == null)
			{
				return;
			}
			if (event instanceof MouseEvent)
			{
				MouseEvent me = (MouseEvent) event;
				if (me.getID() == MouseEvent.MOUSE_MOVED)
				{
					if (me.getComponent() != MDHand.this && !SwingUtilities.isDescendingFrom(me.getComponent(), overlay))
					{
						removeOverlay();
					}
				}
			}
		}, AWTEvent.MOUSE_EVENT_MASK + AWTEvent.MOUSE_MOTION_EVENT_MASK);
		update();
	}
	
	@Override
	public void update()
	{
		repaint();
	}
	
	public void removeOverlay()
	{
		if (hovered != null)
		{
			overlay.removeCardInfo();
			remove(overlay);
			hovered = null;
			overlay = null;
			repaint();
		}
	}
	
	private int getCardX(int cardIndex, int cardCount)
	{
		if (getWidth() <= GraphicsUtils.getCardWidth(2))
		{
			return 0;
		}
		double cardsWidth = GraphicsUtils.getCardWidth(2) * cardCount;
		double padding = (getWidth() - cardsWidth) / (double) (cardCount + 1);
		boolean negativePadding = padding < 0;
		padding = Math.max(padding, 0);
		
		double start = padding - ((padding / cardCount) * cardIndex);
		
		double room = getWidth() - cardsWidth;
		double interval = room / (cardCount - (negativePadding ? 1 : 0));
		
		return (int) (start + ((GraphicsUtils.getCardWidth(2) + interval) * cardIndex));
	}
	
	@Override
	public Point getLocationOf(int cardIndex, int cardCount)
	{
		return new Point(getCardX(cardIndex, cardCount), 0);
	}
	
	public Card getCardAt(int x, int y)
	{
		CardCollection hand = getCollection();
		for (int i = 0 ; i < hand.getCardCount() ; i++)
		{
			int low = getCardX(i, getCardCount());
			int high = low + GraphicsUtils.getCardWidth(2);
			if (x >= low && x < high)
			{
				return hand.getCardAt(i);
			}
		}
		return null;
	}
	
	public int getCardStartX(int x)
	{
		CardCollection hand = getCollection();
		for (int i = 0 ; i < hand.getCardCount() ; i++)
		{
			int low = getCardX(i, getCardCount());
			int high = low + GraphicsUtils.getCardWidth(2);
			if (x >= low && x < high)
			{
				return low;
			}
		}
		return -1;
	}
	
	@Override
	public void setModification(CollectionMod mod)
	{
		super.setModification(mod);
		removeOverlay();
	}
	
	@Override
	public void paintComponent(Graphics gr)
	{
		super.paintComponent(gr);
		Graphics2D g = (Graphics2D) gr;
		
		CardCollection hand = getCollection();
		
		if (hand != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			
			paintCards(g);
			
			if (getClient().isDebugEnabled())
			{
				g.setColor(Color.MAGENTA);
				GraphicsUtils.drawDebug(g, "ID: " + getCollection().getID(), scale(32), getWidth(), getHeight() / 2);
				
				g.setColor(Color.GREEN);
				g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
		}
		else
		{
			g.setColor(Color.RED);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
	}
	
	public class MDHandListener implements MouseListener, MouseMotionListener
	{
		@Override
		public void mouseDragged(MouseEvent event)
		{
			
		}

		@Override
		public void mouseMoved(MouseEvent event)
		{
			if (getCollection() != null)
			{
				Card curHover = getCardAt(event.getX(), 0);
				if (!isBeingModified())
				{
					if ((curHover == null && hovered != null) || (hovered != null && curHover != hovered))
					{
						removeOverlay();
					}
					if (curHover != null && hovered == null)
					{
						hovered = curHover;
						overlay = new MDOverlayHand(hovered);
						overlay.setLocation(getCardStartX(event.getX()), 0);
						add(overlay);
						overlay.addCardInfo();
						repaint();
					}
				}
				else
				{
					removeOverlay();
				}
			}
		}

		@Override
		public void mouseClicked(MouseEvent event)
		{
			
		}

		@Override
		public void mouseEntered(MouseEvent event)
		{
			/*
			System.out.println("Mouse Entered");
			MDCard card = (MDCard) event.getComponent();
			if (overlay != null && overlay.getCard() != card)
			{
				MDCard last = overlay.getCard();
				last.remove(overlay);
				last.repaint();
				overlay = null;
			}
			if (overlay == null)
			{
				overlay = new MDOverlayHand(card);
				card.add(overlay);
				card.repaint();
			}
			*/
		}

		@Override
		public void mouseExited(MouseEvent event)
		{
			if (getCollection() != null)
			{
				Point p = event.getPoint();
				if (p.x < 0 || p.x >= getWidth() || p.y < 0 || p.y >= getHeight())
				{
					if (hovered != null)
					{
						removeOverlay();
					}
				}
				repaint();
			}
		}

		@Override
		public void mousePressed(MouseEvent event)
		{
			
		}

		@Override
		public void mouseReleased(MouseEvent event)
		{
			
		}
	}
}
