package mindustry.plugin.minimods;

import arc.Core;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;

import static mindustry.Vars.mods;

public class JS implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("js", "<code>",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                    data.category = "Moderation";
                    data.help = "Run JS code";
                },
                ctx -> {
                    Core.app.post(() -> {
                        String res = mods.getScripts().runConsole(ctx.args.get("code"));
                        ctx.success("Ran code", "Output:\n```\n" + res + "\n```");
                    });
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
            if (player.admin) {
                player.sendMessage(mods.getScripts().runConsole(arg[0]));
            } else {
                player.sendMessage(GameMsg.noPerms("JS"));
            }
        });
    }
}
