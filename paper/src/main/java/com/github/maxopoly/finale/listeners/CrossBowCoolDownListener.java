package com.github.maxopoly.finale.listeners;

import com.github.maxopoly.finale.Finale;
import com.github.maxopoly.finale.external.CombatTagPlusManager;
import com.github.maxopoly.finale.external.FinaleSettingManager;
import java.text.DecimalFormat;
import java.util.UUID;
import java.util.function.BiFunction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import vg.civcraft.mc.civmodcore.players.scoreboard.bottom.BottomLine;
import vg.civcraft.mc.civmodcore.players.scoreboard.bottom.BottomLineAPI;
import vg.civcraft.mc.civmodcore.players.scoreboard.side.CivScoreBoard;
import vg.civcraft.mc.civmodcore.players.scoreboard.side.ScoreBoardAPI;
import vg.civcraft.mc.civmodcore.utilities.cooldowns.ICoolDownHandler;
import vg.civcraft.mc.civmodcore.utilities.cooldowns.TickCoolDownHandler;

public class CrossBowCoolDownListener implements Listener {

	private static CrossBowCoolDownListener instance;

	public static long getCrossBowCoolDown(UUID uuid) {
		if (instance == null) {
			return -1;
		}
		return instance.cds.getRemainingCoolDown(uuid);
	}

	private ICoolDownHandler<UUID> cds;
	private CombatTagPlusManager ctpManager;
	private boolean combatTag;

	public CrossBowCoolDownListener(long cooldown, boolean combatTag, CombatTagPlusManager ctpManager) {
		instance = this;
		this.cds = new TickCoolDownHandler<>(Finale.getPlugin(), cooldown / 50);
		this.ctpManager = ctpManager;
		this.combatTag = combatTag;
	}

	public long getCoolDown() {
		return cds.getTotalCoolDown();
	}
	
	private BottomLine cooldownBottomLine;
	
	public BottomLine getCooldownBottomLine() {
		if (cooldownBottomLine == null) {
			cooldownBottomLine = BottomLineAPI.createBottomLine("CrossBowCooldown", 1);
			cooldownBottomLine.updatePeriodically(getCooldownBiFunction(), 1L);
		}
		return cooldownBottomLine;
	}
	
	private CivScoreBoard cooldownBoard;
	
	public CivScoreBoard getCooldownBoard() {
		if (cooldownBoard == null) {
			cooldownBoard = ScoreBoardAPI.createBoard("CrossBowCooldown");
			cooldownBoard.updatePeriodically(getCooldownBiFunction(), 1L);
		}
		return cooldownBoard;
	}
	
	public String getCooldownText(Player shooter) {
		return ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "CrossBow: " + ChatColor.GREEN + formatCoolDown(shooter.getUniqueId());
	}
	
	public void putOnCooldown(Player shooter) {
		cds.putOnCoolDown(shooter.getUniqueId());
		FinaleSettingManager settings = Finale.getPlugin().getSettingsManager();
		if (settings.setVanillaItemCooldown(shooter.getUniqueId())) {
			Bukkit.getScheduler().runTaskLater(Finale.getPlugin(), ()->{
					// -1, because this is delayed by one tick
					shooter.setCooldown(Material.CROSSBOW, (int) cds.getTotalCoolDown() - 1);
			}, 1);
		}
		
		if (settings.actionBarItemCooldown(shooter.getUniqueId())) {
			BottomLine bottomLine = getCooldownBottomLine();
			bottomLine.updatePlayer(shooter, getCooldownText(shooter));
		}
		if (settings.sideBarItemCooldown(shooter.getUniqueId())) {
			CivScoreBoard board = getCooldownBoard();
			board.set(shooter, getCooldownText(shooter)); 
		}
	}
	
	public BiFunction<Player, String, String> getCooldownBiFunction() {
		return (shooter, oldText) -> {
			if (!cds.onCoolDown(shooter.getUniqueId())) {
				return null; 
			}
			
			return getCooldownText(shooter);
		};
	}

	@EventHandler
	public void CrossBowShoot(EntityShootBowEvent e) {
		// ensure it's a CrossBow
		if (e.getBow().getType() != Material.CROSSBOW) {
			return;
		}

		// ensure a player shot it it
		if (!(e.getEntity() instanceof Player)) {
			return;
		}

		Player shooter = (Player) e.getEntity();
		// check whether on cooldown
		if (cds.onCoolDown(shooter.getUniqueId())) {
			e.setCancelled(true);
			shooter.sendMessage(
					ChatColor.RED + "You may shoot a crossbow again in " + formatCoolDown(shooter.getUniqueId()) + " seconds");
			return;
		}

		// tag player if desired
		if (combatTag && ctpManager != null) {
			ctpManager.tag((Player) e.getEntity(), null);
		}
		
		// put CrossBow on cooldown
		putOnCooldown(shooter);
	}

	private DecimalFormat df = new DecimalFormat("#.0");
	
	private String formatCoolDown(UUID uuid) {
		long cd = cds.getRemainingCoolDown(uuid);
		if (cd <= 0) {
			return ChatColor.GREEN + "READY";
		}

		//convert from ticks to ms
		return df.format(cd / 20.0) + " sec";
	}

}
