package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

public class AztecBookBan extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAntiBookBan = settings.createGroup("Anti-BookBan");

    private final Setting<Integer> pages = sgGeneral.add(new IntSetting.Builder()
        .name("pages")
        .description("Number of pages to fill per book.")
        .defaultValue(100)
        .min(1)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> charsPerPage = sgGeneral.add(new IntSetting.Builder()
        .name("chars-per-page")
        .description("Characters per page. Higher = more crash potential.")
        .defaultValue(256)
        .min(1)
        .max(256)
        .sliderMax(256)
        .build()
    );

    private final Setting<String> bookTitle = sgGeneral.add(new StringSetting.Builder()
        .name("title")
        .description("Title of the signed book.")
        .defaultValue("Aztec Addon")
        .build()
    );

    private final Setting<Boolean> autoDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-drop")
        .description("Automatically drop the book after signing it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between books in ticks.")
        .defaultValue(5)
        .min(1)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> useUnicode = sgGeneral.add(new BoolSetting.Builder()
        .name("use-unicode")
        .description("Use Unicode characters for maximum crash potential.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiBookBan = sgAntiBookBan.add(new BoolSetting.Builder()
        .name("anti-book-ban")
        .description("Prevents you from opening dangerous books.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxSafePages = sgAntiBookBan.add(new IntSetting.Builder()
        .name("max-safe-pages")
        .description("Maximum pages before a book is considered dangerous.")
        .defaultValue(50)
        .min(1)
        .max(100)
        .sliderMax(100)
        .visible(antiBookBan::get)
        .build()
    );

    private final Setting<Integer> maxSafeChars = sgAntiBookBan.add(new IntSetting.Builder()
        .name("max-safe-chars")
        .description("Maximum characters per page before a book is considered dangerous.")
        .defaultValue(200)
        .min(1)
        .max(256)
        .sliderMax(256)
        .visible(antiBookBan::get)
        .build()
    );

    private int delayLeft;
    private int booksProcessed;
    private int activeSlot = -1;

    public AztecBookBan() {
        super(AddonTemplate.CATEGORY, "aztec-book-ban", "Creates bookbans and protects you from them.");
    }

    @Override
    public void onActivate() {
        delayLeft = 0;
        booksProcessed = 0;
        activeSlot = -1;
    }

    @Override
    public void onDeactivate() {
        if (booksProcessed > 0) {
            // LOG interno con prefix [AztecAddon]
            ChatUtils.infoPrefix("AztecAddon", "Processed " + booksProcessed + " books.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (activeSlot != -1 && autoDrop.get()) {
            InvUtils.drop().slot(activeSlot);
            activeSlot = -1;
            return;
        }

        if (delayLeft > 0) {
            delayLeft--;
            return;
        }

        var book = InvUtils.find(Items.WRITABLE_BOOK);
        if (!book.found()) {
            // LOG interno con prefix [AztecAddon]
            ChatUtils.warningPrefix("AztecAddon", "No more writable books found.");
            toggle();
            return;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.move().from(book.slot()).toHotbar(selectedSlot);
        activeSlot = selectedSlot;

        List<String> pageContents = generatePages();

        mc.getNetworkHandler().sendPacket(new BookUpdateC2SPacket(
            selectedSlot,
            pageContents,
            Optional.of(bookTitle.get())
        ));

        booksProcessed++;
        delayLeft = delay.get();
    }

    public boolean isAntiBookBanEnabled() {
        return isActive() && antiBookBan.get();
    }

    public boolean isDangerousBook(ItemStack stack) {
        var writableContent = stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        if (writableContent != null) {
            var pagesList = writableContent.pages();
            if (pagesList.size() > maxSafePages.get()) return true;
            for (var page : pagesList) {
                if (page.toString().length() > maxSafeChars.get()) return true;
            }
            return false;
        }

        var writtenContent = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (writtenContent != null) {
            var pagesList = writtenContent.pages();
            if (pagesList.size() > maxSafePages.get()) return true;
            for (var page : pagesList) {
                if (page.toString().length() > maxSafeChars.get()) return true;
            }
            return false;
        }

        return false;
    }

    private List<String> generatePages() {
        List<String> pageContents = new ArrayList<>();
        String pageContent = generatePageContent();

        for (int i = 0; i < pages.get(); i++) {
            pageContents.add(pageContent);
        }

        return pageContents;
    }

    private String generatePageContent() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < charsPerPage.get(); i++) {
            if (useUnicode.get()) {
                int charCode = 0x0020 + (int) (Math.random() * 0xFFDF);
                sb.append((char) charCode);
            } else {
                sb.append('a');
            }
        }

        return sb.toString();
    }
}
