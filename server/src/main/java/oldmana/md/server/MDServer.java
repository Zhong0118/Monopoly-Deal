package oldmana.md.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import oldmana.md.server.ai.AIManager;
import oldmana.md.server.card.CardAction;
import oldmana.md.server.card.CardBuilding;
import oldmana.md.server.card.CardMoney;
import oldmana.md.server.card.CardProperty;
import oldmana.md.server.card.CardRegistry;
import oldmana.md.server.card.collection.CardCollection;
import oldmana.md.server.card.collection.deck.CustomDeck.DeckLoadFailureException;
import oldmana.md.server.card.CardType;

import oldmana.general.mjnetworkingapi.packet.Packet;
import oldmana.md.net.packet.server.PacketDestroyPlayer;
import oldmana.md.net.packet.server.PacketKick;
import oldmana.md.net.packet.server.PacketPlaySound;
import oldmana.md.net.packet.server.PacketSoundData;
import oldmana.md.net.packet.server.PacketStatus;
import oldmana.md.net.packet.universal.PacketChat;
import oldmana.md.net.packet.universal.PacketKeepConnected;
import oldmana.md.server.card.Card;
import oldmana.md.server.card.action.*;
import oldmana.md.server.card.collection.Deck;
import oldmana.md.server.card.collection.DiscardPile;
import oldmana.md.server.card.collection.VoidCollection;
import oldmana.md.server.card.collection.deck.CustomDeck;
import oldmana.md.server.card.collection.deck.VanillaDeck;
import oldmana.md.server.event.EventManager;
import oldmana.md.server.event.PlayerRemovedEvent;
import oldmana.md.server.mod.ServerMod;
import oldmana.md.server.mod.ModLoader;
import oldmana.md.server.net.IncomingConnectionsThread;
import oldmana.md.server.net.NetServerHandler;
import oldmana.md.server.playerui.ChatLinkHandler;
import oldmana.md.server.rules.GameRules;
import oldmana.md.server.state.ActionStateDoNothing;
import oldmana.md.server.state.GameState;

public class MDServer
{
	private static MDServer instance;
	
	public static final String VERSION = "0.7 Dev";
	
	private ScheduledExecutorService serverThread;
	private Executor syncExecutor = task -> getScheduler().scheduleTask(task);
	
	private File dataFolder;
	
	private ModLoader modLoader;
	
	private ServerConfig config;
	private PlayerRegistry playerRegistry;
	
	private final List<Client> newClients = new ArrayList<Client>();
	
	private List<Player> players = new ArrayList<Player>();
	
	private GameState gameState;
	
	private CardRegistry cardRegistry;
	
	private Map<Integer, Card> cards = new HashMap<Integer, Card>();
	private int nextCardID = 0;
	private Map<Integer, CardCollection> cardCollections = new HashMap<Integer, CardCollection>();
	private int nextCardCollectionID = 0;
	
	private VoidCollection voidCollection;
	private Deck deck;
	private DiscardPile discardPile;
	
	private NetServerHandler netHandler = new NetServerHandler(this);
	
	private EventManager eventManager = new EventManager();
	private ChatLinkHandler linkHandler = new ChatLinkHandler();
	
	private Console consoleSender = new Console();
	private CommandHandler cmdHandler = new CommandHandler();

	private MDScheduler scheduler = new MDScheduler();
	
	private AIManager aiManager;

	private final List<String> cmdQueue = Collections.synchronizedList(new ArrayList<String>());
	
	private volatile boolean running = false;
	private volatile boolean shutdown = false;

	private GameRules rules = new GameRules();

	private int tickCount;
	
	private boolean verbose;
	
	private Map<String, MDSound> sounds = new HashMap<String, MDSound>();
	
	private IncomingConnectionsThread threadIncConnect;
	
	private byte[] serverKey;
	
	public MDServer()
	{
		this(new File(System.getProperty("user.dir")));
	}
	
	public MDServer(File dataFolder)
	{
		instance = this;
		this.dataFolder = dataFolder;
		config = new ServerConfig();
		playerRegistry = new PlayerRegistry();
	}
	
	public void startServer()
	{
		if (serverThread != null)
		{
			throw new IllegalStateException("Server already started");
		}
		serverThread = Executors.newSingleThreadScheduledExecutor();
		serverThread.execute(() ->
		{
			try
			{
				startServerSync();
			}
			catch (Exception | Error e)
			{
				System.err.println("Failed to start server!");
				e.printStackTrace();
				System.exit(1);
			}
		});
	}
	
