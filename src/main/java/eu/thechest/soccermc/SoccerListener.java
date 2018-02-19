package eu.thechest.soccermc;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.event.PlayerDataLoadedEvent;
import eu.thechest.chestapi.maps.MapLocationType;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.util.BountifulAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SoccerListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
        e.setJoinMessage(null);

        p.teleport(new Location(Bukkit.getWorld("world"),0,80,0));
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) SoccerPlayer.get(all).handleVanishing();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        e.setQuitMessage(null);

        if(ChestUser.isLoaded(p)){
            SoccerGame g = SoccerGame.getGameByPlayer(p);

            if(g != null){
                if(g.isInTeamRed(p.getUniqueId()) || g.isInTeamBlue(p.getUniqueId())){
                    for(Player all : g.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);
                        SoccerPlayer s = SoccerPlayer.get(all);

                        if(g.getStatus() == SoccerGame.Status.INGAME || g.getStatus() == SoccerGame.Status.WARMUP || g.getStatus() == SoccerGame.Status.GOAL){
                            s.updateScoreboard();
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + a.getTranslatedMessage("%p has left the server.").replace("%p",p.getDisplayName() + ChatColor.RED));
                        }
                    }
                }

                if((g.isInTeamBlue(p.getUniqueId()) && g.getOnlinePlayersBlue().size()-1 == 0)){
                    g.finish(true);
                } else if((g.isInTeamRed(p.getUniqueId()) && g.getOnlinePlayersRed().size()-1 == 0)){
                    g.finish(false);
                }

                if(g.getSpectators().contains(p)){
                    g.getSpectators().remove(p);
                }
            }
        }

        SoccerPlayer.unregister(p);
    }

    @EventHandler
    public void onLoaded(PlayerDataLoadedEvent e){
        Player p = e.getPlayer();

        ChestUser u = ChestUser.getUser(p);
        SoccerPlayer s = SoccerPlayer.get(p);

        if(s.getGame() != null){
            if(s.getGame().matchCancelled == null && (s.getGame().getPlayers().size() > s.getGame().getOnlinePlayers().size())){
                s.getGame().matchCancelled = new BukkitRunnable(){
                    @Override
                    public void run() {
                        if(s.getGame().getStatus() == SoccerGame.Status.PREPARING){
                            s.getGame().setStatus(SoccerGame.Status.CANCELLED);
                            s.getGame().unregister();

                            for(Player b : s.getGame().getOnlinePlayers()){
                                BountifulAPI.sendTitle(b,0,3*20,0,ChatColor.GRAY.toString() + ChatColor.BOLD.toString() + ChestUser.getUser(b).getTranslatedMessage("MATCH CANCELLED"),"");
                                ChestUser.getUser(b).connectToLobby();
                            }
                        }
                    }
                }.runTaskLater(ChestAPI.getInstance(), 10*20);
            }

            new BukkitRunnable(){
                @Override
                public void run() {
                    if(s.getGame().startTries == 30) cancel();

                    if(s.getGame().getOnlinePlayers().size() >= s.getGame().getPlayers().size()){
                        if(s.getGame().matchCancelled != null) {
                            s.getGame().matchCancelled.cancel();
                            s.getGame().matchCancelled = null;
                        }

                        s.getGame().startGame();
                        cancel();
                        s.getGame().startTries++;
                    }
                }
            }.runTaskTimer(SoccerMC.getInstance(),10,10);
        } else {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `soccer_spectate` WHERE `uuid` = ?");
                ps.setString(1,p.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();

                if(rs.first()){
                    int gameID = rs.getInt("game");
                    SoccerGame game = SoccerGame.getGame(gameID);

                    if(game != null){
                        ChestAPI.sync(() -> {
                            game.getSpectators().add(p);
                            for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) SoccerPlayer.get(all).handleVanishing();
                            s.updateName();
                            s.updateScoreboard();
                            p.teleport(game.getMap().getLocations(MapLocationType.SOCCER_SPECTATOR).get(0).toBukkitLocation(game.getMapWorld()));

                            for(Player all : game.getOnlinePlayers()){
                                if(all == p) continue;
                                ChestUser a = ChestUser.getUser(all);

                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("%s is now spectating.",u.getRank().getColor() + p.getName() + ChatColor.GOLD));
                            }

                            Bukkit.getScheduler().scheduleSyncDelayedTask(SoccerMC.getInstance(), new Runnable() {
                                @Override
                                public void run() {
                                    game.sendBar(p);
                                }
                            },2*20);
                        });
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("No game found."));
                        u.connectToLobby();
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("No game found."));
                    u.connectToLobby();
                }

                MySQLManager.getInstance().closeResources(rs,ps);
            } catch(Exception e1){
                e1.printStackTrace();
                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                u.connectToLobby();
            }
        }
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        String msg = e.getMessage();

        e.setCancelled(true);

        if(ChestUser.isLoaded(p)){
            ChestUser u = ChestUser.getUser(p);
            SoccerPlayer s = SoccerPlayer.get(p);

            if(s.getGame() != null && s.getGame().getStatus() != SoccerGame.Status.PREPARING){
                for(Player all : s.getGame().getOnlinePlayers()){
                    if(ChestUser.isLoaded(all)){
                        ChestUser a = ChestUser.getUser(all);
                        if(s.getGame().isInTeamBlue(p.getUniqueId())){
                            all.sendMessage(ChatColor.DARK_GRAY + "<[" + ChatColor.BLUE + a.getTranslatedMessage("Team BLUE") + ChatColor.DARK_GRAY + "] " + u.getRank().getColor() + p.getName() + ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + msg);
                        } else if(s.getGame().isInTeamRed(p.getUniqueId())){
                            all.sendMessage(ChatColor.DARK_GRAY + "<[" + ChatColor.RED + a.getTranslatedMessage("Team RED") + ChatColor.DARK_GRAY + "] " + u.getRank().getColor() + p.getName() + ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + msg);
                        } else if(s.getGame().getSpectators().contains(p)){
                            all.sendMessage(ChatColor.DARK_GRAY + "<[" + ChatColor.AQUA + a.getTranslatedMessage("Spectator") + ChatColor.DARK_GRAY + "] " + u.getRank().getColor() + p.getName() + ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + msg);
                        }
                    }
                }

                if(s.getGame().getSpectators().contains(p)){
                    s.getGame().getGame().addSpectatorChatEvent(p,msg);
                } else {
                    s.getGame().getGame().addPlayerChatEvent(p,msg);
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Chicken)){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent e){
        if(e.getEntity() instanceof Player){
            Player p = (Player)e.getEntity();
            p.setFoodLevel(20);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player && e.getEntity() instanceof Chicken){
            Player p = (Player)e.getDamager();
            Chicken c = (Chicken)e.getEntity();

            if(ChestUser.isLoaded(p)){
                SoccerPlayer s = SoccerPlayer.get(p);

                if(s.getGame() != null){
                    if(s.getGame().isInTeamRed(p.getUniqueId()) || s.getGame().isInTeamBlue(p.getUniqueId())){
                        if(s.getGame().chicken.equals(c)){
                            s.getGame().lastChickenDamager = p;
                            for(Player all : s.getGame().getOnlinePlayers()) all.playSound(c.getLocation(), Sound.CHICKEN_HURT,1f,1f);
                        } else {
                            e.setCancelled(true);
                        }
                    } else {
                        e.setCancelled(true);
                    }
                } else {
                    e.setCancelled(true);
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Player p = e.getPlayer();

        boolean freeze = false;

        if(ChestUser.isLoaded(p)){
            ChestUser u = ChestUser.getUser(p);
            SoccerPlayer s = SoccerPlayer.get(p);

            if(s.getGame() != null){
                if(s.getGame().getStatus() == SoccerGame.Status.WARMUP || s.getGame().getStatus() == SoccerGame.Status.PREPARING){
                    if(s.getGame().isInTeamBlue(p.getUniqueId()) || s.getGame().isInTeamRed(p.getUniqueId())){
                        freeze = true;
                    }
                }
            } else {
                freeze = true;
            }
        } else {
            freeze = true;
        }

        if(freeze){
            Location from = e.getFrom();
            Location to = e.getTo();
            double x = Math.floor(from.getX());
            double z = Math.floor(from.getZ());

            if(Math.floor(to.getX()) != x || Math.floor(to.getZ()) != z){
                x += .5;
                z += .5;
                e.getPlayer().teleport(new Location(from.getWorld(),x,from.getY(),z,from.getYaw(),from.getPitch()));
            }
        }
    }
}
