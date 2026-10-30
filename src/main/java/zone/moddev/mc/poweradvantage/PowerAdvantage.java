package zone.moddev.mc.poweradvantage;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import zone.moddev.mc.poweradvantage.init.ModContent;

@Mod(PowerAdvantage.MODID)
public final class PowerAdvantage {
    public static final String MODID = "poweradvantage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PowerAdvantage(IEventBus modBus) {
        new ModContent(modBus);
    }
}
