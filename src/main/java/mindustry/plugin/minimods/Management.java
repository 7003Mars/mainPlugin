package mindustry.plugin.minimods;

import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import mindustry.net.Packets;
import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.Vars;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class Management implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {}

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("gc", "",
            data -> {
                data.help = "Trigger a garbage collection. Testing only.";
                data.roles = new long[] { Roles.MOD, Roles.ADMIN };
                data.category = "Management";
            },
            ctx -> {
                double pre = (Core.app.getJavaHeap() / 1024.0 / 1024.0);
                System.gc();
                double post = (Core.app.getJavaHeap() / 1024.0 / 1024.0);

                ctx.sendEmbed(new EmbedBuilder()
                    .setColor(Color.YELLOW)
                    .setTitle("Garbage Collected")
                    .setDescription((post-pre) + " MB of garbage collected")
                    .addInlineField("Pre-GC usage", pre + " MB")
                    .addInlineField("Post-GC usage", post + " MB")
                );
            }
        );

        handler.register("config", "[name] [value...]", d -> {
            d.help = "Configure server settings.";
            d.category = "Management";
            d.roles =  new long[] { Roles.ADMIN };
        }, ctx -> {
            if (!ctx.args.containsKey("name")) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("All config values:");
                for (Administration.Config c : Administration.Config.all) {
//                            info("&lk| @: @", c.name(), "&lc&fi" + c.get());
//                            info("&lk| | &lw" + c.description);
//                            info("&lk|");
                    eb.addField(c.name() + ": " + c.get(), c.description, true);
                }
                eb.setColor(Color.CYAN);
                ctx.sendEmbed(eb);
                return;
            }

            try {
                Administration.Config c = Administration.Config.valueOf(ctx.args.get("name"));
                if (!ctx.args.containsKey("value")) {
                    ctx.sendEmbed(Color.CYAN, "Configuration", c.name() + " is currently <" + c.getClass().getName() + "> " + c.get());
                    return;
                }

                Object previousValue = c.get();

                if (c.isBool()) {
                    c.set(ctx.args.get("value").equals("true") || ctx.args.get("value").equals("on"));
                } else if (c.isNum()) {
                    c.set(ctx.args.getInt("value", 0));
                } else {
                    c.set(ctx.args.get("value").replace("\\n", "\n"));
                }
                
                ctx.sendEmbed(Color.CYAN, "Configuration", c.name() + " is now set to <" + c.getClass().getName() + "> " + c.get() + "\n\nPrevious Value: " + previousValue);
            } catch(IllegalArgumentException e) {
                ctx.error("Unknown Configuration", e.getMessage());
            }
        });

        handler.register("uploadmod", "[.zip]", d -> {
            d.help = "Upload mod (include .zip file in message)";
            d.category = "Management";
            d.roles = new long[] { Roles.ADMIN };
            d.aliases = new String[] { "umod" };
        }, ctx -> {
            Seq<MessageAttachment> ml = new Seq<>();
            for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                if (ma.getFileName().split("\\.")[ma.getFileName().split("\\.").length - 1].trim().equals("zip")) {
                    ml.add(ma);
                }
            }
            if (ml.size != 1) {
                ctx.error("Error", "Must have exactly one valid .zip file!");
                return;
            } else if (Core.settings.getDataDirectory().child("mods").child(ml.get(0).getFileName()).exists()) {
                ctx.error("Error", "Already a mod with this name!");
                return;
            }

            CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
            Fi fh = Core.settings.getDataDirectory().child("mods").child(ml.get(0).getFileName());

            try {
//                byte[] data = cf.get();
//                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
//                            eb.setTitle("Mod upload terminated.");
//                            eb.setColor(Pals.error);
//                            eb.setDescription("Mod file corrupted or invalid.");
//                            ctx.sendMessage(eb);
//                            return;
//                        }
                fh.writeBytes(cf.get(), false);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Vars.maps.reload();
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Mod upload completed.");
            eb.setDescription(ml.get(0).getFileName() + " was added successfully into the mod folder!\n" +
                    "Restart the server (`<restart <server>`) to activate the mod!");
            ctx.sendEmbed(eb);
        });

        handler.register("removemod", "<modname/id>", d -> {
            d.help = "Remove a mod from the folder";
            d.roles = new long[] { Roles.ADMIN };
            d.category = "Management";
            d.aliases = new String[] { "rmod" };
        }, ctx -> {
            var mod = Utils.getMod(ctx.args.get("modname/id"));
            if (mod == null) {
                ctx.error("Error", "Mod '" + ctx.args.get("modname/id") + "' not found");
                return;
            }
            Log.debug("Mod absolute path: " + mod.file.file().getAbsoluteFile().getAbsolutePath());
            Path path = Paths.get(mod.file.file().getAbsoluteFile().getAbsolutePath());
            String name = mod.name;
            mod.dispose();
            
            try { 
                Files.delete(path);
            } catch(Exception e) {
                e.printStackTrace();
                ctx.error("Unknown error", e.getMessage());
                return;
            }

            if (mod.file.file().delete()) {
                ctx.success("Deleted Mod", "Successfully deleted mod " + Utils.escapeColorCodes(name) + ". Restart the server (`<restart <server>`) to deactivate the mod!");
            } else {
                ctx.error("Error", "Unable to delete mod? (The mod may still be deleted, not sure why the code deletes thrice).");
            }
        });

        handler.register("start", "[map] [mode]", d -> {
            d.help = "Restart the server";
            d.roles = new long[] { Roles.ADMIN };
            d.category = "Management";
            d.aliases = new String[] { "restart" };
        }, ctx -> {
            Vars.net.closeServer();
            Vars.state.set(GameState.State.menu);

            Gamemode mode = Gamemode.survival;
            if (ctx.args.containsKey("mode")) {
                try {
                    mode = Gamemode.valueOf(ctx.args.get("mode"));
                } catch(IllegalArgumentException e) {
                    ctx.error("Invalid Gamemode", "'" + ctx.args.get("mode") + "' is not a valid game mode.");
                    return;
                }
            }

            Map map = Vars.maps.getShuffleMode().next(mode, Vars.state.map);
            if (ctx.args.containsKey("map")) {
                map = Utils.getMapBySelector(ctx.args.get("map"));
                if (map == null) { 
                    ctx.error("Invalid Map", "Map '" + ctx.args.get("map") + "' does not exist");
                }
            }

            try {
                Vars.world.loadMap(map, map.applyRules(mode));
                Vars.state.rules = map.applyRules(mode);
                Vars.logic.play();
                Vars.netServer.openServer();

                ctx.success("Map Loaded", "Hosting map: " + map.name());
            } catch (MapException e) {
                ctx.error("Internal Error", e.map.name() + ": " + e.getMessage());
            }
        });

        handler.register("exit", "", d -> {
            d.help = "Close the server.";
            d.roles = new long[] { Roles.ADMIN };
            d.category = "Management";
        }, ctx -> {
            Channels.LOG.sendMessage(new EmbedBuilder()
                .setTitle(ctx.author().getDisplayName(ctx.server()) + " closed the server!")
                .setColor(new Color(0xff0000))).join();
            ctx.success("Success", "Closed the server");

            Log.info("&ly--SERVER RESTARTING--");
            Vars.netServer.kickAll(Packets.KickReason.serverRestarting);
            Time.runTask(5f, () -> {
                Core.app.exit();
            });
        });
    }
}
