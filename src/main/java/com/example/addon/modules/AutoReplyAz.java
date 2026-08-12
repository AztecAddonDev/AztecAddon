package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoReplyAz extends Module {
    private final SettingGroup sgTrigger = this.settings.createGroup("Trigger");
    private final SettingGroup sgReply = this.settings.createGroup("Reply");
    private final SettingGroup sgCooldown = this.settings.createGroup("Cooldown");


    private final Setting<String> replyMessage = sgReply.add(new StringSetting.Builder()
        .name("reply-message")
        .description("Message to send as automatic reply.")
        .defaultValue("I'm currently busy, I'll reply later.")
        .build()
    );

    private final Setting<Boolean> randomReplies = sgReply.add(new BoolSetting.Builder()
        .name("random-replies")
        .description("Enable random replies from a list instead of a single message.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> randomReplyList = sgReply.add(new StringListSetting.Builder()
        .name("random-reply-list")
        .description("List of random replies to choose from.")
        .defaultValue(List.of(
            "I'm currently busy, I'll reply later.",
            "AFK right now, will respond soon.",
            "I'm not available at the moment.",
            "Will get back to you later."
        ))
        .visible(randomReplies::get)
        .build()
    );


    private final Setting<TriggerMode> triggerMode = sgTrigger.add(new EnumSetting.Builder<TriggerMode>()
        .name("trigger")
        .description("What type of messages trigger auto-reply.")
        .defaultValue(TriggerMode.PrivateMessage)
        .build()
    );

    private final Setting<String> privateMessagePattern = sgTrigger.add(new StringSetting.Builder()
        .name("private-pattern")
        .description("Regex pattern to detect private messages. Use %player% as placeholder for sender name.")
        .defaultValue("(?i).*whispers?.*|(?i).*->.*you.*|(?i).*tells you.*")
        .visible(() -> triggerMode.get() == TriggerMode.PrivateMessage || triggerMode.get() == TriggerMode.Both)
        .build()
    );

    private final Setting<String> mentionPattern = sgTrigger.add(new StringSetting.Builder()
        .name("mention-pattern")
        .description("Regex pattern to detect mentions. Use %player% as placeholder for your name.")
        .defaultValue("(?i).*%player%.*")
        .visible(() -> triggerMode.get() == TriggerMode.Mention || triggerMode.get() == TriggerMode.Both)
        .build()
    );


    private final Setting<ReplyCommand> replyCommand = sgReply.add(new EnumSetting.Builder<ReplyCommand>()
        .name("reply-command")
        .description("Command to use for replying.")
        .defaultValue(ReplyCommand.R)
        .build()
    );


    private final Setting<Integer> cooldownSeconds = sgCooldown.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Minimum time between automatic replies in seconds.")
        .defaultValue(30)
        .min(0)
        .max(300)
        .sliderMax(60)
        .build()
    );



    private long lastReplyTime = 0;
    private String lastMessage = null;
    private final Random random = new Random();

    public AutoReplyAz() {
        super(AddonTemplate.CATEGORY, "auto-reply-az", "Automatically replies to private messages and mentions.");
    }

    @Override
    public void onActivate() {
        lastReplyTime = 0;
        lastMessage = null;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;

        String messageString = event.getMessage().getString();
        if (messageString == null || messageString.isEmpty()) return;


        if (System.currentTimeMillis() - lastReplyTime < cooldownSeconds.get() * 1000L) {
            return;
        }


        if (messageString.equals(lastMessage)) {
            return;
        }


        if (!shouldTriggerReply(messageString)) {
            return;
        }


        sendReply();

        lastReplyTime = System.currentTimeMillis();
        lastMessage = messageString;
    }

    private boolean shouldTriggerReply(String message) {
        String playerName = mc.player != null ? mc.player.getName().getString() : "";

        switch (triggerMode.get()) {
            case PrivateMessage:
                return matchesPattern(message, privateMessagePattern.get(), playerName);
            case Mention:
                return matchesPattern(message, mentionPattern.get().replace("%player%", playerName), playerName);
            case Both:
                return matchesPattern(message, privateMessagePattern.get(), playerName) ||
                       matchesPattern(message, mentionPattern.get().replace("%player%", playerName), playerName);
            default:
                return false;
        }
    }

    private boolean matchesPattern(String message, String pattern, String playerName) {
        if (pattern == null || pattern.isEmpty()) return false;

        try {
            String actualPattern = pattern.replace("%player%", playerName);
            return message.matches(actualPattern);
        } catch (Exception e) {
            return false;
        }
    }

    private void sendReply() {
        String message;

        if (randomReplies.get() && !randomReplyList.get().isEmpty()) {
            List<String> replies = new ArrayList<>(randomReplyList.get());
            message = replies.get(random.nextInt(replies.size()));
        } else {
            message = replyMessage.get();
        }

        if (message == null || message.isEmpty()) return;

        String commandPrefix = replyCommand.get().getCommand();
        String fullCommand = commandPrefix + " " + message;

        info("Auto-replying: " + message);
        mc.player.networkHandler.sendChatCommand(fullCommand);
    }

    public enum TriggerMode {
        PrivateMessage,
        Mention,
        Both
    }

    public enum ReplyCommand {
        R("/r"),
        Reply("/reply");

        private final String command;

        ReplyCommand(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }
    }
}
