package mindustry.plugin.discord;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Channels {
    /**
     * Channel for live chat
     */
    public static ArrayList<TextChannel> CHAT = new ArrayList<>();

    public static TextChannel WARNINGS;
    public static TextChannel APPEAL;
    public static TextChannel BUG_REPORT;
    public static TextChannel GR_REPORT;

    public static TextChannel MAP_SUBMISSIONS;
    public static TextChannel MAP_RATING;

    public static TextChannel LOG;
    public static TextChannel ERROR_LOG;
    public static TextChannel COLONEL_LOG;

    public static ArrayList<TextChannel> BOT = new ArrayList<>();
    public static TextChannel MOD_BOT;
    public static TextChannel APPRENTICE_BOT;
    public static TextChannel ADMIN_BOT;
    public static TextChannel LIVE_LOG;

    /**
     * Retrieves a text channel. Panics if it does not exist.
     */
    private static TextChannel getChannel(DiscordApi api, String id) {
        return api.getTextChannelById(id).get();
    }

    public static void load(DiscordApi api, JSONObject obj) {
        if (obj == null) return;
        if (obj.has("chat")) {
            CHAT = new ArrayList<>();
            try {
                for (Object o : obj.getJSONArray("chat")) {
                    CHAT.add(getChannel(api, o.toString()));
                }
            } catch (Exception e) {
                CHAT.add(getChannel(api, obj.getString("chat")));
            }
        }
        WARNINGS = getChannel(api, obj.getString("warnings"));
        APPEAL = getChannel(api, obj.getString("appeal"));
        BUG_REPORT = getChannel(api, obj.getString("bug_report"));
        GR_REPORT = getChannel(api, obj.getString("gr_report"));
        MAP_SUBMISSIONS = getChannel(api, obj.getString("map_submissions"));
        MAP_RATING = getChannel(api, obj.getString("map_rating"));

        LOG = getChannel(api, obj.getString("log"));
        ERROR_LOG = getChannel(api, obj.getString("error_log"));
        COLONEL_LOG = getChannel(api, obj.getString("colonel_log"));

        try {
            BOT = new ArrayList<>();
            for (Object o : obj.getJSONArray("bot")) {
                BOT.add(getChannel(api, o.toString()));
            }
        } catch (Exception e) {
            BOT.add(getChannel(api, obj.getString("bot")));
        }
        MOD_BOT = getChannel(api, obj.getString("mod_bot"));
        APPRENTICE_BOT = getChannel(api, obj.getString("apprentice_bot"));
        ADMIN_BOT = getChannel(api, obj.getString("admin_bot"));
        LIVE_LOG = getChannel(api, obj.getString("live_log"));
    }
}
