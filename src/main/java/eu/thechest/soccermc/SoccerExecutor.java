package eu.thechest.soccermc;

import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SoccerExecutor implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("toggleparticles")){
            if(sender instanceof Player){
                Player p = (Player)sender;

                if(ChestUser.isLoaded(p)){
                    ChestUser u = ChestUser.getUser(p);
                    SoccerPlayer s = SoccerPlayer.get(p);

                    if(s.seesParticles()){
                        s.setSeeParticles(false);
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Disabled help particles!"));
                    } else {
                        s.setSeeParticles(true);
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Enabled help particles!"));
                    }
                }
            }
        }

        return false;
    }
}
