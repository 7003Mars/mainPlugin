package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.*;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.effect.EffectHelper;
import mindustry.plugin.utils.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.util.logging.FallbackLoggerConfiguration;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.time.Instant;
import java.util.Objects;

import static arc.util.Log.*;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static mindustry.plugin.database.Database.*;
import static mindustry.plugin.discord.Channels.LIVE_LOG;
import static mindustry.plugin.minimods.Communication.autoScreenMessages;
import static mindustry.plugin.minimods.Logs.live_log_message;
import static mindustry.plugin.minimods.Ranks.newPlayers;
import static mindustry.plugin.utils.Utils.escapeEverything;
import static mindustry.plugin.utils.Utils.getArrayListFromString;

public class PhoenixMain extends Plugin {
    //    public static final File prefsFile = new File("prefs.properties");
//    public static Net net = new Net();
//    public static Prefs prefs = new Prefs(prefsFile);
//    public GetMap map = new GetMap();
    private static final String lennyFace = "( \u0361\u00B0 \u035C\u0296 \u0361\u00B0)";
    public static ContentHandler contentHandler;

    protected Seq<MiniMod> minimods = Seq.with(
            new mindustry.plugin.minimods.Communication(),
            new mindustry.plugin.minimods.Cheats(),
            new mindustry.plugin.minimods.Events(),
            new mindustry.plugin.minimods.GameInfo(),
            new mindustry.plugin.minimods.Inspector(),
            new mindustry.plugin.minimods.JS(),
            new mindustry.plugin.minimods.Kick(),
            new mindustry.plugin.minimods.Undo(),
            new mindustry.plugin.minimods.Logs(),
            new mindustry.plugin.minimods.Management(),
            new mindustry.plugin.minimods.Maps(),
            new mindustry.plugin.minimods.Moderation(),
            new mindustry.plugin.minimods.ModLog(),
            new mindustry.plugin.minimods.Mods(),
            new mindustry.plugin.minimods.Rainbow(),
            new mindustry.plugin.minimods.Ranks(),
            new mindustry.plugin.minimods.Redeem(),
            new mindustry.plugin.minimods.Report(),
            new mindustry.plugin.minimods.ServerInfo(),
            new mindustry.plugin.minimods.Skipwave(),
            new mindustry.plugin.minimods.Translate(),
            new mindustry.plugin.minimods.Weapon(),
            new mindustry.plugin.minimods.Sessions()
    );

    // register event handlers and create variables in the constructor
    public PhoenixMain() {
        info("Starting Discord Plugin...");
        info(lennyFace);

        // disable debug logs from javacord (doesnt work tho, idk why)
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);
        Log.level = Log.LogLevel.debug;

        DiscordApi api;
        DiscordRegistrar registrar = null;
        // read settings
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            JSONObject data = new JSONObject(new JSONTokener(pureJson));

            // url to connect to the MindServ
//            Config.mapsURL = data.getString("maps_url");

            JSONObject discordData = data.getJSONObject("discord");
            DiscordVars.invite = discordData.optString("invite");
            String discordToken = discordData.getString("token");
            try {
                api = new DiscordApiBuilder().setToken(discordToken).login().join();
                Log.info("Logged in as: " + api.getYourself());
                DiscordVars.api = api;
            } catch (Exception e) {
                Log.err("Couldn't log into discord.");
                Core.app.exit();
                return;
            }
            Channels.load(api, discordData.optJSONObject("channels"));
            Roles.load(api, discordData.getJSONObject("roles"));
            String discordPrefix = discordData.optString("prefix", "%");
            DiscordVars.prefix = discordPrefix;
            registrar = new DiscordRegistrar(discordPrefix);

            Config.serverName = data.getString("server_name");
            Config.ipApiKey = data.optString("ipapi_key");

            JSONObject configData = data.getJSONObject("config");
            Config.previewSchem = configData.getBoolean("preview_schem");
            Config.assetsDir = configData.getString("assets_dir");
            if (configData.has("map_rating")) {
                Config.mapRating = configData.getBoolean("map_rating");
            }
            if (configData.has("beta")) {
                Config.beta = configData.getBoolean("beta");
            }
            if (configData.has("img_auto_ban_system")) {
                Config.autoBanSystem = configData.getBoolean("img_auto_ban_system");
            }

