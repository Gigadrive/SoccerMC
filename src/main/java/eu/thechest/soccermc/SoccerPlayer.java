package eu.thechest.soccermc;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.util.PlayerUtilities;
import eu.thechest.chestapi.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import javax.naming.ldap.PagedResultsControl;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.UUID;

public class SoccerPlayer {
    public static HashMap<Player,SoccerPlayer> STORAGE = new HashMap<Player,SoccerPlayer>();

    public static SoccerPlayer get(Player p){
        if(STORAGE.containsKey(p)){
            return STORAGE.get(p);
        } else {
            new SoccerPlayer(p);

            if(STORAGE.containsKey(p)){
                return STORAGE.get(p);
            } else {
                return null;
            }
        }
    }

    public static void unregister(Player p){
        if(STORAGE.containsKey(p)){
            STORAGE.remove(p).saveData();
        }
    }

    private Player p;

    private int startPoints;
    private int startGoals;
    private int startVictories;
    private int startPlayedGames;

    private int points;
    private int goals;
    private int victories;
    private int playedGames;

    private boolean particles;

    public SoccerPlayer(Player p){
        this.p = p;
        if(STORAGE.containsKey(p)) return;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `soccer_stats` WHERE `uuid` = ?");
            ps.setString(1,p.getUniqueId().toString());

            ResultSet rs = ps.executeQuery();
            if(rs.first()){
                this.startPoints = rs.getInt("points");
                this.startGoals = rs.getInt("goals");
                this.startVictories = rs.getInt("victories");
                this.startPlayedGames = rs.getInt("playedGames");
                this.particles = rs.getBoolean("setting.particles");

                STORAGE.put(p,this);
            } else {
                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `soccer_stats` (`uuid`) VALUES(?);");
                insert.setString(1,p.getUniqueId().toString());
                insert.executeUpdate();
                insert.close();

                new SoccerPlayer(p);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public Player getPlayer() {
        return p;
    }

    public ChestUser getUser(){
        return ChestUser.getUser(p);
    }

    public void addPoints(int points){
        for(int i = 0; i < points; i++){
            //if((startPoints+this.points+i)<=0) break;

            this.points++;
        }
    }

    public void reducePoints(int points){
        for(int i = 0; i < points; i++){
            if((startPoints+this.points+(i/-1))<=0) break;

            this.points--;
        }
    }

    public int getPoints(){
        return this.startPoints+this.points;
    }

    public void addGoals(int i){
        this.goals += i;
    }

    public int getGoals(){
        return this.startGoals+this.goals;
    }

    public void addVictories(int i){
        this.victories += i;
    }

    public int getVictories(){
        return this.startVictories+this.victories;
    }

    public void addPlayedGames(int i){
        this.playedGames += i;
    }

    public int getPlayedGames(){
        return this.startPlayedGames+this.playedGames;
    }

    public boolean seesParticles(){
        return this.particles;
    }

    public void setSeeParticles(boolean b){
        this.particles = b;
    }

    public SoccerGame getGame(){
        return SoccerGame.getGameByPlayer(p);
    }

    public void updateScoreboard(){
        if(getGame() != null && (getGame().getStatus() == SoccerGame.Status.WARMUP || getGame().getStatus() == SoccerGame.Status.INGAME || getGame().getStatus() == SoccerGame.Status.GOAL)){
            Objective obj;
            if(getUser().getScoreboard().getObjective(DisplaySlot.SIDEBAR) != null){
                obj = getUser().getScoreboard().getObjective(DisplaySlot.SIDEBAR);
            } else {
                obj = getUser().getScoreboard().registerNewObjective("side","dummy");
            }
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(ChatColor.AQUA + "Soccer");

            int s = -1;
            if(getGame().getTeamSize() == 1) s = 9;
            if(getGame().getTeamSize() == 2) s = 11;
            if(getGame().getTeamSize() == 3) s = 13;
            if(getGame().getTeamSize() == 4) s = 15;

            obj.getScore(" ").setScore(s--);
            obj.getScore(ChatColor.AQUA + ">> " + ChatColor.BLUE.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Team BLUE")).setScore(s--);
            for(UUID uuid : getGame().getTeamBlue()){
                String name = PlayerUtilities.getNameFromUUID(uuid);
                if(Bukkit.getPlayer(uuid) != null){
                    SoccerPlayer.get(Bukkit.getPlayer(uuid)).updateName();
                } else {
                    getUser().setPlayerPrefix(name,ChatColor.DARK_GRAY.toString() + ChatColor.STRIKETHROUGH.toString());
                }

                obj.getScore(name).setScore(s--);
            }

            obj.getScore("  ").setScore(s--);
            obj.getScore(ChatColor.DARK_RED + ">> " + ChatColor.RED.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Team RED")).setScore(s--);

            for(UUID uuid : getGame().getTeamRed()){
                String name = PlayerUtilities.getNameFromUUID(uuid);
                if(Bukkit.getPlayer(uuid) != null){
                    SoccerPlayer.get(Bukkit.getPlayer(uuid)).updateName();
                } else {
                    getUser().setPlayerPrefix(name,ChatColor.DARK_GRAY.toString() + ChatColor.STRIKETHROUGH.toString());
                }

                obj.getScore(name).setScore(s--);
            }

            obj.getScore("   ").setScore(s--);
            obj.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(s--);
            obj.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(s--);
        } else {
            getUser().clearScoreboard();
        }
    }

    public void handleVanishing(){
        for(Player all : Bukkit.getOnlinePlayers()){
            if(all == p) continue;

            if(getGame() != null){
                if(getGame().getOnlinePlayers().contains(all)){
                    p.showPlayer(all);
                } else {
                    p.hidePlayer(all);
                }
            } else {
                p.hidePlayer(all);
            }
        }

        /*for(SoccerGame g : SoccerGame.STORAGE.values()){
            if(getGame() == null || getGame() != g){
                if(g.chicken != null && !g.chicken.isDead()){
                    getUser().hideEntity(g.chicken);
                }
            }
        }*/
    }

    public void giveItems(){
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);

        if(getGame() != null){
            boolean giveShovels = false;

            if(getGame().isInTeamBlue(p.getUniqueId())){
                ItemStack plate = new ItemStack(Material.LEATHER_CHESTPLATE);
                LeatherArmorMeta plateMeta = (LeatherArmorMeta)plate.getItemMeta();
                plateMeta.setDisplayName(ChatColor.BLUE + getUser().getTranslatedMessage("BLUE"));
                plateMeta.setColor(Color.fromRGB(0,0,255));
                plate.setItemMeta(plateMeta);

                p.getInventory().setChestplate(plate);

                giveShovels = true;
            } else if(getGame().isInTeamRed(p.getUniqueId())){
                ItemStack plate = new ItemStack(Material.LEATHER_CHESTPLATE);
                LeatherArmorMeta plateMeta = (LeatherArmorMeta)plate.getItemMeta();
                plateMeta.setDisplayName(ChatColor.RED + getUser().getTranslatedMessage("RED"));
                plateMeta.setColor(Color.fromRGB(255,0,0));
                plate.setItemMeta(plateMeta);

                p.getInventory().setChestplate(plate);

                giveShovels = true;
            }

            if(giveShovels){
                ItemStack wood = ItemUtil.namedItem(Material.WOOD_SPADE, "I", null);
                wood.addUnsafeEnchantment(Enchantment.KNOCKBACK, 0);
                wood = ItemUtil.setUnbreakable(wood, true);
                wood = ItemUtil.hideFlags(wood);

                ItemStack iron = ItemUtil.namedItem(Material.IRON_SPADE, "II", null);
                iron.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
                iron = ItemUtil.setUnbreakable(iron, true);
                iron = ItemUtil.hideFlags(iron);

                ItemStack diamond = ItemUtil.namedItem(Material.DIAMOND_SPADE, "III", null);
                diamond.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
                diamond = ItemUtil.setUnbreakable(diamond, true);
                diamond = ItemUtil.hideFlags(diamond);

                p.getInventory().setItem(0, wood);
                p.getInventory().setItem(1, iron);
                p.getInventory().setItem(2, diamond);
            }
        }
    }

    public void updateName(){
        if(getGame() != null){
            for(Player all : getGame().getOnlinePlayers()){
                if(ChestUser.isLoaded(all)){
                    ChestUser a = ChestUser.getUser(all);
                    all.setScoreboard(a.getScoreboard());

                    if(getGame().isInTeamRed(p.getUniqueId())){
                        a.setPlayerPrefix(p.getName(), ChatColor.RED + ChatColor.BOLD.toString() + "[" + a.getTranslatedMessage("RED") + "] " + getUser().getRank().getColor());
                        a.setPlayerSuffix(p.getName()," " + ChatColor.GRAY + "[" + getPoints() + "]");
                    } else if(getGame().isInTeamBlue(p.getUniqueId())){
                        a.setPlayerPrefix(p.getName(), ChatColor.BLUE + ChatColor.BOLD.toString() + "[" + a.getTranslatedMessage("BLUE") + "] " + getUser().getRank().getColor());
                        a.setPlayerSuffix(p.getName()," " + ChatColor.GRAY + "[" + getPoints() + "]");
                    } else if(getGame().getSpectators().contains(p)){
                        a.setPlayerPrefix(p.getName(), ChatColor.AQUA + ChatColor.BOLD.toString() + "[SPEC] " + getUser().getRank().getColor());
                        a.setPlayerSuffix(p.getName(),"");
                    }
                }
            }
        }
    }

    public void saveData(){
        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `soccer_stats` SET `points`=`points`+?, `monthlyPoints`=`monthlyPoints`+?, `goals`=`goals`+?, `victories`=`victories`+?, `playedGames`=`playedGames`+?, `setting.particles` = ? WHERE `uuid` = ?");
                ps.setInt(1,points);
                ps.setInt(2,points);
                ps.setInt(3,goals);
                ps.setInt(4,victories);
                ps.setInt(5,playedGames);
                ps.setBoolean(6,particles);
                ps.setString(7,p.getUniqueId().toString());
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });
    }
}
