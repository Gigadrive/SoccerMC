package eu.thechest.soccermc;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.maps.MapType;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SoccerMC extends JavaPlugin {
    private static SoccerMC instance;

    public static ArrayList<Map> MAPS = new ArrayList<Map>();
    public static HashMap<UUID, Vector> VELOCITY = new HashMap<UUID, Vector>();

    public void onEnable(){
        instance = this;

        SoccerExecutor exec = new SoccerExecutor();
        getCommand("toggleparticles").setExecutor(exec);

        ServerSettingsManager.ENABLE_NICK = false;
        ServerSettingsManager.ENABLE_CHAT = true;
        ServerSettingsManager.MAP_VOTING = false;
        ServerSettingsManager.ARROW_TRAILS = false;
        ServerSettingsManager.KILL_EFFECTS = false;
        ServerSettingsManager.RUNNING_GAME = GameType.SOCCER;
        ServerSettingsManager.SHOW_LEVEL_IN_EXP_BAR = false;
        ServerSettingsManager.ADJUST_CHAT_FORMAT = false;
        ServerSettingsManager.PROTECT_ARMORSTANDS = true;
        ServerSettingsManager.PROTECT_FARMS = true;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = true;
        ServerSettingsManager.VIP_JOIN = false;
        ServerSettingsManager.AUTO_OP = true;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = false;
        ServerSettingsManager.UPDATE_TAB_NAME_WITH_SCOREBOARD = false;
        ServerSettingsManager.ALLOW_MULITPLE_MAPS = true;
        ServerSettingsManager.setMaxPlayers(24);
        ServerSettingsManager.updateGameState(GameState.JOINABLE);

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `maps` WHERE `mapType` = ? AND `active` = ?");
            ps.setString(1, MapType.SOCCER.toString());
            ps.setBoolean(2,true);
            ResultSet rs = ps.executeQuery();

            while(rs.next()){
                Map m = Map.getMap(rs.getInt("id"));

                if(!MAPS.contains(m)) MAPS.add(m);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }

        new BukkitRunnable(){
            @Override
            public void run() {
                doBallPhysics();
            }
        }.runTaskTimer(this,1,1);

        for(World w : Bukkit.getWorlds()){
            w.setTime(0);
            w.setStorm(false);
            w.setThundering(false);
            w.setGameRuleValue("doDaylightCycle","false");
            w.setGameRuleValue("doMobSpawning","false");
            w.setGameRuleValue("doFireTick","false");

            for(Entity e : w.getEntities()){
                if(e.getType() != EntityType.PLAYER && e.getType() != EntityType.ITEM_FRAME && e.getType() != EntityType.PAINTING && e.getType() != EntityType.ARMOR_STAND) e.remove();
            }
        }

        Bukkit.getPluginManager().registerEvents(new SoccerListener(), this);
    }

    public void onDisable(){
        for(Player p : Bukkit.getOnlinePlayers()) SoccerPlayer.unregister(p);
        for(SoccerGame g : SoccerGame.STORAGE.values()) g.unregister();
    }

    public static SoccerMC getInstance(){
        return instance;
    }

    public static void doBallPhysics(){
        for(SoccerGame g : SoccerGame.STORAGE.values()){
            if(g.chicken != null){
                Chicken c = g.chicken;
                UUID uuid = c.getUniqueId();
                Vector momentum = c.getVelocity();

                if(VELOCITY.containsKey(uuid)){
                    momentum = VELOCITY.get(uuid);
                    VELOCITY.remove(uuid);
                }

                if(!c.isDead()){
                    Vector newv = c.getVelocity();

                    // NEW CODE
                    if(newv.getX() == 0.0D){
                        newv.setX(-momentum.getX()*0.9D);
                    } else if(Math.abs(momentum.getX() - newv.getX()) < 0.15D){
                        newv.setX(momentum.getX() * 0.975D);
                    }

                    if((newv.getY() == 0.0D) && (momentum.getY() < -0.1D)){
                        newv.setY(-momentum.getY() * 0.9D);
                    }

                    if(newv.getZ() == 0.0D){
                        newv.setZ(-momentum.getZ() * 0.9D);
                    } else if(Math.abs(momentum.getZ() - newv.getZ()) < 0.15D){
                        newv.setZ(momentum.getZ() * 0.975D);
                    }

                    c.setMaxHealth(20D);
                    c.setHealth(20D);
                    c.setVelocity(newv);
                    VELOCITY.put(uuid, newv);

                    for(Player p : g.getOnlinePlayers()){
                        if(SoccerPlayer.get(p).seesParticles()){
                            p.playEffect(c.getLocation(), Effect.INSTANT_SPELL, 128);
                        }
                    }
                }
            }
        }
    }
}