	private void startServerSync()
	{
		if (!isIntegrated())
		{
			System.setOut(new ServerPrintStream(System.out));
			System.setErr(new ServerPrintStream(System.err));
		}
		
		System.out.println("Starting Monopoly Deal Server Version " + VERSION);
		
		cardRegistry = new CardRegistry();
		registerDefaultCards();
		
		gameState = new GameState(this);
		gameState.addActionState(new ActionStateDoNothing());
		
		voidCollection = new VoidCollection();
		deck = new Deck();
		discardPile = new DiscardPile();
		config.loadConfig();
		verbose = config.getBoolean("verbose");
		int port = isIntegrated() ? 0 : config.getInt("port");
		serverKey = config.getBigInteger("serverKey").toByteArray();
		try
		{
			threadIncConnect = new IncomingConnectionsThread(port);
		}
		catch (IOException e)
		{
			System.out.println("Failed to bind port " + port);
			e.printStackTrace();
			System.exit(0);
		}
		System.out.println("Using port " + port);
		cmdHandler.registerDefaultCommands();
		try
		{
			playerRegistry.loadPlayers();
		}
		catch (IOException e)
		{
			System.err.println("Failed to load players file!");
			e.printStackTrace();
			System.exit(1);
		}
		// Keep connected checker
		scheduler.scheduleTask(20, true, task ->
		{
			for (Player player : getPlayers())
			{
				if (player.isOnline())
				{
					if (!player.hasSentPing() && player.getLastPing() + (20 * 20) <= tickCount)
					{
						player.sendPacket(new PacketKeepConnected());
						player.setSentPing(true);
					}
					else if (player.getLastPing() + (40 * 20) <= tickCount)
					{
						player.setOnline(false);
						player.setSentPing(false);
						if (player.getNet() != null)
						{
							player.getNet().close();
							player.setNet(null);
						}
						System.out.println(player.getDescription() + " timed out");
					}
				}
			}
		});
		
		//aiManager = new AIManager();
		
		loadSounds();
		
		System.out.println("Loading Mods");
		try
		{
			loadMods();
		}
		catch (Exception | Error e)
		{
			System.out.println("Failed to load mods!");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Loading Decks");
		deck.registerDeckStack("vanilla", new VanillaDeck());
		if (deck.getDeckStack() == null)
		{
			deck.setDeckStack("vanilla");
		}
		loadDecks();
		
		Thread consoleReader = new Thread(() ->
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			while (!shutdown)
			{
				try
				{
					String line = reader.readLine();
					synchronized (cmdQueue)
					{
						cmdQueue.add(line);
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
					break;
				}
			}
		}, "Console Reader Thread");
		consoleReader.setDaemon(true);
		consoleReader.start();
		
		// AI ticking task
		getScheduler().scheduleTask(50, true, task ->
		{
			for (Player player : getPlayers())
			{
				if (player.isBot())
				{
					player.doAIAction();
				}
			}
		});
		
		System.out.println("Finished initialization");
		
		running = true;
		serverThread.scheduleAtFixedRate(() -> tickServer(), 50, 50, TimeUnit.MILLISECONDS);
	}
	
	public void tickServer()
	{
		try
		{
			synchronized (newClients)
			{
				for (Client client : new ArrayList<Client>(newClients))
				{
					if (!client.isConnected())
					{
						continue;
					}
					netHandler.processPackets(client);
				}
			}
			
			// Process packets
			for (Player player : getPlayers())
			{
				if (player.isConnected())
				{
					if (player.getNet().isClosed())
					{
						player.setOnline(false);
						continue;
					}
					netHandler.processPackets(player);
				}
			}
			
			// Process commands
			synchronized (cmdQueue)
			{
				for (String line : cmdQueue)
				{
					getCommandHandler().executeCommand(consoleSender, line);
				}
				cmdQueue.clear();
			}
			
			scheduler.runTick();
			
			gameState.tick();
			
			tickCount++;
			
			if (shutdown)
			{
				doShutdown();
			}
		}
		catch (Exception | Error e)
		{
			System.err.println("Server has crashed!");
			e.printStackTrace();
			
			try
			{
				doShutdown(true); // Try to gracefully kick players
			}
			catch (Exception | Error e2) {}
			System.exit(1);
		}
	}
	
	private void doShutdown()
	{
		doShutdown(false);
	}
	
	private void doShutdown(boolean error)
	{
		for (ServerMod mod : getMods())
		{
			try
			{
				mod.onShutdown();
			}
			catch (Exception | Error e)
			{
				System.err.println("Error while executing onShutdown in mod " + mod.getName());
				e.printStackTrace();
			}
		}
		
		broadcastPacket(new PacketKick(error ? "Server crashed" : "Server shut down"));
		threadIncConnect.interrupt();
		while (true)
		{
			boolean hasPackets = false;
			for (Player player : getPlayers())
			{
				if (player.getNet() != null && player.getNet().hasOutPackets())
				{
					hasPackets = true;
					break;
				}
			}
			if (!hasPackets)
			{
				break;
			}
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		System.out.println("Server stopped");
		instance = null;
		running = false;
		serverThread.shutdown();
		if (!isIntegrated())
		{
			System.exit(error ? 1 : 0);
		}
	}
	
	public boolean isRunning()
	{
		return running;
	}
	
	public void waitForShutdown()
	{
		try
		{
			serverThread.awaitTermination(100, TimeUnit.HOURS);
		}
		catch (InterruptedException e) {}
	}
	
	/**
	 * Signal for the server to start shutting down at the end of the tick.
	 */
	public void shutdown()
	{
		shutdown = true;
	}
	
	public boolean isShuttingDown()
	{
		return shutdown;
	}
	
	/**
	 * Check if this server is controlled by a client.
	 * @return True if this server is directly ran by a client
	 */
	public boolean isIntegrated()
	{
		try
		{
			Class.forName("oldmana.md.client.MDClient");
			return true;
		}
		catch (ClassNotFoundException e) {}
		return false;
	}
	
	public int getPort()
	{
		return threadIncConnect.getSocket().getLocalPort();
	}
	
	public File getDataFolder()
	{
		return dataFolder;
	}
	
	private void loadMods()
	{
		modLoader = new ModLoader();
		File modsFolder = new File(getDataFolder(), "mods");
		if (!modsFolder.exists())
		{
			modsFolder.mkdir();
		}
		modLoader.loadMods(modsFolder);
	}
	
	public List<ServerMod> getMods()
	{
		return modLoader.getMods();
	}
	
	public <M extends ServerMod> M getMod(Class<M> modClass)
	{
		for (ServerMod mod : modLoader.getMods())
		{
			if (mod.getClass() == modClass)
			{
				return (M) mod;
			}
		}
		return null;
	}
	
	public ServerMod getMod(String name)
	{
		return modLoader.getModByName(name);
	}
	
	public boolean isModLoaded(String name)
	{
		return modLoader.isModLoaded(name);
	}
	
	public ModLoader getModLoader()
	{
		return modLoader;
	}
	
	private void loadDecks()
	{
		File folder = new File(getDataFolder(), "decks");
		if (!folder.exists())
		{
			folder.mkdir();
		}
		if (!folder.isDirectory())
		{
			System.out.println("Failed to load decks. Non-directory file named decks in server folder?");
			return;
		}
		for (File f : folder.listFiles())
		{
			String name = f.getName();
			if (!f.isDirectory() && name.endsWith(".json"))
			{
				String deckName = f.getName().substring(0, name.length() - 5);
				try
				{
					getDeck().registerDeckStack(deckName, new CustomDeck(deckName, f));
				}
				catch (DeckLoadFailureException e)
				{
					System.out.println("Failed to load deck file " + name);
					e.printStackTrace();
				}
			}
		}
	}
	
	private void registerDefaultCards()
	{
		CardType.CARD = CardRegistry.registerCardType(null, Card.class);
		CardType.ACTION = CardRegistry.registerCardType(null, CardAction.class);
		CardType.MONEY = CardRegistry.registerCardType(null, CardMoney.class);
		CardType.PROPERTY = CardRegistry.registerCardType(null, CardProperty.class);
		CardType.BUILDING = CardRegistry.registerCardType(null, CardBuilding.class);
		
		CardType.CHARGE = CardRegistry.registerCardType(null, CardActionCharge.class);
		
		CardType.DEAL_BREAKER = CardRegistry.registerCardType(null, CardActionDealBreaker.class);
		CardType.DEBT_COLLECTOR = CardRegistry.registerCardType(null, CardActionDebtCollector.class);
		CardType.DOUBLE_THE_RENT = CardRegistry.registerCardType(null, CardActionDoubleTheRent.class);
		CardType.FORCED_DEAL = CardRegistry.registerCardType(null, CardActionForcedDeal.class);
		CardType.ITS_MY_BIRTHDAY = CardRegistry.registerCardType(null, CardActionItsMyBirthday.class);
		CardType.JUST_SAY_NO = CardRegistry.registerCardType(null, CardActionJustSayNo.class);
		CardType.PASS_GO = CardRegistry.registerCardType(null, CardActionPassGo.class);
		CardType.RENT = CardRegistry.registerCardType(null, CardActionRent.class);
		CardType.SLY_DEAL = CardRegistry.registerCardType(null, CardActionSlyDeal.class);
		
		CardType.HOUSE = CardRegistry.registerCardType(null, CardActionHouse.class);
		CardType.HOTEL = CardRegistry.registerCardType(null, CardActionHotel.class);
	}
	
	public CommandHandler getCommandHandler()
	{
		return cmdHandler;
	}
	
	public Console getConsoleSender()
	{
		return consoleSender;
	}
	
	public EventManager getEventManager()
	{
		return eventManager;
	}
	
	public ChatLinkHandler getChatLinkHandler()
	{
		return linkHandler;
	}
	
	public MDScheduler getScheduler()
	{
		return scheduler;
	}
	
	public AIManager getAIManager()
	{
		return aiManager;
	}
	
	public GameState getGameState()
	{
		return gameState;
	}
	
	public CardRegistry getCardRegistry()
	{
		return cardRegistry;
	}
	
	public PlayerRegistry getPlayerRegistry()
	{
		return playerRegistry;
	}
	
	public Player getPlayerByUUID(UUID uuid)
	{
		for (Player player : getPlayers())
		{
			if (player.getUUID().equals(uuid))
			{
				return player;
			}
		}
		return null;
	}
	
	public boolean isPlayerWithUUIDLoggedIn(UUID uuid)
	{
		return getPlayerByUUID(uuid) != null;
	}
	
	public void addClient(Client client)
	{
		synchronized (newClients)
		{
			newClients.add(client);
		}
	}
	
	public void removeClient(Client client)
	{
		synchronized (newClients)
		{
			newClients.remove(client);
		}
	}
	
	public void disconnectClient(Client client)
	{
		client.getNet().close();
		removeClient(client);
	}
	
	public void disconnectClient(Client client, String reason)
	{
		client.sendPacket(new PacketKick(reason));
		disconnectClient(client);
	}
	
	public void addPlayer(Player player)
	{
		players.add(player);
		gameState.getTurnOrder().addPlayer(player);
	}
	
	public void kickPlayer(Player player)
	{
		kickPlayer(player, "You've been kicked");
	}
	
	public void kickPlayer(Player player, String reason)
	{
		player.sendPacket(new PacketKick(reason));
		if (player.getNet() != null)
		{
			player.getNet().close();
			player.setNet(null);
		}
		for (Card card : player.getAllCards())
		{
			card.transfer(getDiscardPile(), -1, 2);
		}
		players.remove(player);
		gameState.getTurnOrder().removePlayer(player);
		for (Player p : getPlayers())
		{
			p.removeButtons(player);
		}
		eventManager.callEvent(new PlayerRemovedEvent(player));
		CardCollection.unregister(player.getHand());
		CardCollection.unregister(player.getBank());
		broadcastPacket(new PacketDestroyPlayer(player.getID()));
	}
	
	public List<Player> getPlayers()
	{
		return getGameState().getTurnOrder().getOrder();
	}
	
	public int getPlayerCount()
	{
		return players.size();
	}
	
	public List<Player> getPlayersExcluding(Player excluded)
	{
		List<Player> players = getPlayers();
		players.remove(excluded);
		return players;
	}
	
	public List<Player> getPlayersExcluding(List<Player> excluded)
	{
		List<Player> players = getPlayers();
		players.removeAll(excluded);
		return players;
	}
	
	public Player getPlayerByID(int id)
	{
		for (Player player : players)
		{
			if (player.getID() == id)
			{
				return player;
			}
		}
		return null;
	}
	
	public Player getPlayerByName(String name)
	{
		name = name.toLowerCase();
		for (Player player : players)
		{
			if (player.getName().toLowerCase().equals(name))
			{
				return player;
			}
		}
		return null;
	}
	
	public GameRules getGameRules()
	{
		return rules;
	}
	
	public Map<Integer, Card> getCards()
	{
		return cards;
	}
	
	public int nextCardID()
	{
		return nextCardID++;
	}
	
	public Map<Integer, CardCollection> getCardCollections()
	{
		return cardCollections;
	}
	
	public int nextCardCollectionID()
	{
		return nextCardCollectionID++;
	}
	
	public VoidCollection getVoidCollection()
	{
		return voidCollection;
	}
	
	public Deck getDeck()
	{
		return deck;
	}
	
	public DiscardPile getDiscardPile()
	{
		return discardPile;
	}
	
	public int getTickCount()
	{
		return tickCount;
	}
	
	public byte[] getServerKey()
	{
		return serverKey;
	}
	
	private void loadSounds()
	{
		File soundsFolder = new File(getDataFolder(), "sounds");
		if (!soundsFolder.exists())
		{
			soundsFolder.mkdir();
		}
		for (File f : soundsFolder.listFiles())
		{
			if (!f.isDirectory() && f.getName().endsWith(".wav"))
			{
				loadSound(f);
			}
		}
	}
	
	public void loadSound(File file)
	{
		loadSound(file, file.getName().substring(0, file.getName().length() - 4));
	}
	
	public void loadSound(File file, String name)
	{
		loadSound(file, name, false);
	}
	
	public void loadSound(File file, boolean sendPackets)
	{
		loadSound(file, file.getName().substring(0, file.getName().length() - 4), sendPackets);
	}
	
	public void loadSound(File file, String name, boolean sendPackets)
	{
		try
		{
			DigestInputStream is = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("MD5"));
			byte[] data = new byte[is.available()];
			is.read(data);
			int hash = Arrays.hashCode(is.getMessageDigest().digest());
			MDSound sound = new MDSound(name, data, hash);
			sounds.put(name, sound);
			is.close();
			System.out.println("Loaded sound file: " + file.getName());
			if (sendPackets)
			{
				broadcastPacket(new PacketSoundData(sound.getName(), sound.getData(), sound.getHash()));
			}
		}
		catch (Exception e)
		{
			System.out.println("Error loading sound file: " + file.getName());
			e.printStackTrace();
		}
	}
	
	public boolean doesSoundExist(String name)
	{
		return sounds.containsKey(name);
	}
	
	public void playSound(String name)
	{
		playSound(name, false);
	}
	
	public void playSound(String name, boolean queued)
	{
		broadcastPacket(new PacketPlaySound(name, queued));
	}
	
	public void verifySounds(Player player, Map<String, Integer> cachedSounds)
	{
		Set<Entry<String, MDSound>> sounds = this.sounds.entrySet();
		int i = 1;
		for (Entry<String, MDSound> entry : sounds)
		{
			MDSound sound = entry.getValue();
			if (!cachedSounds.containsKey(sound.getName()) || sound.getHash() != cachedSounds.get(sound.getName()))
			{
				player.sendPacket(new PacketStatus("Downloading sound " + i + "/" + sounds.size() + ".."));
				player.sendPacket(new PacketSoundData(sound.getName(), sound.getData(), sound.getHash()));
			}
			i++;
		}
		getGameState().sendStatus(player);
	}
	
	public void broadcastPacket(Packet packet)
	{
		for (Player player : players)
		{
			player.sendPacket(packet);
		}
	}
	
	public void broadcastPacket(Packet packet, Player exception)
	{
		for (Player player : players)
		{
			if (player != exception)
			{
				player.sendPacket(packet);
			}
		}
	}
	
	public void broadcastMessage(String message)
	{
		broadcastMessage(message, false);
	}
	
	public void broadcastMessage(String message, Player exception)
	{
		broadcastMessage(message, exception, false);
	}
	
	public void broadcastMessage(String message, boolean printConsole)
	{
		broadcastPacket(new PacketChat(MessageBuilder.fromSimple(message)));
		if (printConsole)
		{
			System.out.println(ChatColor.stripFormatting(message));
		}
	}
	
	public void broadcastMessage(String message, Player exception, boolean printConsole)
	{
		broadcastPacket(new PacketChat(MessageBuilder.fromSimple(message)), exception);
		if (printConsole)
		{
			System.out.println(ChatColor.stripFormatting(message));
		}
	}
	
	public boolean isVerbose()
	{
		return verbose;
	}
	
	public Executor getSyncExecutor()
	{
		return syncExecutor;
	}
	
	public static MDServer getInstance()
	{
		return instance;
	}
}
