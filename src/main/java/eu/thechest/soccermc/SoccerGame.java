package eu.thechest.soccermc;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.game.Game;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.items.ItemCategory;
import eu.thechest.chestapi.items.VaultItem;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.maps.MapLocationData;
import eu.thechest.chestapi.maps.MapLocationType;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.server.ServerUtil;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.util.BarUtil;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.chestapi.util.ParticleEffect;
import net.minecraft.server.v1_8_R3.EntityCreature;
import net.minecraft.server.v1_8_R3.EntityInsentient;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.PathfinderGoalHurtByTarget;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class SoccerGame {
    public static HashMap<Integer,SoccerGame> STORAGE = new HashMap<Integer,SoccerGame>();

    public static SoccerGame getGame(int id){
        if(STORAGE.containsKey(id)){
            return STORAGE.get(id);
        } else {
            new SoccerGame(id);

            if(STORAGE.containsKey(id)){
                return STORAGE.get(id);
            } else {
                return null;
            }
        }
    }

    public static SoccerGame getGameByPlayer(Player p){
        SoccerGame game = null;

        for(SoccerGame g : STORAGE.values()){
            if(g.isInTeamBlue(p.getUniqueId()) || g.isInTeamRed(p.getUniqueId()) || g.getSpectators().contains(p)) game = g;
        }

        if(game == null){
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `soccer_upcomingGames` WHERE `team1` LIKE '%" + p.getUniqueId().toString() + "%' OR `team2` LIKE '%" + p.getUniqueId().toString() + "%' ORDER BY `time`");
                ResultSet rs = ps.executeQuery();
                if(rs.first()){
                    game = SoccerGame.getGame(rs.getInt("id"));
                }

                MySQLManager.getInstance().closeResources(rs,ps);
            } catch(Exception e){
                e.printStackTrace();
            }
        }

        return game;
    }

    private int id;
    private ArrayList<UUID> teamRed;
    private ArrayList<UUID> teamBlue;
    private ArrayList<Player> spectators;
    private boolean ranked;
    private Game game;
    private Map map;
    private String mapName;
    private Status status;

    public BukkitTask matchCancelled;
    public BukkitTask warmupCountdown;
    public BukkitTask chickenScheduler;

    public int warmupCountdownCount;
    public Chicken chicken;
    public Player lastChickenDamager;

    public int scoreRed = 0;
    public int scoreBlue = 0;

    public int maxScore = 3;

    public int bluePoints = 0;
    public int redPoints = 0;

    public int startTries = 0;

    public SoccerGame(int id){
        if(STORAGE.containsKey(id)) return;

        this.teamRed = new ArrayList<UUID>();
        this.teamBlue = new ArrayList<UUID>();
        this.spectators = new ArrayList<Player>();
        this.status = Status.PREPARING;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `soccer_upcomingGames` WHERE `id` = ?");
            ps.setInt(1,id);
            ResultSet rs = ps.executeQuery();

            if(rs.first()){
                this.id = id;

                for(String s : rs.getString("team1").split(",")) if(s != null && !s.isEmpty()) teamRed.add(UUID.fromString(s));
                for(String s : rs.getString("team2").split(",")) if(s != null && !s.isEmpty()) teamBlue.add(UUID.fromString(s));
                this.ranked = rs.getBoolean("ranked");

                ArrayList<Map> maps = new ArrayList<Map>();
                maps.addAll(SoccerMC.MAPS);
                Collections.shuffle(maps);
                map = maps.get(0);
                String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? "" : "/";
                mapName = map.loadMapToServer();
                FileUtils.copyDirectory(new File(Bukkit.getWorldContainer().getAbsolutePath() + d + "plugins/WorldGuard/worlds/" + map.getOriginalWorldName() + "/"),new File(Bukkit.getWorldContainer().getAbsolutePath() + d + "plugins/WorldGuard/worlds/" + mapName + "/"));
                ChestAPI.sync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"worldguard reload"));

                STORAGE.put(id,this);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public int getId() {
        return id;
    }

    public ArrayList<UUID> getTeamRed() {
        return teamRed;
    }

    public boolean isInTeamRed(UUID uuid){
        for(UUID u : getTeamRed()){
            if(u.toString().equals(uuid.toString())) return true;
        }

        return false;
    }

    public ArrayList<Player> getOnlinePlayersRed(){
        ArrayList<Player> p = new ArrayList<Player>();
        for(UUID uuid : getTeamRed()) if(Bukkit.getPlayer(uuid) != null) p.add(Bukkit.getPlayer(uuid));
        return p;
    }

    public ArrayList<UUID> getTeamBlue() {
        return teamBlue;
    }

    public ArrayList<Player> getOnlinePlayersBlue(){
        ArrayList<Player> p = new ArrayList<Player>();
        for(UUID uuid : getTeamBlue()) if(Bukkit.getPlayer(uuid) != null) p.add(Bukkit.getPlayer(uuid));
        return p;
    }

    public boolean isInTeamBlue(UUID uuid){
        for(UUID u : getTeamBlue()){
            if(u.toString().equals(uuid.toString())) return true;
        }

        return false;
    }

    public ArrayList<UUID> getPlayers(){
        ArrayList<UUID> p = new ArrayList<UUID>();
        p.addAll(getTeamRed());
        p.addAll(getTeamBlue());
        return p;
    }

    public ArrayList<Player> getOnlinePlayers(){
        ArrayList<Player> p = new ArrayList<Player>();

        for(UUID uuid : getPlayers()) if(Bukkit.getPlayer(uuid) != null) p.add(Bukkit.getPlayer(uuid));
        p.addAll(getSpectators());

        return p;
    }

    public ArrayList<Player> getSpectators(){
        return this.spectators;
    }

    public boolean isRanked() {
        return ranked;
    }

    public Game getGame() {
        return game;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status s){
        this.status = s;
        saveData();
    }

    public Map getMap() {
        return map;
    }

    public String getMapWorld(){
        return mapName;
    }

    public int getTeamSize(){
        return getTeamRed().size();
    }

    public void startGame(){
        if(getStatus() == Status.PREPARING){
            setStatus(Status.WARMUP);
            for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) SoccerPlayer.get(all).handleVanishing();
            teleportPlayersToMap();
            startCountdown();
            startChickenScheduler();
            game = GameManager.initializeNewGame(GameType.SOCCER,getMap());
            game.getParticipants().addAll(getPlayers());

            for(Player p : getOnlinePlayersRed()){
                SoccerPlayer s = SoccerPlayer.get(p);
                s.addPlayedGames(1);

                redPoints += s.getPoints();
                s.updateScoreboard();
            }

            for(Player p : getOnlinePlayersBlue()){
                SoccerPlayer s = SoccerPlayer.get(p);
                s.addPlayedGames(1);

                bluePoints += s.getPoints();
                s.updateScoreboard();
            }

            new BukkitRunnable(){
                @Override
                public void run() {
                    for(Player p : getOnlinePlayers()) sendBar(p);
                }
            }.runTaskLater(SoccerMC.getInstance(),2*20);
        }
    }

    public void playGoalExplosion(Player player, Location loc){
        ChestUser up = ChestUser.getUser(player);
        VaultItem g = up.getActiveItem(ItemCategory.GOAL_EXPLOSIONS);

        if(g != null){
            int i = g.getID();

            if(i == 155){
                // SMALL EXPLOSION
                for(Player p : getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);

                    p.playSound(p.getEyeLocation(),Sound.EXPLODE,1f,1f);
                    ParticleEffect.EXPLOSION_NORMAL.display(0f,0f,0f,0,1,loc,100);
                }
            } else if(i == 156){
                // NORMAL EXPLOSION
                for(Player p : getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);

                    p.playSound(p.getEyeLocation(),Sound.EXPLODE,1f,1f);
                    ParticleEffect.EXPLOSION_LARGE.display(0f,0f,0f,0,1,loc,100);
                }
            } else if(i == 157){
                // LARGE EXPLOSION
                for(Player p : getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);

                    p.playSound(p.getEyeLocation(),Sound.EXPLODE,1f,1f);
                    ParticleEffect.EXPLOSION_HUGE.display(0f,0f,0f,0,1,loc,100);
                }
            }
        }
    }

    public static Chicken spawnChicken(Location loc){
        Chicken chicken = (Chicken)loc.getWorld().spawnEntity(loc, EntityType.CHICKEN);
        EntityCreature c = (EntityCreature) ((EntityInsentient)((CraftEntity)chicken).getHandle());

        clearFields(c);
        c.targetSelector.a(1, new PathfinderGoalHurtByTarget(c, false, new Class[0]));

        chicken.setAdult();

        net.minecraft.server.v1_8_R3.Entity nmsEntity = ((CraftEntity) chicken)
                .getHandle();
        NBTTagCompound tag = nmsEntity.getNBTTag();
        if (tag == null) {
            tag = new NBTTagCompound();
        }
        nmsEntity.c(tag);
        tag.setInt("Silent", 1);
        nmsEntity.f(tag);

        return chicken;
    }

    private static void clearFields(EntityCreature c){
        try {
            Field field = c.goalSelector.getClass().getDeclaredField("b");
            field.setAccessible(true);

            ((List<?>) field.get(c.goalSelector)).clear();
            ((List<?>) field.get(c.targetSelector)).clear();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void teleportPlayersToMap(){
        ChestAPI.sync(() -> {
            ArrayList<MapLocationData> spawnsRed = new ArrayList<MapLocationData>();
            ArrayList<MapLocationData> spawnsBlue = new ArrayList<MapLocationData>();

            switch(getTeamSize()){
                case 1:
                    spawnsRed.addAll(getMap().getLocations(MapLocationType.SOCCER_RED_1));
                    spawnsBlue.addAll(getMap().getLocations(MapLocationType.SOCCER_BLUE_1));
                    break;
                case 2:
                    spawnsRed.addAll(getMap().getLocations(MapLocationType.SOCCER_RED_2));
                    spawnsBlue.addAll(getMap().getLocations(MapLocationType.SOCCER_BLUE_2));
                    break;
                case 3:
                    spawnsRed.addAll(getMap().getLocations(MapLocationType.SOCCER_RED_3));
                    spawnsBlue.addAll(getMap().getLocations(MapLocationType.SOCCER_BLUE_3));
                    break;
                case 4:
                    spawnsRed.addAll(getMap().getLocations(MapLocationType.SOCCER_RED_4));
                    spawnsBlue.addAll(getMap().getLocations(MapLocationType.SOCCER_BLUE_4));
                    break;
            }

            Collections.shuffle(spawnsRed);
            Collections.shuffle(spawnsBlue);

            for(Player all : getOnlinePlayers()){
                SoccerPlayer.get(all).updateName();

                if(isInTeamRed(all.getUniqueId())){
                    MapLocationData data = spawnsRed.get(0);
                    all.teleport(data.toBukkitLocation(getMapWorld()));
                    spawnsRed.remove(data);
                    all.setGameMode(GameMode.ADVENTURE);
                } else if(isInTeamBlue(all.getUniqueId())){
                    MapLocationData data = spawnsBlue.get(0);
                    all.teleport(data.toBukkitLocation(getMapWorld()));
                    spawnsBlue.remove(data);
                    all.setGameMode(GameMode.ADVENTURE);
                }
            }

            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestAPI.getInstance(), new Runnable() {
                @Override
                public void run() {
                    for(Player all : getOnlinePlayers()) SoccerPlayer.get(all).updateName();
                }
            },10);
        });
    }

    public void sendBar(Player p){
        ChestUser u = ChestUser.getUser(p);

        if(isInTeamBlue(p.getUniqueId())){
            BarUtil.setBar(p,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("RED") + " " + ChatColor.WHITE.toString() + scoreRed + ChatColor.AQUA + " - " + ChatColor.WHITE.toString() + scoreBlue + " " + ChatColor.BLUE.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("BLUE"),100);
        } else {
            BarUtil.setBar(p,ChatColor.BLUE.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("BLUE") + " " + ChatColor.WHITE.toString() + scoreBlue + ChatColor.AQUA + " - " + ChatColor.WHITE.toString() + scoreRed + " " + ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("RED"),100);
        }
    }

    public void updateBar(Player p){
        ChestUser u = ChestUser.getUser(p);

        if(isInTeamRed(p.getUniqueId())){
            BarUtil.updateText(p,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("RED") + " " + ChatColor.WHITE.toString() + scoreRed + ChatColor.AQUA + " - " + ChatColor.WHITE.toString() + scoreBlue + " " + ChatColor.BLUE.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("BLUE"));
        } else {
            BarUtil.updateText(p,ChatColor.BLUE.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("BLUE") + " " + ChatColor.WHITE.toString() + scoreBlue + ChatColor.AQUA + " - " + ChatColor.WHITE.toString() + scoreRed + " " + ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("RED"));
        }
    }

    public void startChickenScheduler(){
        if(chickenScheduler == null){
            chickenScheduler = new BukkitRunnable(){
                @Override
                public void run() {
                    if(chicken != null && !chicken.isDead()){
                        boolean in = false;
                        boolean red = false;

                        if(isInRegion(chicken.getLocation(),"goalRegion_1_BLUE")){
                            in = true;
                            red = true;
                        } else if(isInRegion(chicken.getLocation(),"goalRegion_1_RED")){
                            in = true;
                            red = false;
                        }

                        if(in) {
                            if (red) { // GOAL FOR RED
                                if (lastChickenDamager != null && isInTeamRed(lastChickenDamager.getUniqueId())) {
                                    SoccerPlayer.get(lastChickenDamager).addGoals(1);
                                    SoccerPlayer.get(lastChickenDamager).addPoints(4);
                                    SoccerPlayer.get(lastChickenDamager).getUser().giveExp(5);

                                    playGoalExplosion(lastChickenDamager,chicken.getLocation());

                                    game.addSoccerGoalEvent(true, lastChickenDamager);
                                } else {
                                    game.addSoccerGoalEvent(true);
                                }

                                scoreRed += 1;
                                for(Player p : getOnlinePlayers()) updateBar(p);

                                if (scoreRed >= maxScore) {
                                    finish();
                                } else {
                                    setStatus(Status.GOAL);

                                    for (Player a : getOnlinePlayers()) {
                                        SoccerPlayer.get(a).updateScoreboard();

                                        BountifulAPI.sendTitle(a,0,20,20,ChatColor.RED.toString() + ChatColor.BOLD.toString() + ChestUser.getUser(a).getTranslatedMessage("GOAL!"), getTitleScoreSubTitle(a));

                                        if (isInTeamRed(a.getUniqueId())) {
                                            a.playSound(a.getEyeLocation(), Sound.SUCCESSFUL_HIT, 1f, 1f);
                                        } else if(isInTeamBlue(a.getUniqueId())) {
                                            a.playSound(a.getEyeLocation(), Sound.NOTE_BASS, 1f, 0.5f);
                                        }
                                    }

                                    Bukkit.getScheduler().scheduleSyncDelayedTask(SoccerMC.getInstance(), new Runnable() {
                                        public void run() {
                                            teleportPlayersToMap();
                                            startCountdown();
                                        }
                                    }, 6 * 20);
                                }

                                chicken.remove();
                                chicken = null;

                                if(chickenScheduler != null) chickenScheduler.cancel();
                                chickenScheduler = null;
                            } else { // GOAL FOR BLUE
                                if (lastChickenDamager != null && isInTeamBlue(lastChickenDamager.getUniqueId())) {
                                    SoccerPlayer.get(lastChickenDamager).addGoals(1);
                                    SoccerPlayer.get(lastChickenDamager).addPoints(4);
                                    SoccerPlayer.get(lastChickenDamager).getUser().giveExp(5);

                                    playGoalExplosion(lastChickenDamager,chicken.getLocation());

                                    game.addSoccerGoalEvent(false, lastChickenDamager);
                                } else {
                                    game.addSoccerGoalEvent(false);
                                }

                                scoreBlue += 1;
                                for(Player p : getOnlinePlayers()) updateBar(p);

                                if (scoreBlue == maxScore) {
                                    finish();
                                } else {
                                    setStatus(Status.GOAL);

                                    for (Player a : getOnlinePlayers()) {
                                        SoccerPlayer.get(a).updateScoreboard();

                                        BountifulAPI.sendTitle(a,0,20,20,ChatColor.RED.toString() + ChatColor.BOLD.toString() + ChestUser.getUser(a).getTranslatedMessage("GOAL!"), getTitleScoreSubTitle(a));

                                        if (isInTeamRed(a.getUniqueId())) {
                                            a.playSound(a.getEyeLocation(), Sound.SUCCESSFUL_HIT, 1f, 1f);
                                        } else if(isInTeamBlue(a.getUniqueId())) {
                                            a.playSound(a.getEyeLocation(), Sound.NOTE_BASS, 1f, 0.5f);
                                        }
                                    }

                                    Bukkit.getScheduler().scheduleSyncDelayedTask(SoccerMC.getInstance(), new Runnable() {
                                        public void run() {
                                            teleportPlayersToMap();
                                            startCountdown();
                                        }
                                    }, 6 * 20);
                                }

                                chicken.remove();
                                chicken = null;

                                if(chickenScheduler != null) chickenScheduler.cancel();
                                chickenScheduler = null;
                            }
                        }
                    }
                }
            }.runTaskTimer(SoccerMC.getInstance(),1L,1L);
        }
    }

    public void finish(){
        finish(getOnlinePlayersBlue().size() == 0 || scoreRed >= maxScore);
    }

    public void finish(boolean redWins){
        if(getStatus() == Status.INGAME || getStatus() == Status.WARMUP || getStatus() == Status.GOAL){
            if(redWins){
                // RED WINS
                game.getWinners().addAll(getTeamRed());

                if(chickenScheduler != null){
                    chickenScheduler.cancel();
                    chickenScheduler = null;
                }

                if(warmupCountdown != null){
                    warmupCountdown.cancel();
                    warmupCountdown = null;
                }

                if(matchCancelled != null){
                    matchCancelled.cancel();
                    matchCancelled = null;
                }

                game.setCompleted(true);
                game.saveData();
                setStatus(Status.ENDING);

                int points = ChestAPI.calculateRating(redPoints,bluePoints,1,10)/getTeamSize();

                for(Player p : getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);
                    SoccerPlayer s = SoccerPlayer.get(p);

                    s.updateScoreboard();
                    u.sendGameLogMessage(game.getID());

                    if(isInTeamRed(p.getUniqueId())){
                        p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("VICTORY!"),ChatColor.RED + u.getTranslatedMessage("Team RED has won the game!"));

                        s.addVictories(1);
                        s.addPoints(points);
                        s.updateName();
                        s.getUser().giveExp(15);

                        if(points == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have gained %s point!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have gained %s points!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        }

                        if(s.getPoints() == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s point!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s points!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        }
                    } else if(isInTeamBlue(p.getUniqueId())){
                        p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("DEFEAT!"),ChatColor.RED + u.getTranslatedMessage("Team RED has won the game!"));

                        s.reducePoints(points);
                        s.updateName();
                        s.getUser().giveExp(1);

                        if(points == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have lost %s point!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have lost %s points!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        }

                        if(s.getPoints() == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s point!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s points!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        }
                    } else {
                        p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                        BountifulAPI.sendTitle(p,0,4*20,1*20, ChatColor.GOLD + ChestUser.getUser(p).getTranslatedMessage("WINNER") + ": " + ChatColor.RED + ChestUser.getUser(p).getTranslatedMessage("RED"),getTitleScoreSubTitle(p));
                    }
                }
            } else {
                // BLUE WINS
                game.getWinners().addAll(getTeamBlue());

                if(chickenScheduler != null){
                    chickenScheduler.cancel();
                    chickenScheduler = null;
                }

                if(warmupCountdown != null){
                    warmupCountdown.cancel();
                    warmupCountdown = null;
                }

                if(matchCancelled != null){
                    matchCancelled.cancel();
                    matchCancelled = null;
                }

                game.setCompleted(true);
                game.saveData();
                setStatus(Status.ENDING);

                int points = ChestAPI.calculateRating(bluePoints,redPoints,1,20)/getTeamSize();

                for(Player p : getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);
                    SoccerPlayer s = SoccerPlayer.get(p);

                    s.updateScoreboard();
                    u.sendGameLogMessage(game.getID());

                    if(isInTeamRed(p.getUniqueId())){
                        p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("DEFEAT!"),ChatColor.BLUE + u.getTranslatedMessage("Team BLUE has won the game!"));

                        s.reducePoints(points);
                        s.updateName();
                        s.getUser().giveExp(1);

                        if(points == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have lost %s point!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have lost %s points!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        }

                        if(s.getPoints() == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s point!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s points!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        }
                    } else if(isInTeamBlue(p.getUniqueId())){
                        p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("VICTORY!"),ChatColor.BLUE + u.getTranslatedMessage("Team BLUE has won the game!"));

                        s.addVictories(1);
                        s.addPoints(points);
                        s.updateName();
                        s.getUser().giveExp(15);

                        if(points == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have gained %s point!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have gained %s points!",ChatColor.YELLOW.toString() + points + ChatColor.RED.toString()));
                        }

                        if(s.getPoints() == 1){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s point!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You now have %s points!",ChatColor.YELLOW.toString() + s.getPoints() + ChatColor.RED.toString()));
                        }
                    } else {
                        p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                        BountifulAPI.sendTitle(p,0,4*20,1*20, ChatColor.GOLD + ChestUser.getUser(p).getTranslatedMessage("WINNER") + ": " + ChatColor.RED + ChestUser.getUser(p).getTranslatedMessage("RED"),getTitleScoreSubTitle(p));
                    }
                }
            }

            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestAPI.getInstance(), new Runnable() {
                @Override
                public void run() {
                    for(Player p : getOnlinePlayers()) if(ChestUser.isLoaded(p)) ChestUser.getUser(p).connectToLobby();
                }
            }, 10*20);

            Bukkit.getScheduler().scheduleSyncDelayedTask(ChestAPI.getInstance(), new Runnable() {
                @Override
                public void run() {
                    unregister();
                }
            }, 12*20);
        }
    }

    private boolean isInRegion(Location loc, String regionID){
        boolean b = false;

        for(ProtectedRegion r : WorldGuardPlugin.inst().getRegionManager(loc.getWorld()).getApplicableRegions(loc)){
            if(r.getId().equalsIgnoreCase(regionID)){
                b = true;
            }
        }

        return b;
    }

    private String getTitleScoreSubTitle(Player p){
        if(teamBlue.contains(p)){
            return ChatColor.BLUE + ChestUser.getUser(p).getTranslatedMessage("BLUE") + " " + ChatColor.WHITE + scoreBlue + " " + ChatColor.GRAY + "-" + " " + ChatColor.WHITE + scoreRed + " " + ChatColor.RED + ChestUser.getUser(p).getTranslatedMessage("RED");
        } else {
            return ChatColor.RED + ChestUser.getUser(p).getTranslatedMessage("RED") + " " + ChatColor.WHITE + scoreRed + " " + ChatColor.GRAY + "-" + " " + ChatColor.WHITE + scoreBlue + " " + ChatColor.BLUE + ChestUser.getUser(p).getTranslatedMessage("BLUE");
        }
    }

    public void startCountdown(){
        if(warmupCountdown == null){
            warmupCountdownCount = 5;
            setStatus(Status.WARMUP);

            if(chicken != null){
                chicken.remove();
                chicken = null;
            }

            warmupCountdown = new BukkitRunnable(){
                @Override
                public void run() {
                    switch(warmupCountdownCount){
                        case 5:
                            chicken = spawnChicken(getMap().getLocations(MapLocationType.SOCCER_CHICKEN).get(0).toBukkitLocation(getMapWorld()));
                            for(Player all : Bukkit.getOnlinePlayers()) if(ChestUser.isLoaded(all)) SoccerPlayer.get(all).handleVanishing();

                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.GREEN.toString() + warmupCountdownCount,"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            }
                            break;
                        case 4:
                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.YELLOW.toString() + warmupCountdownCount,"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            }
                            break;
                        case 3:
                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.GOLD.toString() + warmupCountdownCount,"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            }
                            break;
                        case 2:
                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.RED.toString() + warmupCountdownCount,"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            }
                            break;
                        case 1:
                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.DARK_RED.toString() + warmupCountdownCount,"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_BASS,1f,1f);
                            }
                            break;
                        case 0:
                            for(Player all : getOnlinePlayers()){
                                BountifulAPI.sendTitle(all,0,2*20,0, ChatColor.DARK_GREEN.toString() + ChestUser.getUser(all).getTranslatedMessage("GO!"),"");
                                all.playSound(all.getEyeLocation(), Sound.NOTE_PLING,1f,1f);

                                SoccerPlayer.get(all).giveItems();
                            }

                            startChickenScheduler();
                            setStatus(Status.INGAME);

                            cancel();
                            warmupCountdown = null;

                            break;
                    }

                    warmupCountdownCount--;
                }
            }.runTaskTimer(SoccerMC.getInstance(),3*20,20);
        }
    }

    public void saveData(){
        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `soccer_upcomingGames` SET `status` = ?, `server` = ?, `map` = ? WHERE `id` = ?");
                ps.setString(1,getStatus().toString());
                ps.setString(2, ServerUtil.getServerName());
                ps.setInt(3,getMap().getID());
                ps.setInt(4,getId());
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });
    }

    public void unregister(){
        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("DELETE FROM `soccer_upcomingGames` WHERE `id` = ?");
            ps.setInt(1,this.id);
            ps.executeUpdate();
            ps.close();

            if(game != null) game.saveData();
            map.removeMap(getMapWorld());

            STORAGE.remove(getId());
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public enum Status {
        PREPARING,WARMUP,INGAME,GOAL,ENDING,CANCELLED
    }
}
