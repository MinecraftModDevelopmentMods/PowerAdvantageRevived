package zone.moddev.mc.poweradvantage;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import zone.moddev.mc.poweradvantage.init.ModContent;

/**
 * NeoForge entry point for Power Advantage and its MMDLib-backed content registration.
 */
@Mod(PowerAdvantage.MODID)
public final class PowerAdvantage {
    /**
     * Stable registry and resource namespace for this mod.
     */
    public static final String MODID = "poweradvantage";
    /**
     * Mod logger for runtime diagnostics.
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Installs the mod's content, factories and event registrations.
     *
     * @param modBus owning mod event bus
     */
    public PowerAdvantage(IEventBus modBus) {
        new ModContent(modBus);
    }
}
