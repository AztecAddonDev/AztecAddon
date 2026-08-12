package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AztecAntiBookBan extends Module {
    private final SettingGroup sgProtection = settings.getDefaultGroup();
    private final SettingGroup sgDetection = settings.createGroup("Detection");


    private final Setting<Boolean> blockOpening = sgProtection.add(new BoolSetting.Builder()
        .name("block-opening")
        .description("Prevents you from opening dangerous books.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoRemove = sgProtection.add(new BoolSetting.Builder()
        .name("auto-remove")
        .description("Automatically removes dangerous books from your inventory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> warnInChat = sgProtection.add(new BoolSetting.Builder()
        .name("warn-in-chat")
        .description("Shows a warning in chat when a dangerous book is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanInterval = sgProtection.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to scan inventory in ticks.")
        .defaultValue(20)
        .min(5)
        .max(200)
        .sliderMax(100)
        .visible(autoRemove::get)
        .build()
    );


    private final Setting<Integer> maxSafePages = sgDetection.add(new IntSetting.Builder()
        .name("max-safe-pages")
        .description("Maximum pages before a book is considered dangerous.")
        .defaultValue(50)
        .min(1)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> maxSafeChars = sgDetection.add(new IntSetting.Builder()
        .name("max-safe-chars")
        .description("Maximum characters per page before a book is considered dangerous.")
        .defaultValue(200)
        .min(1)
        .max(256)
        .sliderMax(256)
        .build()
    );

    private final Setting<Boolean> checkUnicode = sgDetection.add(new BoolSetting.Builder()
        .name("check-unicode")
        .description("Check for invalid Unicode characters that can crash clients.")
        .defaultValue(true)
        .build()
    );


    private int scanDelay;
    private int booksBlocked;
    private int booksRemoved;

    public AztecAntiBookBan() {
        super(AddonTemplate.CATEGORY, "aztec-anti-book-ban", "Protects you from bookban attacks.");
    }

    @Override
    public void onActivate() {
        scanDelay = 0;
        booksBlocked = 0;
        booksRemoved = 0;
    }

    @Override
    public void onDeactivate() {
        if (booksBlocked > 0 || booksRemoved > 0) {
            ChatUtils.sendPlayerMsg("§a[AntiBookBan] Blocked §f" + booksBlocked + "§a openings, removed §f" + booksRemoved + "§a books.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!autoRemove.get()) return;

        scanDelay++;
        if (scanDelay < scanInterval.get()) return;
        scanDelay = 0;

        scanInventory();
    }

    private void scanInventory() {

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isBook(stack) && isDangerousBook(stack)) {
                handleDangerousBook(i, stack, "inventory slot " + i);
            }
        }


        ItemStack offhand = mc.player.getOffHandStack();
        if (isBook(offhand) && isDangerousBook(offhand)) {
            handleDangerousBookOffhand(offhand);
        }
    }

    private void handleDangerousBook(int slot, ItemStack stack, String location) {
        if (warnInChat.get()) {
            ChatUtils.sendPlayerMsg("§c[AntiBookBan] §lDangerous book found! §r§7Location: " + location);
        }

        if (autoRemove.get()) {
            mc.player.getInventory().removeStack(slot);
            booksRemoved++;
        }
    }

    private void handleDangerousBookOffhand(ItemStack stack) {
        if (warnInChat.get()) {
            ChatUtils.sendPlayerMsg("§c[AntiBookBan] §lDangerous book found! §r§7Location: offhand");
        }

        if (autoRemove.get()) {
            mc.player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            booksRemoved++;
        }
    }



    public boolean isBlockOpeningEnabled() {
        return isActive() && blockOpening.get();
    }

    public boolean isDangerousBook(ItemStack stack) {
        if (!isBook(stack)) return false;


        var writableContent = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        if (writableContent != null) {
            return checkPages(writableContent.pages());
        }


        var writtenContent = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (writtenContent != null) {
            return checkPages(writtenContent.pages());
        }

        return false;
    }

    public void onBookBlocked() {
        booksBlocked++;
    }



    private boolean isBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() == Items.WRITTEN_BOOK || stack.getItem() == Items.WRITABLE_BOOK;
    }

    private boolean checkPages(Iterable<?> pagesList) {
        int pageCount = 0;

        for (var page : pagesList) {
            pageCount++;
            String pageStr = page.toString();


            if (pageStr.length() > maxSafeChars.get()) return true;


            if (checkUnicode.get() && hasInvalidUnicode(pageStr)) return true;
        }


        if (pageCount > maxSafePages.get()) return true;

        return false;
    }

    private boolean hasInvalidUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);


            if (c < 0x20 && c != '\n' && c != '\t') return true;


            if (c >= 0xE000 && c <= 0xF8FF) return true;


            if (Character.isSurrogate(c)) return true;
        }
        return false;
    }
}
