package oldmana.md.server.mod;

import oldmana.md.server.MDServer;
import oldmana.md.server.rules.struct.RuleStructObject;
import oldmana.md.server.rules.struct.RuleStructObject.RuleObjectBuilder;

import java.util.Collections;
import java.util.Set;

/**
 * Mods can be created to add new behavior to the server.
 */
public class ServerMod
{
	private String name;
	private String version;
	
	private Set<String> dependencies;
	private Set<String> softDependencies;
	
	private RuleStructObject modRule;
	
	public String getName()
	{
		return name;
	}
	
	protected final void setName(String name)
	{
		if (this.name != null)
		{
			throw new IllegalStateException("This is not intended to be used.");
		}
		this.name = name;
	}
	
	public String getVersion()
	{
		return version;
	}
	
	protected final void setVersion(String version)
	{
		if (this.version != null)
		{
			throw new IllegalStateException("This is not intended to be used.");
		}
		this.version = version;
	}
	
	public Set<String> getDependencies()
	{
		return Collections.unmodifiableSet(dependencies);
	}
	
	protected final void setDependencies(Set<String> dependencies)
	{
		if (this.dependencies != null)
		{
			throw new IllegalStateException("This is not intended to be used.");
		}
		this.dependencies = dependencies;
	}
	
	public Set<String> getSoftDependencies()
	{
		return Collections.unmodifiableSet(softDependencies);
	}
	
	protected final void setSoftDependencies(Set<String> softDependencies)
	{
		if (this.softDependencies != null)
		{
			throw new IllegalStateException("This is not intended to be used.");
		}
		this.softDependencies = softDependencies;
	}
	
	public boolean dependsOn(ServerMod mod)
	{
		return dependencies.contains(mod.getName()) || softDependencies.contains(mod.getName());
	}
	
	public RuleStructObject getModRule()
	{
		return modRule;
	}
	
	protected void generateModRule()
	{
		if (modRule != null)
		{
			return;
		}
		modRule = RuleObjectBuilder.from(getServer().getGameRules().getRootRuleStruct().getChild("modRules"))
				.jsonName(getName())
				.name(getName())
				.description("Rules for " + getName())
				.reducible(true)
				.register();
	}
	
	public MDServer getServer()
	{
		return MDServer.getInstance();
	}
	
	/**
	 * Called right after this mod has been instantiated. Only dependencies are guaranteed to be loaded at this stage.
	 */
	public void onLoad() {}
	
	/**
	 * Called after all mods have been loaded. Mods are enabled by order of dependency.
	 */
	public void onEnable() {}
	
	/**
	 * Called when the server is about to shut down.
	 */
	public void onShutdown() {}
}
