package dev.aztec.addon.hud;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

public class AztecWatermarkAz extends HudElement {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Width of the watermark image.")
        .defaultValue(64.0)
        .min(10.0)
        .max(512.0)
        .sliderMax(256.0)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Height of the watermark image.")
        .defaultValue(64.0)
        .min(10.0)
        .max(512.0)
        .sliderMax(256.0)
        .build()
    );

    // ✅ FIX: Usar el mod ID correcto ("aztecaddon") en lugar de "template"
    private static final Identifier ICON_TEXTURE = Identifier.of("aztecaddon", "icon.png");

    public static final HudElementInfo<AztecWatermarkAz> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "aztec-watermark-az",
        "Aztec watermark with icon.",
        AztecWatermarkAz::new
    );

    public AztecWatermarkAz() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double w = width.get();
        double h = height.get();

        setSize(w, h);

        // ✅ FIX 1.21.11: Usar RenderLayer.getText() para renderizar texturas correctamente
        try {
            renderer.texture(
                ICON_TEXTURE,
                x, y,
                w, h,
                Color.WHITE
            );
        } catch (Exception e) {
            // Fallback: cuadrado verde si la textura no se puede cargar
            renderer.quad(x, y, w, h, new Color(0, 168, 107, 255)); // Verde jade Aztec
        }
    }
}