            // connect to database
            JSONObject databaseData = data.getJSONObject("database");
            String dbURL = databaseData.getString("url");
            String dbUser = databaseData.getString("user");
            String dbPwd = databaseData.getString("password");
            String playerTable = databaseData.getString("table_player");

            debug("database url: @, user: @", dbURL, dbUser);

            try { // test connection
                Database.connect(dbURL, dbUser, dbPwd, playerTable);
            } catch (Exception e) {
                err(e.toString());
                err("Could not login to PostgresSQL database!");
            }

            if (data.has("minimods")) {
                JSONObject minimodsData = data.getJSONObject("minimods");
                if (!(minimodsData.has("pets") && !minimodsData.getBoolean("pets"))) {
                    minimods.add(new mindustry.plugin.minimods.Pets());
                }
                if (!(minimodsData.has("rtv") && !minimodsData.getBoolean("rtv"))) {
                    minimods.add(new mindustry.plugin.minimods.RTV());
                }
            }
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
            e.printStackTrace();
            return;
        }

        try {
            for (MiniMod mod : minimods) {
                mod.registerDiscordCommands(registrar);
            }
        } catch (Exception e) {
            Log.err(e);
            Core.app.exit();
        }

        DiscordRegistrar finalRegistrar = registrar;
        registrar.register("help", "[cmd]", data -> {
            data.help = "Display information about commands";
            data.aliases = new String[]{"h"};
        }, ctx -> {
            if (ctx.args.containsKey("cmd") && !ctx.args.get("cmd").equals("all")) {
                ctx.sendEmbed(finalRegistrar.helpEmbed(ctx.args.get("cmd")));
            } else {
                boolean all = ctx.args.containsKey("all");
                ctx.sendEmbed(finalRegistrar.helpEmbed(all ? null : ctx.author()));
            }
        });

        // Initialize discord stuff
        for (var b : Channels.BOT) {
            b.addMessageCreateListener(registrar::dispatchEvent);
        }
        Channels.ADMIN_BOT.addMessageCreateListener(registrar::dispatchEvent);
        Channels.APPRENTICE_BOT.addMessageCreateListener(registrar::dispatchEvent);
        Channels.MOD_BOT.addMessageCreateListener(registrar::dispatchEvent);

//        Channels.ADMIN_BOT.addMessageEditListener(registrar::dispatchEvent);

        // Log startup
        var startupEmbed = new EmbedBuilder()
                .setTitle("Starting Server")
                .setColor(DiscordPalette.ERROR);
        var startupMessage = new MessageBuilder()
                .setEmbed(startupEmbed);
        Channels.COLONEL_LOG.sendMessage(startupEmbed);
        startupMessage.send(Channels.LOG);

        Utils.init();
        EffectHelper.init();
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        // Update discord status
        DiscordVars.api.updateActivity("Loading...");
        Timer.schedule(this::updateDiscordStatus, 30, 60);

        try {
            live_log_message = LIVE_LOG.sendMessage(new EmbedBuilder()
                    .setTitle("Server started")
                    .setDescription("Logging everything here, update every 30s.")
                    .setColor(DiscordPalette.SUCCESS)
                    .setTimestampToNow()
            ).get().getId();
            // get all old messages from this bot in LIVE_LOG channel and delete them
            Channels.LIVE_LOG.getMessages(100).thenAccept(messages -> {
                for (Message message : messages) {
                    if (message.getAuthor().isYourself() && message.getId() != live_log_message) {
                        message.delete();
                    }
                }
            });
        } catch (Exception e) {
            err(e);
        }

        updateBannedWordsClient();

        autoScreenMessages = getArrayListFromString(Core.settings.getString("autoscreenmessages", "[]"));

        // delete all duplicate names
        Database.deleteAllDuplicateNames();
        Events.on(EventType.ServerLoadEvent.class, event -> {
            if (Config.autoBanSystem) contentHandler = new ContentHandler();
            DiscordVars.api.updateActivity(
                    Strings.stripColors(Vars.state.map.name()) +
                            " with " + Groups.player.size() +
                            (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players");
            Log.info("Everything's loaded !");
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            String[] bannedNames = new String[]{
                    "IGGGAMES",
                    "CODEX",
                    "VALVE",
                    "tuttop",
                    "Volas Y0uKn0w1sR34Lp",
                    "IgruhaOrg",
                    "андрей",
                    "THIS IS MY KINGDOM CUM, THIS IS MY CUM",
                    "HITLER"
            };
            if (Structs.contains(bannedNames, s -> s.toLowerCase().equals(escapeEverything(player.name.toLowerCase())))) {
                player.con.kick("[scarlet]Please change your name.");
                return;
            }

            // check if the player is already in the database
            Database.Player pd = Database.getPlayerData(player.uuid());

            // check if he's impersonating a rank
            for (int i = 0; i < Rank.all.length; i++) {
                if (i == 0) continue;

                Rank rank = Rank.all[i];
                if (player.name.toLowerCase().contains(rank.tag)) {
                    player.con.kick("[scarlet]Dont impersonate a rank.");
                    Log.warn("Player " + Strings.stripColors(player.name) + " tried to impersonate rank: " + rank.name);
                    return;
                }
            }

            // check for ban & give name
            if (pd != null) {
                if (pd.banned || pd.bannedUntil > Instant.now().getEpochSecond()) {
                    player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason + "\n[white] If you want to appeal join our discord server: [cyan]" + DiscordVars.invite);
                    return;
                }

                Rank rank = Rank.all[pd.rank];
                Call.sendMessage(Strings.format("[#@]@ []@[][@] [accent]joined the front!",
                        rank.color.toString().substring(0, 6), rank.name, player.name, Utils.calculatePhash(player.uuid())));
                player.name = Utils.formatName(pd, player);

//                // Give Marshals admin
//                if (pd.rank == Rank.all.length - 1) {
//                    player.admin = true;
//                }
                if (pd.playTime < 60) {
                    pd.verified = false;
                    newPlayers.put(player.uuid(), new Utils.Pair<>(pd.playTime, pd.buildingsBuilt));
                } else {
                    pd.verified = true;
                }
                Database.setPlayerData(pd);
            } else { // not in database
                info("New player connected: " + Strings.stripColors(event.player.name));
                pd = new Database.Player(player.uuid(), 0);
                Database.setPlayerData(pd);


                Rank rank = Rank.all[0];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + player.name + "[accent] joined the front!");
                pd.verified = false;
                Database.setPlayerData(pd);
                newPlayers.put(player.uuid(), new Utils.Pair<>(0, 0));
            }
            // send message to everyone: x has joined y times and has been kicked z times
            var info = Query.findPlayerInfo(player.uuid());
            Call.sendMessage(player.name + "[accent] has joined [scarlet]" + info.timesJoined + "[accent] times and has been kicked [scarlet]" + info.timesKicked + "[accent] times.");
            Call.infoMessage(player.con, Utils.Message.welcome());

            // update names database
            // first check if current name is already in the database
            if (!Objects.requireNonNull(getNames(player.uuid())).contains(player.name)) {
                // add name to database
                saveName(player.uuid(), player.name);
            }

//
//            CompletableFuture.runAsync(() -> {
//                if(verification) {
//                    if (pd != null && !pd.verified) {
//                        CustomLog.info("Unverified player joined: " + player.name);
//                        String url = "http://api.vpnblocker.net/v2/json/" + player.con.address + "/" + apiKey;
//                        String pjson = ClientBuilder.newClient().target(url).request().accept(MediaType.APPLICATION_JSON).get(String.class);
//
//                        JSONObject json = new JSONObject(new JSONTokener(pjson));
//                        if (json.has("host-ip")) {
//                            if (json.getBoolean("host-ip")) { // verification failed
//                                CustomLog.info("IP verification failed for: " + player.name);
//                                Call.onInfoMessage(player.con, verificationMessage);
//                            } else {
//                                CustomLog.info("IP verification success for: " + player.name);
//                                pd.verified = true;
//                                setData(player.uuid(), pd);
//                            }
//                        } else { // site doesn't work for some reason  ?
//                            pd.verified = true;
//                            setData(player.uuid(), pd);
//                        }
//                    }
//                }
//            });
//            player.sendMessage(welcomeMessage);

        });

        // Log game over
        Events.on(EventType.GameOverEvent.class, event -> {
            if (Groups.player.size() > 0) {
                EmbedBuilder gameOverEmbed = new EmbedBuilder().setTitle("Game over!").setDescription("Map " + escapeEverything(state.map.name()) + " ended with " + state.wave + " waves and " + Groups.player.size() + " players!").setColor(DiscordPalette.INFO);
                Channels.LOG.sendMessage(gameOverEmbed);
                for (var c : Channels.CHAT) {
                    c.sendMessage(gameOverEmbed);
                }
            }
        });

        // TODO: remove this when MapRules is back in use
        Cooldowns.instance.set("rotate", 0);
        Cooldowns.instance.set("configure", 1);
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Vars.netServer.admins.addActionFilter(action -> {
                Player player = action.player;
//                if (player == null) return true; // Play should never be null
                if (player.admin) return true;

                switch (action.type) {
                    case rotate -> {
                        if (!Cooldowns.instance.canRun("rotate", player.uuid())) {
                            player.sendMessage(GameMsg.error("Mod", "Rotate ratelimit exceeded, please rotate slower"));
                            return false;
                        }
                    }
                    case configure -> {
                        if (!Cooldowns.instance.canRun("configure", player.uuid())) {
                            player.sendMessage(GameMsg.error("Mod", "Configure ratelimit exceeded, please configure slower"));
                            return false;
                        }
                    }
                    default -> {
                        return true;
                    }
                }
                return true;
            });

            final ObjectMap<String, String> lastMessages = new ObjectMap<>();
            Cooldowns.instance.set("t", 1);
            Vars.netServer.admins.addChatFilter((player, message) -> {
                if (!Cooldowns.instance.canRun("t", player.uuid())) {
                    player.sendMessage(GameMsg.error("Comms", "Exceeded rate limit of 1 second."));
                    return null;
                }

                if (lastMessages.get(player.uuid(), "").equalsIgnoreCase(message)) {
                    player.sendMessage(GameMsg.error("Comms", "Sending the same message twice is not allowed."));
                    return null;
                }
                lastMessages.put(player.uuid(), message);

                return message;
            });

            info("Registered all filters.");
        });

        for (MiniMod minimod : minimods) {
            minimod.registerEvents();
        }
    }

    public void updateDiscordStatus() {
        if (Vars.state.is(GameState.State.playing) || Vars.state.is(GameState.State.paused)) {
            DiscordVars.api.updateActivity(
                    Utils.escapeColorCodes(Vars.state.map.name()) +
                            " with " + Groups.player.size() +
                            (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players");
        } else {
            DiscordVars.api.updateActivity(ActivityType.CUSTOM, "Not hosting");
            DiscordLog.error("Server crashed", "Restarting...", null);
            Core.app.exit();
            System.exit(1);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("logging", "<trace/debug> <true/false>", "Enable or disable logging for javacord.", args -> {
            if (!Objects.equals(args[1], "false") && !Objects.equals(args[1], "true")) {
                err("Second argument has to be true or false!");
            }
            switch (args[0]) {
                case "trace", "t" -> {
                    FallbackLoggerConfiguration.setTrace(Objects.equals(args[1], "true"));
                    info("Set trace logging to " + args[1]);
                }
                case "debug", "d" -> {
                    FallbackLoggerConfiguration.setDebug(Objects.equals(args[1], "true"));
                    info("Set debug to " + args[1]);
                }
                default -> {
                    err("Please select either trace or debug!");
                }
            }
        });

        for (MiniMod mod : minimods) {
            mod.registerServerCommands(handler);
        }


        Vars.netServer.addPacketHandler("playerdata_by_id", (player, identifier) -> {
            var p = Groups.player.getByID(Integer.parseInt(identifier));
            if (p == null) return;
            var target = Database.getPlayerData(p.uuid());
            if (target == null) return;

            JSONObject data = new JSONObject();

            data.put("id", p.id);
            data.put("name", p.name);
            data.put("realName", p.name);
            data.put("playercode", target.phash);
            data.put("rank", target.rank);
            data.put("buildings", target.buildingsBuilt);
            data.put("games", target.gamesPlayed);
            data.put("frozen", false);
            data.put("muted", false);
            data.put("playtime", target.playTime);

            Call.clientPacketReliable(player.con, "playerdata", data.toString());
        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {
        for (MiniMod minimod : minimods) {
            minimod.registerCommands(handler);
        }
    }
}